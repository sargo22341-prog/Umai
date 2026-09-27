package org.opensources.umai.planning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.PhotoDownloader
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.planning.data.BarcodePictures
import org.opensources.umai.planning.data.LabelPictures
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.OpenFoodFactsRepository
import org.opensources.umai.planning.data.PlanPhotos
import org.opensources.umai.planning.domain.Barcodes
import org.opensources.umai.planning.domain.FoodLookup
import org.opensources.umai.planning.domain.FoodNote
import org.opensources.umai.planning.domain.FoodNoteLabels
import org.opensources.umai.planning.domain.FoodProduct
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.LabelOutcome
import org.opensources.umai.planning.domain.NutritionFacts
import org.opensources.umai.planning.domain.NutritionLabelReader
import org.opensources.umai.planning.domain.NutritionNumbers
import org.opensources.umai.planning.domain.Nutrient
import java.time.LocalDate
import kotlin.math.roundToInt

/** The stages of adding a food to the plan. */
enum class FoodEntryStep { PRODUCT, NUTRITION, PORTION }

/** How the product is described: found by its barcode, or typed. */
enum class FoodEntryMode { AUTO, MANUAL }

/** Why the barcode did not give the product. */
enum class LookupIssue {
    /** No barcode was found on the picture. */
    UNREADABLE,

    /** The digits typed are not a barcode: wrong length, or a mistyped digit. */
    INVALID_CODE,

    /** Open Food Facts does not know the product: it is typed instead. */
    NOT_FOUND,

    /** Open Food Facts knows the product, not its nutrition: the rest is typed. */
    NO_NUTRITION,

    /** Open Food Facts could not be reached. */
    FAILED,
}

/** Why the label was not read. */
enum class LabelIssue {
    /** No model is installed, or the local AI is off. */
    NO_MODEL,

    /** The model installed has no vision part, or it does not load on this phone. */
    NO_VISION,

    /** The picture could not be opened. */
    PICTURE_UNREADABLE,

    /** The model saw no nutrition table. */
    NOTHING_FOUND,

    FAILED,
}

data class FoodEntryUiState(
    val date: LocalDate,
    val step: FoodEntryStep = FoodEntryStep.PRODUCT,
    val mode: FoodEntryMode = FoodEntryMode.AUTO,
    /** The barcode, as scanned or typed. */
    val barcode: String = "",
    /** While the barcode is read on its photo, then looked up. */
    val searching: Boolean = false,
    val lookupIssue: LookupIssue? = null,
    /** The product found by its barcode, whose details fill the form. */
    val found: FoodProduct? = null,
    val name: String = "",
    val mealType: MealType = MealType.SNACK,
    /** The photo of the product, framed and kept on the phone once the food is added. */
    val photoPath: String? = null,
    val processingPhoto: Boolean = false,
    val photoFailed: Boolean = false,
    /** Whether the on-device model can be asked to read the label. */
    val canReadLabel: Boolean = false,
    val readingLabel: Boolean = false,
    val labelRead: Boolean = false,
    val labelIssue: LabelIssue? = null,
    val unit: FoodUnit = FoodUnit.GRAM,
    /** The values for 100 [unit], as typed or read. */
    val values: Map<Nutrient, String> = emptyMap(),
    /** The quantity eaten, in [unit]. */
    val quantity: String = "",
    val saving: Boolean = false,
    val error: NetworkError? = null,
    /** Set once the food is in the plan; the screen then closes. */
    val added: FoodAdded? = null,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = FoodEntryStep.entries.size
    val isFirstStep: Boolean get() = step == FoodEntryStep.entries.first()
    val isLastStep: Boolean get() = step == FoodEntryStep.entries.last()

    val per100: NutritionFacts
        get() = NutritionFacts(
            values.mapNotNull { (nutrient, text) -> NutritionNumbers.parse(text)?.let { nutrient to it } }.toMap(),
        )

    val quantityValue: Double? get() = NutritionNumbers.parse(quantity)?.takeIf { it > 0 }

    /** The nutrition of the quantity eaten; empty until both are known. */
    val portion: NutritionFacts
        get() = quantityValue?.let { per100.scaled(it / NutritionFacts.LABEL_QUANTITY) } ?: NutritionFacts()

    val calories: Int? get() = portion[Nutrient.ENERGY]?.roundToInt()

    val canGoOn: Boolean get() = step != FoodEntryStep.PRODUCT || (name.isNotBlank() && !searching)

    /** In automatic mode, the form waits for a product found before asking for the rest. */
    val showsProductForm: Boolean get() = mode == FoodEntryMode.MANUAL || found != null

    val busy: Boolean get() = saving || processingPhoto || readingLabel || searching

    val canSave: Boolean get() = name.isNotBlank() && !busy
}

