package org.opensources.umai.llm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.llm.data.DeviceAccelerators
import org.opensources.umai.llm.data.LiteRtLmLoader
import org.opensources.umai.llm.data.TpuCrashGuard
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.LocalModelCatalog
import org.opensources.umai.llm.domain.ModelFile
import java.io.File

/**
 * Runs the real model on each backend of the phone, when its files are in the
 * app's models folder (downloaded from the Local AI screen, or pushed with adb):
 * the proof that the TPU, the GPU and the CPU are really used. Skipped on a
 * phone without the files, or without the backend.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtLmBackendTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
    private val tpuGuard = TpuCrashGuard(context.cacheDir.resolve("tpu-loading-test"), "test")
    private val device = DeviceAccelerators.profile(nativeLibraryDir, tpuGuard)
    private val loader = LiteRtLmLoader(nativeLibraryDir, context.cacheDir.resolve("litertlm"), tpuGuard)
    private val model = LocalModelCatalog.recommended

    @Test
    fun theTensorTpuRunsTheModel() {
        assumeTrue("No TPU the app can reach on ${device.socName}", device.tpuReachable)
        val file = model.filesFor(device.tensorChip).first { AiBackend.TPU in it.backends }
        run(file, AiBackend.TPU)
    }

    @Test
    fun theGpuRunsTheModel() = run(universalFile(), AiBackend.GPU)

    @Test
    fun theCpuRunsTheModel() = run(universalFile(), AiBackend.CPU)

    private fun universalFile() = model.files.first { it.chip == null }

    private fun run(file: ModelFile, backend: AiBackend) = runBlocking {
        val path = context.getExternalFilesDir("models")?.resolve(file.fileName)
        assumeTrue("${file.fileName} is not on the phone", path?.isFile == true)
        logMemory(backend, "before")
        val engine = loader.load(checkNotNull(path).path, backend, file.contextSizeOn(backend))
        logMemory(backend, "loaded")
        try {
            assertEquals(backend, engine.backend)
            assertNull(DeviceAccelerators.missingDriver(backend))
            val answer = engine.generate(REQUEST).toList().joinToString("")
            logMemory(backend, "answered")
            val speed = checkNotNull(engine.lastSpeed) { "The runtime did not time the answer" }
            Log.i(
                TAG,
                "Backend: $backend | Model: ${model.name} | SoC: ${device.socName} | " +
                    "prompt ${speed.promptTokens} tokens at %.1f/s | answer ${speed.generatedTokens} tokens at %.1f/s | %s"
                        .format(speed.promptSpeed, speed.generationSpeed, answer),
            )
            val courses = (Json.parseToJsonElement(answer) as JsonObject)["items"]!!.jsonArray
            assertTrue(courses.isNotEmpty())
        } finally {
            engine.close()
        }
    }

    private fun logMemory(backend: AiBackend, stage: String) {
        val status = File("/proc/self/status").readLines()
            .filter { it.startsWith("RssAnon") || it.startsWith("RssFile") || it.startsWith("VmSwap") }
            .joinToString(" ") { it.replace(Regex("\\s+"), " ") }
        Log.i(TAG, "Memory $backend $stage: $status")
    }

    private companion object {
        const val TAG = "UmaiAiTest"

        val REQUEST = LlmRequest(
            system = "You sort recipes by course: main, dessert, drink or other.",
            user = "r1: Lasagnes à la bolognaise\nr2: Tarte aux pommes\nr3: Mojito",
            jsonSchema = """
                {"type": "object",
                 "properties": {"items": {"type": "array", "items": {"type": "object",
                   "properties": {"id": {"type": "string"}, "course": {"enum": ["main", "dessert", "drink", "other"]}},
                   "required": ["id", "course"]}}},
                 "required": ["items"]}
            """.trimIndent(),
            maxTokens = 128,
            temperature = 0f,
        )
    }
}
