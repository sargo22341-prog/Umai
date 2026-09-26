package org.opensources.umai.planning.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.domain.FoodLookup
import org.opensources.umai.planning.domain.FoodProduct
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.Nutrient
import org.opensources.umai.planning.domain.NutritionFacts

class OpenFoodFactsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: OpenFoodFactsRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repository = OpenFoodFactsRepository(
            client = OkHttpClient(),
            userAgent = "umai/test (https://example.org)",
            language = { "fr" },
            baseUrl = server.url("/"),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun `a sandwich is found per 100 g, with its serving and photo`() = runTest {
        enqueue(SANDWICH)

        val lookup = repository.product("3560070565313")

        assertEquals(
            FoodLookup.Found(
                FoodProduct(
                    barcode = "3560070565313",
                    name = "CLASSIC' Jambon Beurre",
                    unit = FoodUnit.GRAM,
                    per100 = NutritionFacts(
                        mapOf(
                            Nutrient.ENERGY to 238.0,
                            Nutrient.FAT to 10.0,
                            Nutrient.SATURATED_FAT to 4.7,
                            Nutrient.CARBOHYDRATES to 24.0,
                            Nutrient.SUGARS to 2.8,
                            Nutrient.FIBER to 3.1,
                            Nutrient.PROTEIN to 11.0,
                            Nutrient.SALT to 1.5,
                        ),
                    ),
                    portion = 125.0,
                    imageUrl = "https://images.openfoodfacts.org/images/products/356/007/056/5313/front_fr.120.400.jpg",
                ),
            ),
            lookup,
        )
        val request = server.takeRequest()
        assertEquals("/api/v2/product/3560070565313", request.url.encodedPath)
        assertEquals("umai/test (https://example.org)", request.headers["User-Agent"])
        assertTrue(request.url.queryParameter("fields").orEmpty().split(",").containsAll(listOf("product_name_fr", "nutriments")))
    }

    @Test
    fun `a drink is counted in ml, and a name in the language of the app wins`() = runTest {
        enqueue(
            """
            {"code":"5449000000996","status":1,"product":{
              "product_name":"Coca-Cola","product_name_fr":"Coca-Cola Original",
              "product_quantity":"330","product_quantity_unit":"ml",
              "nutriments":{"energy-kcal_100g":42,"carbohydrates_100g":10.6}}}
            """.trimIndent(),
        )

        val product = (repository.product("5449000000996") as FoodLookup.Found).product

        assertEquals("Coca-Cola Original", product.name)
        assertEquals(FoodUnit.MILLILITRE, product.unit)
        assertEquals(330.0, product.portion)
        assertEquals(42.0, product.per100[Nutrient.ENERGY])
    }

    @Test
    fun `the front photo is taken in the language of the product without one in the app's`() = runTest {
        enqueue(
            """
            {"status":1,"product":{"product_name":"Stroopwafels","lang":"nl",
              "selected_images":{"front":{"display":{"nl":"https://images.example/front_nl.jpg"}}},
              "image_front_url":"https://images.example/front_en.jpg","nutriments":{"energy-kcal_100g":450}}}
            """.trimIndent(),
        )

        assertEquals("https://images.example/front_nl.jpg", (repository.product("96385074") as FoodLookup.Found).product.imageUrl)
    }

    @Test
    fun `without a front photo of its own the product has the one Open Food Facts gives`() = runTest {
        enqueue("""{"status":1,"product":{"product_name":"Pain","image_front_url":"https://images.example/front.jpg"}}""")

        assertEquals("https://images.example/front.jpg", (repository.product("96385074") as FoodLookup.Found).product.imageUrl)
    }

    @Test
    fun `an energy given in kJ only is turned into kcal`() = runTest {
        enqueue("""{"status":1,"product":{"product_name":"Pain","nutriments":{"energy-kj_100g":1046}}}""")

        val product = (repository.product("96385074") as FoodLookup.Found).product

        assertEquals(250.0, product.per100[Nutrient.ENERGY]!!, 0.1)
    }

    @Test
    fun `an unknown barcode is not found`() = runTest {
        enqueue("""{"code":"3017620429999","status":0,"status_verbose":"product not found"}""", code = 404)

        assertEquals(FoodLookup.NotFound, repository.product("3017620429999"))
    }

    @Test
    fun `a product without a name nor nutrition is not found`() = runTest {
        enqueue("""{"status":1,"product":{"nutriments":{}}}""")

        assertEquals(FoodLookup.NotFound, repository.product("3017620429999"))
    }

    @Test
    fun `a server failure is reported`() = runTest {
        enqueue("oops", code = 503)

        assertEquals(FoodLookup.Failed(NetworkError.Server(503)), repository.product("3017620429999"))
    }

    private companion object {
        val SANDWICH = """
            {"code":"3560070565313","status":1,"status_verbose":"product found","product":{
              "product_name":"CLASSIC' Jambon Beurre","product_name_fr":"CLASSIC' Jambon Beurre",
              "generic_name":"Sandwich au pain de mie complet garni de jambon cuit standard et de beurre.",
              "product_quantity":125,"product_quantity_unit":"g","quantity":"125 g",
              "serving_quantity":125,"serving_quantity_unit":"g","serving_size":"2 sandwichs 125 g",
              "lang":"fr",
              "selected_images":{"front":{"display":{
                "en":"https://images.openfoodfacts.org/images/products/356/007/056/5313/front_en.70.400.jpg",
                "fr":"https://images.openfoodfacts.org/images/products/356/007/056/5313/front_fr.120.400.jpg"}}},
              "image_front_url":"https://images.openfoodfacts.org/images/products/356/007/056/5313/front_en.70.400.jpg",
              "image_front_small_url":"https://images.openfoodfacts.org/images/products/356/007/056/5313/front_en.70.200.jpg",
              "nutriments":{"energy-kcal_100g":238,"energy-kj_100g":997,"fat_100g":10,"saturated-fat_100g":4.7,
                "carbohydrates_100g":24,"sugars_100g":2.8,"fiber_100g":3.1,"proteins_100g":11,"salt_100g":1.5,
                "sodium_100g":0.6,"energy-kcal_serving":298}}}
        """.trimIndent()
    }
}
