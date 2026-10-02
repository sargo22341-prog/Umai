package org.opensources.umai.planning.ui

import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.domain.FoodEstimate
import org.opensources.umai.planning.domain.FoodProduct
import org.opensources.umai.planning.domain.FoodSuggestion
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.NutritionFacts
import org.opensources.umai.planning.domain.NutritionNumbers
import org.opensources.umai.planning.domain.Nutrient
import java.time.LocalDate
import kotlin.math.roundToInt

/** The stages of adding a food to the plan. */
enum class FoodEntryStep { PRODUCT, NUTRITION, PORTION }

/** How the product is described: found by what was typed or by its barcode, or typed in full. */
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

/** Why what was typed did not give the food. */
enum class DescriptionIssue {
    /** The table of basic foods does not have it, and there is no model to ask: it is described by hand. */
    NOT_FOUND,

    /** The model listed no food in it. */
    MODEL_FOUND_NOTHING,

    /** The model could not run. */
    MODEL_FAILED,

    /** The table shipped with the app could not be read. */
    TABLE_UNREADABLE,
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
    /** What the user typed they ate: "2 pommes, 1 café sans sucre". */
    val description: String = "",
    /** The foods of the table it may be, while it is typed. */
    val suggestions: List<FoodSuggestion> = emptyList(),
    /** While the description is looked up, and the model asked when the table does not have it. */
    val estimating: Boolean = false,
    val descriptionIssue: DescriptionIssue? = null,
    /** What the description gave, whose values fill the form. */
    val estimate: FoodEstimate? = null,
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
    /** Whether the on-device model can be asked: to estimate a meal the table does not have, to read a label. */
    val canAskModel: Boolean = false,
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

    val canGoOn: Boolean get() = step != FoodEntryStep.PRODUCT || (name.isNotBlank() && !searching && !estimating)

    /** In automatic mode, the form waits for a food found before asking for the rest. */
    val showsProductForm: Boolean get() = mode == FoodEntryMode.MANUAL || found != null || estimate != null

    /** In automatic mode, before a food is found: what was eaten is typed, or its barcode scanned. */
    val showsSearch: Boolean get() = mode == FoodEntryMode.AUTO && found == null && estimate == null

    val canEstimate: Boolean get() = description.isNotBlank() && !estimating

    /** Whether the values come from the model's guess, to be checked more than the table's. */
    val isModelEstimate: Boolean get() = estimate?.byModel == true

    val busy: Boolean get() = saving || processingPhoto || readingLabel || searching || estimating

    val canSave: Boolean get() = name.isNotBlank() && !busy
}

/** [photoKept] is false when the photo of the product could not be kept. */
data class FoodAdded(val photoKept: Boolean)
