package org.opensources.umai.planning.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.planning.domain.FoodLookup
import org.opensources.umai.planning.domain.FoodProduct
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.Nutrient
import org.opensources.umai.planning.domain.NutritionFacts

/**
 * Looks a packaged product up by its barcode in Open Food Facts, the open
 * database of food products (data under the Open Database License), through
 * its read API: `GET /api/v2/product/{barcode}`. No account is needed; the
 * requests name the app in their User-Agent, as the project asks.
 *
 * The values ending in `_100g` are always for 100 g or 100 ml, whatever the
 * label gives, which is what the plan counts from.
 */
class OpenFoodFactsRepository(
    /** The client for other websites, which never carries Mealie credentials. */
    private val client: OkHttpClient,
    private val userAgent: String,
    /** "fr" or "en": the name of the product in the language of the app, when it has one. */
    private val language: () -> String,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {

    suspend fun product(barcode: String): FoodLookup {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/v2/product")
            .addPathSegment(barcode)
            .addQueryParameter("fields", fields(language()))
            .build()
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val answer = apiCall {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response -> response.code to response.body.string() }
            }
        }
        val (code, body) = when (answer) {
            is ApiResult.Failure -> return FoodLookup.Failed(answer.error)
            is ApiResult.Success -> answer.value
        }
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        // An unknown barcode is answered 404, with a status of 0.
        if (code == HTTP_NOT_FOUND || (root?.get("status") as? JsonPrimitive)?.intOrNull == 0) return FoodLookup.NotFound
        if (code !in 200..299) {
            return FoodLookup.Failed(if (code >= 500) NetworkError.Server(code) else NetworkError.Http(code, null))
        }
        val product = root?.get("product") as? JsonObject ?: return FoodLookup.Failed(NetworkError.InvalidResponse)
        return parse(barcode, product, language())?.let { FoodLookup.Found(it) } ?: FoodLookup.NotFound
    }

    internal companion object {

        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org/"
        private const val HTTP_NOT_FOUND = 404

        /** One kilocalorie is 4.184 kilojoules. */
        private const val KJ_PER_KCAL = 4.184

        private val NUTRIMENTS = mapOf(
            Nutrient.FAT to "fat_100g",
            Nutrient.SATURATED_FAT to "saturated-fat_100g",
            Nutrient.CARBOHYDRATES to "carbohydrates_100g",
            Nutrient.SUGARS to "sugars_100g",
            Nutrient.FIBER to "fiber_100g",
            Nutrient.PROTEIN to "proteins_100g",
            Nutrient.SALT to "salt_100g",
        )

        /** Only what the plan uses is asked for: a whole product weighs tens of kilobytes. */
        private fun fields(language: String) = listOf(
            "product_name_$language", "product_name", "generic_name_$language", "generic_name",
            "nutriments", "serving_quantity", "serving_quantity_unit",
            "product_quantity", "product_quantity_unit", "quantity",
            "lang", "selected_images", "image_front_url", "image_front_small_url",
        ).joinToString(",")

        /** The product in [product], `null` when it has neither a name nor any nutrition. */
        fun parse(barcode: String, product: JsonObject, language: String): FoodProduct? {
            fun text(key: String) = (product[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            fun number(from: JsonObject?, key: String) =
                (from?.get(key) as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()

            val nutriments = product["nutriments"] as? JsonObject
            val values = NUTRIMENTS.mapNotNull { (nutrient, key) -> number(nutriments, key)?.let { nutrient to it } }
                .toMap().toMutableMap()
            val kcal = number(nutriments, "energy-kcal_100g")
                ?: (number(nutriments, "energy-kj_100g") ?: number(nutriments, "energy_100g"))?.div(KJ_PER_KCAL)
            kcal?.let { values[Nutrient.ENERGY] = it }

            val name = text("product_name_$language") ?: text("product_name")
                ?: text("generic_name_$language") ?: text("generic_name")
            if (name == null && values.isEmpty()) return null

            val serving = number(product, "serving_quantity")?.takeIf { it > 0 }
            val portion = serving ?: number(product, "product_quantity")?.takeIf { it > 0 }
            val unitText = if (serving != null) text("serving_quantity_unit") else text("product_quantity_unit")
            val millilitres = unitText?.lowercase() == "ml" ||
                (unitText == null && text("quantity")?.let(litre::containsMatchIn) == true)
            return FoodProduct(
                barcode = barcode,
                name = name.orEmpty(),
                unit = if (millilitres) FoodUnit.MILLILITRE else FoodUnit.GRAM,
                per100 = NutritionFacts(values),
                portion = portion,
                imageUrl = frontPhoto(product, listOfNotNull(language, text("lang")))
                    ?: text("image_front_url") ?: text("image_front_small_url"),
            )
        }

        /**
         * The front photo in the first of [languages] it was taken for. A
         * product has one per language, and `image_front_url` falls back on
         * one that may show something else: the English photo of a French
         * sandwich was its list of ingredients.
         */
        private fun frontPhoto(product: JsonObject, languages: List<String>): String? {
            val display = ((product["selected_images"] as? JsonObject)?.get("front") as? JsonObject)
                ?.get("display") as? JsonObject ?: return null
            return languages.firstNotNullOfOrNull { (display[it] as? JsonPrimitive)?.contentOrNull }
        }

        private val litre = Regex("""\d\s*(ml|cl|l)\b""", RegexOption.IGNORE_CASE)
    }
}
