package org.opensources.umai.generation.data

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opensources.umai.TestModelFiles
import org.opensources.umai.llm.data.DeviceAccelerators
import org.opensources.umai.llm.data.LiteRtLmLoader
import org.opensources.umai.llm.data.LocalLanguageModel
import org.opensources.umai.llm.data.TpuCrashGuard
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.LocalModelCatalog

/** Exercises the actual generation prompt on the Tensor TPU, without writing to Mealie. */
class RecipeGenerationDeviceTest {
    @Test
    fun createsACompleteRecipeOnPixel10Tpu() = runBlocking {
        val recipe = generate(
            "Une recette vegan pour 2 personnes avec des lentilles et des carottes, environ 400 kcal par portion.",
        )
        assertTrue(recipe.ingredients.none { ingredient ->
            listOf("poulet", "lardon", "oeuf", "œuf", "parmesan").any { it in ingredient.food.lowercase() }
        })
    }

    @Test
    fun specifiedWeightsAreKeptInTheRecipe() = runBlocking {
        val recipe = generate(
            "Pour 4 personnes : 300 g de spaghettis secs, 400 g de poulet cru, 400 g de tomates concassées, " +
                "100 g d’œufs sans coquille, 200 g d’épinards et 10 g d’huile d’olive. " +
                "Donne des étapes précises, surtout pour incorporer les œufs sans les brouiller.",
        )
        assertEquals(4, recipe.servings)
        assertEquals(300.0, recipe.ingredients.first { "spaghetti" in it.food.lowercase() || "pâte" in it.food.lowercase() }.grams, 0.01)
        assertEquals(400.0, recipe.ingredients.first { "poulet" in it.food.lowercase() }.grams, 0.01)
        assertEquals(10.0, recipe.ingredients.first { "huile" in it.food.lowercase() }.grams, 0.01)
    }

    @Test
    fun aVagueLowCalorieRequestStillHasWeighedIngredients() = runBlocking {
        val recipe = generate("Un repas peu calorique pour 2 personnes, avec des ingrédients simples et courants.")
        assertEquals(2, recipe.servings)
        assertTrue(recipe.steps.none { Regex("^\\s*\\d+[.)]").containsMatchIn(it) })
    }

    private suspend fun generate(prompt: String): GeneratedRecipe {
        assumeTrue("This acceptance test targets Pixel 10 Pro XL", Build.MODEL == "Pixel 10 Pro XL")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val guard = TpuCrashGuard(context.cacheDir.resolve("tpu-generation-test"), "test")
        val device = DeviceAccelerators.profile(nativeDir, guard)
        assumeTrue("TPU is unavailable", device.tpuReachable)
        val file = LocalModelCatalog.recommended.filesFor(device.tensorChip).first { AiBackend.TPU in it.backends }
        val path = TestModelFiles.pathFor(file.fileName)
        assumeTrue("The model is not installed", path?.isFile == true)
        val request = RecipeGenerationRepository.request(
            prompt,
            "Poulet carbonara; Omelette; Soupe de légumes", "fr",
        ).copy(seed = 42)
        assertTrue(LocalLanguageModel.estimatedTokens(request) <= file.contextSizeOn(AiBackend.TPU))
        val engine = LiteRtLmLoader(nativeDir, context.cacheDir.resolve("litertlm"), guard)
            .load(checkNotNull(path).path, AiBackend.TPU, file.contextSizeOn(AiBackend.TPU), null)
        try {
            val answer = withTimeout(600000) { engine.generate(request).toList().joinToString("") }
            val recipe = GeneratedRecipe.parse(answer)
            assertNotNull("The model must produce a complete usable recipe: $answer", recipe)
            Log.i("UmaiAiTest", "Generated recipe: $answer")
            assertTrue(checkNotNull(recipe).ingredients.all { it.line.contains(" g ") && it.grams > 0 })
            assertNull(DeviceAccelerators.missingDriver(AiBackend.TPU))
            return recipe
        } finally {
            engine.close()
        }
    }
}
