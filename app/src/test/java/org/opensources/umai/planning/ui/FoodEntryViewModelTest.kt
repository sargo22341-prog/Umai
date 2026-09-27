package org.opensources.umai.planning.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.OkHttpClient
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.planning.data.FakePlanPhotos
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.OpenFoodFactsRepository
import org.opensources.umai.planning.domain.FoodNoteLabels
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.NutritionLabelReader
import org.opensources.umai.planning.domain.Nutrient
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class FoodEntryViewModelTest {

    private lateinit var fake: FakeMealieServer
    private val day = LocalDate.of(2026, 9, 24)

    private val labels = FoodNoteLabels(
        locale = Locale.FRENCH,
        nutrients = mapOf(Nutrient.CARBOHYDRATES to "Glucides", Nutrient.SUGARS to "Sucres"),
    )

    private class Model(private val ready: Boolean, private val outcome: LlmOutcome) : LanguageModel {
        override suspend fun isReady() = ready
        override val contextSize = 4_096
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit) = outcome
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel(
        model: LanguageModel = Model(ready = true, outcome = LlmOutcome.Success(COLA)),
        photos: FakePlanPhotos = FakePlanPhotos(),
        picture: ByteArray? = byteArrayOf(1, 2, 3),
        scanned: String? = "3560070565313",
        downloaded: EncodedImage? = PRODUCT_PHOTO,
    ) = FoodEntryViewModel(
        date = day,
        mealPlanRepository = MealPlanRepository { fake.api() },
        photos = photos,
        labelPictures = { picture },
        labelReader = NutritionLabelReader(model),
        barcodePictures = { scanned },
        products = OpenFoodFactsRepository(
            client = OkHttpClient(),
            userAgent = "umai/test",
            language = { "fr" },
            baseUrl = fake.baseUrl,
        ),
        photoDownloader = { downloaded },
        decimalSeparator = ',',
    )

    /** Waits for the state; a test that only needs the wait leaves the value. */
    @IgnorableReturnValue
    private suspend fun FoodEntryViewModel.await(predicate: (FoodEntryUiState) -> Boolean) =
        withTimeout(TIMEOUT_MS) { state.first(predicate) }

    @Test
    fun `the form starts on the product, as a snack, and needs a name to go on`() {
        val vm = viewModel()

        assertEquals(FoodEntryStep.PRODUCT, vm.state.value.step)
        assertEquals(FoodEntryMode.AUTO, vm.state.value.mode)
        assertFalse(vm.state.value.showsProductForm)
        assertEquals(MealType.SNACK, vm.state.value.mealType)
        vm.next()
        assertEquals(FoodEntryStep.PRODUCT, vm.state.value.step)

        vm.setName("Cola")
        vm.next()
        vm.next()
        assertEquals(FoodEntryStep.PORTION, vm.state.value.step)
        assertTrue(vm.previous())
        assertTrue(vm.previous())
        assertFalse(vm.previous())
    }

    @Test
    fun `a barcode scanned fills the product from Open Food Facts, with its photo`() = runBlocking {
        val photos = FakePlanPhotos()
        fake.enqueueJson(SANDWICH)
        val vm = viewModel(photos = photos)

        vm.readBarcodePicture("content://barcode")
        val state = vm.await { it.found != null && !it.processingPhoto }

        assertEquals(FoodEntryMode.AUTO, state.mode)
        assertTrue(state.showsProductForm)
        assertEquals("3560070565313", state.barcode)
        assertEquals("CLASSIC' Jambon Beurre", state.name)
        assertEquals(FoodUnit.GRAM, state.unit)
        assertEquals("238", state.values[Nutrient.ENERGY])
        assertEquals("4,7", state.values[Nutrient.SATURATED_FAT])
        assertEquals("125", state.quantity)
        assertEquals(298, state.calories)
        assertEquals("pending.jpg", state.photoPath)
        assertEquals(listOf(PRODUCT_PHOTO), photos.kept)
        assertEquals("/api/v2/product/3560070565313", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a barcode read by the camera is looked up`() = runBlocking {
        fake.enqueueJson(SANDWICH)
        val vm = viewModel(downloaded = null)

        vm.barcodeScanned("3560070565313")
        val state = vm.await { it.found != null }

        assertEquals("3560070565313", state.barcode)
        assertEquals("CLASSIC' Jambon Beurre", state.name)
        assertEquals("/api/v2/product/3560070565313", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a photo already taken is not replaced by the one of the database`() = runBlocking {
        val photos = FakePlanPhotos()
        fake.enqueueJson(SANDWICH)
        val vm = viewModel(photos = photos)
        vm.setPhoto("content://photo", CropRegion.Full)

        vm.readBarcodePicture("content://barcode")
        vm.await { it.found != null && !it.processingPhoto }

        assertTrue(photos.kept.isEmpty())
    }

    @Test
    fun `a product Open Food Facts does not know is described by hand`() = runBlocking {
        fake.enqueueJson("""{"status":0,"status_verbose":"product not found"}""", code = 404)
        val vm = viewModel()

        vm.readBarcodePicture("content://barcode")
        val state = vm.await { !it.searching && it.lookupIssue != null }

        assertEquals(LookupIssue.NOT_FOUND, state.lookupIssue)
        assertEquals(FoodEntryMode.MANUAL, state.mode)
        assertTrue(state.showsProductForm)
    }

    @Test
    fun `a product without nutrition keeps its name and has the rest typed`() = runBlocking {
        fake.enqueueJson("""{"status":1,"product":{"product_name":"Madeleines","nutriments":{}}}""")
        val vm = viewModel(downloaded = null)

        vm.readBarcodePicture("content://barcode")
        val state = vm.await { !it.searching && it.lookupIssue != null }

        assertEquals(LookupIssue.NO_NUTRITION, state.lookupIssue)
        assertEquals(FoodEntryMode.MANUAL, state.mode)
        assertEquals("Madeleines", state.name)
    }

    @Test
    fun `a photo without a barcode, or digits that are not one, are told apart`() = runBlocking {
        val unreadable = viewModel(scanned = null)
        unreadable.readBarcodePicture("content://barcode")
        assertEquals(LookupIssue.UNREADABLE, unreadable.await { !it.searching }.lookupIssue)

        val mistyped = viewModel()
        mistyped.setBarcode("3560070565314")
        mistyped.searchBarcode()
        assertEquals(LookupIssue.INVALID_CODE, mistyped.await { !it.searching }.lookupIssue)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a barcode typed is looked up, and a failure lets the user try again`() = runBlocking {
        fake.enqueueError(503)
        val vm = viewModel()
        vm.setBarcode("3560 0705 65313")

        vm.searchBarcode()
        val state = vm.await { !it.searching && it.lookupIssue != null }

        assertEquals(LookupIssue.FAILED, state.lookupIssue)
        assertEquals(FoodEntryMode.AUTO, state.mode)
        assertEquals("/api/v2/product/3560070565313", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a label read fills the values per 100 and suggests the can`() = runBlocking {
        val vm = viewModel()
        vm.await { it.canReadLabel }

        vm.readLabel("content://label")
        val state = vm.await { it.labelRead }

        assertEquals(FoodUnit.MILLILITRE, state.unit)
        assertEquals("42", state.values[Nutrient.ENERGY])
        assertEquals("10,6", state.values[Nutrient.CARBOHYDRATES])
        assertEquals("330", state.quantity)
        assertEquals(139, state.calories)
        assertNull(state.labelIssue)
    }

    @Test
    fun `a quantity already typed is kept when the label is read`() = runBlocking {
        val vm = viewModel()
        vm.setQuantity("500")

        vm.readLabel("content://label")
        val state = vm.await { it.labelRead }

        assertEquals("500", state.quantity)
        assertEquals(210, state.calories)
    }

    @Test
    fun `without a model the values are typed by hand`() = runBlocking {
        val vm = viewModel(model = Model(ready = false, outcome = LlmOutcome.Failure(LlmFailure.NOT_READY)))

        assertFalse(vm.state.value.canReadLabel)
        vm.setValue(Nutrient.ENERGY, "294")
        vm.setQuantity("150 g")

        assertEquals(441, vm.state.value.calories)
    }

    @Test
    fun `each reason a label is not read is told apart`() = runBlocking {
        val unreadable = viewModel(picture = null)
        unreadable.readLabel("content://label")
        assertEquals(LabelIssue.PICTURE_UNREADABLE, unreadable.await { !it.readingLabel }.labelIssue)

        val blind = viewModel(model = Model(true, LlmOutcome.Failure(LlmFailure.MEDIA_UNSUPPORTED)))
        blind.readLabel("content://label")
        assertEquals(LabelIssue.NO_VISION, blind.await { !it.readingLabel }.labelIssue)

        val empty = viewModel(model = Model(true, LlmOutcome.Success("""{"columns":[],"rows":[]}""")))
        empty.readLabel("content://label")
        assertEquals(LabelIssue.NOTHING_FOUND, empty.await { !it.readingLabel }.labelIssue)
    }

    @Test
    fun `the food becomes a note of the plan carrying its calories, with its photo kept`() = runBlocking {
        val photos = FakePlanPhotos()
        fake.enqueueJson(CREATED)
        val vm = viewModel(photos = photos)
        vm.setName(" Cola ")
        vm.setPhoto("content://photo", CropRegion.Full)
        vm.readLabel("content://label")
        vm.await { it.labelRead && it.photoPath != null }

        vm.save(labels)
        val state = vm.await { it.added != null }

        assertTrue(state.added!!.photoKept)
        val request = fake.takeRequest()
        assertEquals("/api/households/mealplans", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("2026-09-24", body["date"]!!.jsonPrimitive.content)
        assertEquals("snack", body["entryType"]!!.jsonPrimitive.content)
        assertEquals("Cola", body["title"]!!.jsonPrimitive.content)
        assertEquals("139 kcal · 330 ml\nGlucides 35 g · Sucres 35 g", body["text"]!!.jsonPrimitive.content)
        assertEquals(day to "pending.jpg", photos.attached[7])
    }

    @Test
    fun `a food without nutrition is added as a plain note`() = runBlocking {
        fake.enqueueJson(CREATED)
        val vm = viewModel()
        vm.setName("Pomme")

        vm.save(labels)
        vm.await { it.added != null }

        val body = Json.parseToJsonElement(fake.takeRequest().body!!.utf8()).jsonObject
        assertEquals("Pomme", body["title"]!!.jsonPrimitive.content)
        // An empty text is left out, as Mealie's default.
        assertEquals("", body["text"]?.jsonPrimitive?.content.orEmpty())
    }

    @Test
    fun `a failure keeps the form and its photo, and says why`() = runBlocking {
        val photos = FakePlanPhotos()
        fake.enqueueError(500)
        val vm = viewModel(photos = photos)
        vm.setName("Cola")
        vm.setPhoto("content://photo", CropRegion.Full)

        vm.save(labels)
        val state = vm.await { !it.saving && it.error != null }

        assertTrue(state.error is NetworkError.Server)
        assertNull(state.added)
        assertEquals("pending.jpg", state.photoPath)
        assertTrue(photos.attached.isEmpty())
    }

    @Test
    fun `a photo that cannot be kept is told when the food is added`() = runBlocking {
        fake.enqueueJson(CREATED)
        val vm = viewModel(photos = FakePlanPhotos(attaches = false))
        vm.setName("Cola")
        vm.setPhoto("content://photo", CropRegion.Full)

        vm.save(labels)

        assertFalse(vm.await { it.added != null }.added!!.photoKept)
    }

    @Test
    fun `a photo removed or replaced is discarded`() {
        val photos = FakePlanPhotos()
        val vm = viewModel(photos = photos)

        vm.setPhoto("content://photo", CropRegion.Full)
        vm.removePhoto()

        assertNull(vm.state.value.photoPath)
        assertEquals(listOf("pending.jpg"), photos.discarded)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        const val COLA = """
            {"columns": ["100 ml", "330 ml"],
             "rows": [
               {"name": "Energie", "values": ["180 kJ / 42 kcal", "594 kJ / 139 kcal"]},
               {"name": "Glucides", "values": ["10.6 g", "35 g"]},
               {"name": "dont sucres", "values": ["10.6 g", "35 g"]}
             ]}
        """

        val PRODUCT_PHOTO = EncodedImage(byteArrayOf(9), mediaType = "image/jpeg", extension = "jpg")

        const val SANDWICH = """
            {"code":"3560070565313","status":1,"product":{
              "product_name":"CLASSIC' Jambon Beurre","serving_quantity":125,"serving_quantity_unit":"g",
              "image_front_url":"https://images.example/front.jpg",
              "nutriments":{"energy-kcal_100g":238,"fat_100g":10,"saturated-fat_100g":4.7}}}
        """

        const val CREATED = """
            {"id":7,"date":"2026-09-24","entryType":"snack","title":"Cola","text":"139 kcal · 330 ml",
             "recipeId":null,"groupId":"g","userId":"u","householdId":"h","recipe":null}
        """
    }
}
