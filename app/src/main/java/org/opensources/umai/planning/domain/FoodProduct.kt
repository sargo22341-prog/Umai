package org.opensources.umai.planning.domain

import org.opensources.umai.core.network.NetworkError

/**
 * A packaged product found by its barcode: its values for 100 of [unit], and
 * [portion], the quantity of one serving, or of the package, when known.
 */
data class FoodProduct(
    val barcode: String,
    val name: String,
    val unit: FoodUnit,
    val per100: NutritionFacts,
    val portion: Double?,
    val imageUrl: String?,
)

sealed interface FoodLookup {
    data class Found(val product: FoodProduct) : FoodLookup

    /** No product has this barcode in the database. */
    data object NotFound : FoodLookup

    data class Failed(val error: NetworkError) : FoodLookup
}

/** The barcodes printed on food: EAN-13, EAN-8, UPC-A and GTIN-14. */
object Barcodes {

    private val lengths = setOf(8, 12, 13, 14)

    /**
     * [text] as a barcode, spaces left out, `null` when it is not one: the
     * wrong length, or a check digit that does not match, as when a digit is
     * mistyped.
     */
    fun normalize(text: String): String? {
        val digits = text.filterNot { it.isWhitespace() || it == '-' }
        if (digits.length !in lengths || !digits.all { it in '0'..'9' }) return null
        return digits.takeIf { hasValidCheckDigit(it) }
    }

    /** GS1 check digit: from the right, the digits are weighted 3, 1, 3, 1… */
    private fun hasValidCheckDigit(digits: String): Boolean {
        val body = digits.dropLast(1).reversed()
        val sum = body.withIndex().sumOf { (index, char) -> (char - '0') * if (index % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
