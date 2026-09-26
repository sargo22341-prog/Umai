package org.opensources.umai.planning

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.core.image.DeviceImageCropper
import org.opensources.umai.llm.data.LiteRtLmLoader
import org.opensources.umai.llm.data.TpuCrashGuard
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.AiEngine
import org.opensources.umai.llm.domain.AiSense
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.planning.data.DeviceLabelPictures
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.LabelOutcome
import org.opensources.umai.planning.domain.LabelReading
import org.opensources.umai.planning.domain.NutritionLabelReader
import org.opensources.umai.planning.domain.Nutrient
import kotlin.math.abs

/**
 * Reads real nutrition labels with the real model, the way the app does: the
 * photo scaled by [DeviceLabelPictures], then the model with its vision part,
 * on the CPU where the app runs it first. Skipped on a phone without the
 * model, or without the photos, which are not in the repository; push them
 * with adb first:
 *
 * ```
 * adb push cola_nutrition_test.jpg Sandwich_nutrition_test.jpg /sdcard/Android/data/org.opensources.umai.debug/files/labels/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class NutritionLabelDeviceTest {

    @Test
    fun aCanOfColaIsReadPer100MillilitresWithItsCan() {
        val reading = read("cola_nutrition_test.jpg")

        assertEquals(FoodUnit.MILLILITRE, reading.unit)
        assertClose(42.0, reading.per100[Nutrient.ENERGY])
        assertClose(0.0, reading.per100[Nutrient.FAT])
        assertClose(10.6, reading.per100[Nutrient.CARBOHYDRATES])
        assertClose(10.6, reading.per100[Nutrient.SUGARS])
        assertClose(0.0, reading.per100[Nutrient.PROTEIN])
        assertNull("The label prints no fibre", reading.per100[Nutrient.FIBER])
        assertClose(330.0, reading.portion)
    }

    @Test
    fun aSandwichIsReadPer100Grams() {
        val reading = read("Sandwich_nutrition_test.jpg")

        assertEquals(FoodUnit.GRAM, reading.unit)
        assertClose(294.0, reading.per100[Nutrient.ENERGY])
        assertClose(14.0, reading.per100[Nutrient.FAT])
        assertClose(1.4, reading.per100[Nutrient.SATURATED_FAT])
        assertClose(30.0, reading.per100[Nutrient.CARBOHYDRATES])
        assertClose(3.3, reading.per100[Nutrient.SUGARS])
        assertClose(9.8, reading.per100[Nutrient.PROTEIN])
        assertClose(1.3, reading.per100[Nutrient.SALT])
        assertNull("The label has one column", reading.portion)
    }

    private fun read(name: String): LabelReading = runBlocking {
        val photo = context.getExternalFilesDir("labels")?.resolve(name)
        assumeTrue("$name is not on the phone", photo?.isFile == true)
        val model = checkNotNull(model())

        val jpeg = DeviceLabelPictures(context, DeviceImageCropper(context)).read(Uri.fromFile(photo).toString())
        assertNotNull("The photo could not be read", jpeg)
        val started = SystemClock.elapsedRealtime()
        val outcome = NutritionLabelReader(model).read(checkNotNull(jpeg))
        Log.i(TAG, "$name: ${SystemClock.elapsedRealtime() - started} ms | $outcome")
        assertTrue("Not read: $outcome", outcome is LabelOutcome.Read)
        (outcome as LabelOutcome.Read).reading
    }

    private fun assertClose(expected: Double, actual: Double?) {
        assertNotNull("Expected $expected, nothing read", actual)
        assertTrue("Expected $expected, read $actual", abs(expected - checkNotNull(actual)) < 0.05)
    }

    /** The loaded engine as the reader sees a model, logging what it answers. */
    private class EngineModel(private val engine: AiEngine) : LanguageModel {
        override suspend fun isReady(): Boolean = true

        override val contextSize: Int = LocalModelCatalog.recommended.files.first { it.chip == null }.contextSizeOn(AiBackend.CPU)

        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            val answer = engine.generate(request).toList().joinToString("")
            Log.i(TAG, "Answer: $answer")
            return LlmOutcome.Success(answer)
        }
    }

    companion object {
        private const val TAG = "UmaiLabelTest"

        private val context = InstrumentationRegistry.getInstrumentation().targetContext

        /** One engine for the class: the model takes gigabytes, loaded once and freed after. */
        private var engine: AiEngine? = null

        private fun model(): LanguageModel? {
            engine?.let { return EngineModel(it) }
            val file = LocalModelCatalog.recommended.files.first { it.chip == null }
            val path = context.getExternalFilesDir("models")?.resolve(file.fileName)
            assumeTrue("${file.fileName} is not on the phone", path?.isFile == true)
            val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            val guard = TpuCrashGuard(context.cacheDir.resolve("tpu-loading-test"), "test")
            val loader = LiteRtLmLoader(nativeLibraryDir, context.cacheDir.resolve("litertlm"), guard)
            val loaded = loader.load(checkNotNull(path).path, AiBackend.CPU, file.contextSizeOn(AiBackend.CPU), AiSense.SIGHT)
            engine = loaded
            return EngineModel(loaded)
        }

        @JvmStatic
        @AfterClass
        fun closeEngine() {
            engine?.close()
            engine = null
        }
    }
}
