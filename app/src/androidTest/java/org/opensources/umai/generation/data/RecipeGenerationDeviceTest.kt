package org.opensources.umai.generation.data

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
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
            "Une recette vegan pour 2 personnes avec des lentilles et des carottes, environ 400 kcal par portion.",
            "Poulet carbonara; Omelette; Soupe de légumes", "fr",
        ).copy(seed = 42)
        assertTrue(LocalLanguageModel.estimatedTokens(request) <= file.contextSizeOn(AiBackend.TPU))
        val engine = LiteRtLmLoader(nativeDir, context.cacheDir.resolve("litertlm"), guard)
            .load(checkNotNull(path).path, AiBackend.TPU, file.contextSizeOn(AiBackend.TPU), null)
        try {
            val answer = withTimeout(600000) { engine.generate(request).toList().joinToString("") }
            val recipe = GeneratedRecipe.parse(answer)
            assertNotNull("The model must produce a complete usable recipe: $answer", recipe)
            assertTrue(checkNotNull(recipe).ingredients.none { ingredient ->
                listOf("poulet", "lardon", "oeuf", "œuf", "crème", "parmesan").any { it in ingredient.food.lowercase() }
            })
            assertNull(DeviceAccelerators.missingDriver(AiBackend.TPU))
        } finally {
            engine.close()
        }
    }
}
