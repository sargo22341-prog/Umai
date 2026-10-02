package org.opensources.umai.planning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.PhotoDownloader
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.planning.data.BarcodePictures
import org.opensources.umai.planning.data.LabelPictures
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.OpenFoodFactsRepository
import org.opensources.umai.planning.data.PlanPhotos
import org.opensources.umai.planning.domain.Barcodes
import org.opensources.umai.planning.domain.DescriptionOutcome
import org.opensources.umai.planning.domain.FoodDescriptions
import org.opensources.umai.planning.domain.FoodEstimate
import org.opensources.umai.planning.domain.FoodLookup
import org.opensources.umai.planning.domain.FoodNote
import org.opensources.umai.planning.domain.FoodNoteLabels
import org.opensources.umai.planning.domain.FoodProduct
import org.opensources.umai.planning.domain.FoodSuggestion
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.LabelOutcome
import org.opensources.umai.planning.domain.ModelFoodEstimator
import org.opensources.umai.planning.domain.NutritionLabelReader
import org.opensources.umai.planning.domain.Nutrient
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Adds to the plan something eaten that is not a recipe: a snack, a drink.
 *
 * By default the food is typed as it was eaten, "2 pommes, 1 café", and
 * looked up in the table of basic foods, or estimated by the on-device model
 * when the table does not have it ([FoodDescriptions]). A product is found
 * instead by its barcode, scanned or typed, in Open Food Facts, which gives
 * its name, nutrition, portion and photo; a product it does not know is typed
 * instead. Typed, the nutrition label can be photographed and read by the
 * on-device model, and its photo forgotten at once. The food becomes a note of the Mealie plan, which carries the calories
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
    private val descriptions: FoodDescriptions,
    /** How the app writes decimals: values read on a label are filled in as the user would type them. */
    private val decimalSeparator: Char = '.',
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEntryUiState(date = date))
    val state: StateFlow<FoodEntryUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var estimateJob: Job? = null

    init {
        viewModelScope.launch {
            val canAsk = descriptions.canAskModel()
            val canRead = labelReader.isReady()
            _state.update { it.copy(canAskModel = canAsk, canReadLabel = canRead) }
        }
    }

    /** Offers the foods of the table the text may be, once the user pauses typing. */
    fun setDescription(text: String) {
        _state.update { it.copy(description = text, descriptionIssue = null) }
        suggestJob?.cancel()
        if (text.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGESTION_DELAY_MS)
            val found = withContext(Dispatchers.Default) { descriptions.suggestions(text) }
            _state.update { current ->
                if (found == null) {
                    current.copy(suggestions = emptyList(), descriptionIssue = DescriptionIssue.TABLE_UNREADABLE)
                } else {
                    current.copy(suggestions = found)
                }
            }
        }
    }

    fun chooseSuggestion(suggestion: FoodSuggestion) {
        suggestJob?.cancel()
        fillEstimate(FoodEstimate(listOf(suggestion.item), byModel = false), suggestion.title)
    }

    /** Looks up every food of the description, and asks the model when the table does not have them all. */
    fun estimateDescription() {
        val current = _state.value
        if (!current.canEstimate) return
        val text = current.description.trim()
        suggestJob?.cancel()
        _state.update { it.copy(estimating = true, descriptionIssue = null, suggestions = emptyList()) }
        estimateJob = viewModelScope.launch {
            val title = text.replaceFirstChar { it.titlecase() }
            when (val outcome = withContext(Dispatchers.Default) { descriptions.describe(text) }) {
                is DescriptionOutcome.Estimated -> fillEstimate(outcome.estimate, title)
                DescriptionOutcome.NotFound -> _state.update {
                    it.copy(
                        estimating = false,
                        descriptionIssue = DescriptionIssue.NOT_FOUND,
                        mode = FoodEntryMode.MANUAL,
                        name = it.name.ifBlank { title },
                    )
                }
                DescriptionOutcome.ModelFoundNothing -> failDescription(DescriptionIssue.MODEL_FOUND_NOTHING)
                is DescriptionOutcome.ModelFailed -> failDescription(DescriptionIssue.MODEL_FAILED)
                DescriptionOutcome.TableUnreadable -> failDescription(DescriptionIssue.TABLE_UNREADABLE)
            }
        }
    }

    fun cancelEstimate() {
        estimateJob?.cancel()
        _state.update { it.copy(estimating = false) }
    }

    /** Forgets the food the description gave, to type another. */
    fun clearEstimate() = _state.update {
        it.copy(estimate = null, name = "", values = emptyMap(), quantity = "", unit = FoodUnit.GRAM)
    }

    private fun failDescription(issue: DescriptionIssue) =
        _state.update { it.copy(estimating = false, descriptionIssue = issue) }

    /** Fills the form with what the description gave, over what was there. */
    private fun fillEstimate(estimate: FoodEstimate, title: String) = _state.update { current ->
        current.copy(
            estimating = false,
            estimate = estimate,
            suggestions = emptyList(),
            descriptionIssue = null,
            name = title,
            unit = estimate.unit,
            values = estimate.per100.values.mapValues { (_, value) -> numberText(value) },
            quantity = estimate.amount?.let(::numberText).orEmpty(),
        )
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
            val text = FoodNote.text(
                quantity = current.quantityValue,
                unit = current.unit,
                portion = current.portion,
                labels = labels,
                estimated = current.isModelEstimate,
            )
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
        /** The pause in typing after which the foods it may be are looked up. */
        private const val SUGGESTION_DELAY_MS = 200L

        fun factory(container: AppContainer, date: LocalDate) = viewModelFactory {
            initializer {
                val language = container.localeController.appLanguage()
                val separator = if (language == "fr") ',' else '.'
                FoodEntryViewModel(
                    date = date,
                    mealPlanRepository = container.mealPlanRepository,
                    photos = container.planPhotos,
                    labelPictures = container.labelPictures,
                    labelReader = NutritionLabelReader(container.localLanguageModel),
                    barcodePictures = container.barcodePictures,
                    products = container.openFoodFacts,
                    photoDownloader = container.externalPhotoDownloader,
                    descriptions = FoodDescriptions(
                        table = container.foodTable::table,
                        model = ModelFoodEstimator(container.localLanguageModel),
                        language = language,
                        decimalSeparator = separator,
                    ),
                    decimalSeparator = separator,
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