/** [photoKept] is false when the photo of the product could not be kept. */
data class FoodAdded(val photoKept: Boolean)

/**
 * Adds to the plan something eaten that is not a recipe: a snack, a drink.
 *
 * By default the product is found by its barcode, scanned or typed, in Open
 * Food Facts, which gives its name, nutrition, portion and photo; a product it
 * does not know is typed instead. Typed, the nutrition label can be
 * photographed and read by the on-device model, and its photo forgotten at
 * once. The food becomes a note of the Mealie plan, which carries the calories
 * of the quantity eaten.
 */
class FoodEntryViewModel(
    date: LocalDate,
    private val mealPlanRepository: MealPlanRepository,
    private val photos: PlanPhotos,
    private val labelPictures: LabelPictures,
    private val labelReader: NutritionLabelReader,
    private val barcodePictures: BarcodePictures,
    private val products: OpenFoodFactsRepository,
    private val photoDownloader: PhotoDownloader,
    /** How the app writes decimals: values read on a label are filled in as the user would type them. */
    private val decimalSeparator: Char = '.',
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEntryUiState(date = date))
    val state: StateFlow<FoodEntryUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val ready = labelReader.isReady()
            _state.update { it.copy(canReadLabel = ready) }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setMode(mode: FoodEntryMode) = _state.update { it.copy(mode = mode, lookupIssue = null) }

    fun setBarcode(text: String) = _state.update { it.copy(barcode = text, lookupIssue = null) }

    /** Looks up the product of the barcode read by the camera. */
    fun barcodeScanned(code: String) = search {
        Barcodes.normalize(code).orFail(LookupIssue.INVALID_CODE)
    }

    /** Reads the barcode on the picture at [sourceUri], then looks the product up. */
    fun readBarcodePicture(sourceUri: String) = search {
        barcodePictures.read(sourceUri).orFail(LookupIssue.UNREADABLE)
    }

    /** Looks up the product of the barcode typed. */
    fun searchBarcode() = search {
        Barcodes.normalize(_state.value.barcode).orFail(LookupIssue.INVALID_CODE)
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searching = false) }
    }

    /** [barcode] gives the code to look up, or `null` once it has reported why there is none. */
    private fun search(barcode: suspend () -> String?) {
        if (_state.value.searching) return
        _state.update { it.copy(searching = true, lookupIssue = null) }
        searchJob = viewModelScope.launch {
            val code = barcode() ?: return@launch
            _state.update { it.copy(barcode = code) }
            when (val lookup = products.product(code)) {
                is FoodLookup.Found -> fill(lookup.product)
                FoodLookup.NotFound -> _state.update {
                    it.copy(searching = false, lookupIssue = LookupIssue.NOT_FOUND, mode = FoodEntryMode.MANUAL)
                }
                is FoodLookup.Failed -> fail(LookupIssue.FAILED)
            }
        }
    }

    /** The barcode, or `null` once [issue] is reported for its absence. */
    private fun String?.orFail(issue: LookupIssue): String? {
        if (this == null) fail(issue)
        return this
    }

    private fun fail(issue: LookupIssue) {
        _state.update { it.copy(searching = false, lookupIssue = issue) }
    }

    /**
     * Fills the form with what Open Food Facts gives, over what was there:
     * the product scanned is the one being added. Without its calories, the
     * rest of the form is typed.
     */
    private suspend fun fill(product: FoodProduct) {
        val complete = product.per100[Nutrient.ENERGY] != null
        _state.update { current ->
            current.copy(
                searching = false,
                found = product,
                mode = if (complete) FoodEntryMode.AUTO else FoodEntryMode.MANUAL,
                lookupIssue = if (complete) null else LookupIssue.NO_NUTRITION,
                name = product.name.ifBlank { current.name },
                unit = product.unit,
                values = product.per100.values.mapValues { (_, value) -> numberText(value) },
                quantity = product.portion?.let(::numberText).orEmpty(),
            )
        }
        // The photo of the database stands in for one the user did not take.
        val url = product.imageUrl ?: return
        if (_state.value.photoPath != null) return
        _state.update { it.copy(processingPhoto = true) }
        val path = photoDownloader.download(url)?.let { photos.keep(it) }
        _state.update { current ->
            // A photo taken meanwhile wins.
            if (path == null || current.photoPath != null) {
                path?.let(photos::discard)
                current.copy(processingPhoto = false)
            } else {
                current.copy(processingPhoto = false, photoPath = path)
            }
        }
    }

    fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }

    fun setUnit(unit: FoodUnit) = _state.update { it.copy(unit = unit) }

    fun setValue(nutrient: Nutrient, text: String) =
        _state.update { it.copy(values = it.values + (nutrient to text)) }

    fun setQuantity(text: String) = _state.update { it.copy(quantity = text) }

    /** The photo is framed and stored at once: the picker's access to it does not last. */
    fun setPhoto(sourceUri: String, region: CropRegion) {
        if (_state.value.processingPhoto) return
        _state.update { it.copy(processingPhoto = true, photoFailed = false) }
        viewModelScope.launch {
            val path = photos.frame(sourceUri, region)
            _state.update { current ->
                if (path == null) {
                    current.copy(processingPhoto = false, photoFailed = true)
                } else {
                    current.photoPath?.let(photos::discard)
                    current.copy(processingPhoto = false, photoPath = path)
                }
            }
        }
    }

    fun removePhoto() {
        _state.value.photoPath?.let(photos::discard)
        _state.update { it.copy(photoPath = null, photoFailed = false) }
    }

    /** Reads the label on the picture at [sourceUri], and fills in what it gives. */
    fun readLabel(sourceUri: String) {
        if (_state.value.readingLabel) return
        _state.update { it.copy(readingLabel = true, labelIssue = null, labelRead = false) }
        readJob = viewModelScope.launch {
            val jpeg = labelPictures.read(sourceUri)
            if (jpeg == null) {
                _state.update { it.copy(readingLabel = false, labelIssue = LabelIssue.PICTURE_UNREADABLE) }
                return@launch
            }
            when (val outcome = labelReader.read(jpeg)) {
                is LabelOutcome.Read -> _state.update { current ->
                    val reading = outcome.reading
                    current.copy(
                        readingLabel = false,
                        labelRead = true,
                        unit = reading.unit,
                        values = reading.per100.values.mapValues { (_, value) -> numberText(value) },
                        // What was typed stays: the label only suggests the portion.
                        quantity = current.quantity.ifBlank { reading.portion?.let(::numberText).orEmpty() },
                    )
                }
                LabelOutcome.NothingFound -> _state.update {
                    it.copy(readingLabel = false, labelIssue = LabelIssue.NOTHING_FOUND)
                }
                is LabelOutcome.Failed -> _state.update {
                    it.copy(readingLabel = false, labelIssue = outcome.reason.toIssue())
                }
            }
        }
    }

    fun cancelReading() {
        readJob?.cancel()
        _state.update { it.copy(readingLabel = false) }
    }

    fun dismissLabelIssue() = _state.update { it.copy(labelIssue = null) }

    fun next() = _state.update { current ->
        if (!current.canGoOn || current.isLastStep) current
        else current.copy(step = FoodEntryStep.entries[current.step.ordinal + 1])
    }

    /** Answers false on the first step, which has nothing before it. */
    fun previous(): Boolean {
        val current = _state.value
        if (current.isFirstStep) return false
        _state.update { it.copy(step = FoodEntryStep.entries[it.step.ordinal - 1]) }
        return true
    }

    fun save(labels: FoodNoteLabels) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val text = FoodNote.text(current.quantityValue, current.unit, current.portion, labels)
            val result = mealPlanRepository.add(
                date = current.date,
                type = current.mealType,
                recipeId = null,
                title = current.name.trim(),
                text = text,
            )
            when (result) {
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.error) }
                is ApiResult.Success -> {
                    val photoKept = current.photoPath?.let { photos.attach(it, result.value) } ?: true
                    _state.update { it.copy(saving = false, photoPath = null, added = FoodAdded(photoKept)) }
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** [value] to a tenth, without a trailing zero. */
    private fun numberText(value: Double): String {
        val tenths = (value * 10).roundToInt()
        val whole = (tenths / 10).toString()
        return if (tenths % 10 == 0) whole else "$whole$decimalSeparator${tenths % 10}"
    }

    override fun onCleared() {
        // Left without adding the food: its photo has nothing to go with.
        _state.value.photoPath?.let(photos::discard)
    }

    companion object {
        fun factory(container: AppContainer, date: LocalDate) = viewModelFactory {
            initializer {
                FoodEntryViewModel(
                    date = date,
                    mealPlanRepository = container.mealPlanRepository,
                    photos = container.planPhotos,
                    labelPictures = container.labelPictures,
                    labelReader = NutritionLabelReader(container.localLanguageModel),
                    barcodePictures = container.barcodePictures,
                    products = container.openFoodFacts,
                    photoDownloader = container.externalPhotoDownloader,
                    decimalSeparator = if (container.localeController.appLanguage() == "fr") ',' else '.',
                )
            }
        }
    }
}

private fun LlmFailure.toIssue(): LabelIssue = when (this) {
    LlmFailure.NOT_READY -> LabelIssue.NO_MODEL
    LlmFailure.MEDIA_UNSUPPORTED -> LabelIssue.NO_VISION
    LlmFailure.LOAD_FAILED, LlmFailure.TOO_LONG, LlmFailure.GENERATION_FAILED -> LabelIssue.FAILED
}
