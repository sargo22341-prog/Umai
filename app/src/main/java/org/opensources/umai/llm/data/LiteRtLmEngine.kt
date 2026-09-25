package org.opensources.umai.llm.data

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.AiBackendUnavailable
import org.opensources.umai.llm.domain.AiEngine
import org.opensources.umai.llm.domain.AiEngineLoader
import org.opensources.umai.llm.domain.AiSpeed
import org.opensources.umai.llm.domain.LlmRequest
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads models with LiteRT-LM, Google's runtime for language models on
 * phones: the TPU of a Tensor chip through its dispatch library, the GPU
 * through OpenCL, the CPU through XNNPACK. No Google Play service is involved.
 */
@OptIn(ExperimentalApi::class)
class LiteRtLmLoader(
    private val nativeLibraryDir: String,
    private val cacheDir: File,
    private val tpuGuard: TpuCrashGuard,
) : AiEngineLoader {

    /** Whether the runtime's native library loads on this phone: it is only built for 64-bit ARM. */
    val isAvailable: Boolean by lazy { runCatching { System.loadLibrary(NATIVE_LIBRARY) }.isSuccess }

    /** The big cores do the work; the two smallest would only slow the others down. */
    private val cpuThreads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(MIN_THREADS, MAX_THREADS)

    override fun load(path: String, backend: AiBackend, contextSize: Int): AiEngine {
        // Asks the runtime to time each answer: the speeds tell in the logs where the model ran.
        ExperimentalFlags.enableBenchmark = true
        cacheDir.mkdirs()
        val engine = Engine(
            EngineConfig(
                modelPath = path,
                backend = when (backend) {
                    AiBackend.TPU -> Backend.NPU(nativeLibraryDir = nativeLibraryDir)
                    AiBackend.GPU -> Backend.GPU()
                    AiBackend.CPU -> Backend.CPU(threadCount = cpuThreads)
                },
                // A TPU build runs with the context it was compiled with.
                maxNumTokens = contextSize.takeUnless { backend == AiBackend.TPU },
                cacheDir = cacheDir.path,
            ),
        )
        try {
            if (backend == AiBackend.TPU) tpuGuard.loading { engine.initialize() } else engine.initialize()
        } catch (e: Exception) {
            engine.close()
            throw AiBackendUnavailable("LiteRT-LM could not load the model on $backend: ${e.message}", e)
        }
        // A backend whose driver refuses the model can leave the engine uninitialized without an error.
        if (!engine.isInitialized()) {
            engine.close()
            throw AiBackendUnavailable("LiteRT-LM did not initialize the model on $backend")
        }
        DeviceAccelerators.missingDriver(backend)?.let { driver ->
            engine.close()
            throw AiBackendUnavailable("LiteRT-LM accepted $backend but $driver is not loaded: the model would run elsewhere")
        }
        return LiteRtLmEngine(engine, backend)
    }

    private companion object {
        const val NATIVE_LIBRARY = "litertlm_jni"
        const val MIN_THREADS = 2
        const val MAX_THREADS = 6
    }
}

/**
 * One loaded model. The answer is held to the JSON schema of the request by
 * the runtime's constrained decoding: this version of LiteRT-LM (the one the
 * published Tensor dispatch library is built with) only constrains tool calls,
 * so the schema becomes the parameters of the one tool the model answers with.
 * The runtime hands a tool call over whole, once written.
 */
@OptIn(ExperimentalApi::class)
private class LiteRtLmEngine(private val engine: Engine, override val backend: AiBackend) : AiEngine {

    @Volatile
    override var lastSpeed: AiSpeed? = null
        private set

    override fun generate(request: LlmRequest): Flow<String> = callbackFlow {
        val conversation = synchronized(LiteRtLmEngine::class) {
            // Read when the conversation is created, and global: set only around that.
            ExperimentalFlags.enableConversationConstrainedDecoding = true
            try {
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(request.system),
                        tools = listOf(tool(AnswerTool(request.jsonSchema))),
                        // A TPU build samples inside its compiled graph, which takes no sampler settings.
                        samplerConfig = if (backend == AiBackend.TPU) {
                            null
                        } else {
                            SamplerConfig(topK = TOP_K, topP = TOP_P, temperature = request.temperature.toDouble(), seed = request.seed)
                        },
                        automaticToolCalling = false,
                    ),
                )
            } finally {
                ExperimentalFlags.enableConversationConstrainedDecoding = false
            }
        }
        val finished = CountDownLatch(1)
        conversation.sendMessageAsync(
            request.user,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val answer = message.toolCalls.firstOrNull { it.name == AnswerTool.NAME }
                    trySend(answer?.let { toJson(it.arguments).toString() } ?: message.text())
                }

                override fun onDone() {
                    lastSpeed = runCatching { conversation.getBenchmarkInfo().toSpeed() }.getOrNull()
                    finished.countDown()
                    channel.close()
                }

                override fun onError(throwable: Throwable) {
                    finished.countDown()
                    channel.close(throwable)
                }
            },
        )
        awaitClose {
            // The flow was cancelled mid-answer: the native side stops at the next token,
            // and the conversation is only freed once it has.
            if (finished.count > 0) {
                conversation.cancelProcess()
                finished.await(CANCEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            conversation.close()
        }
    }.buffer(Channel.UNLIMITED)

    override fun close() = engine.close()

    private fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun BenchmarkInfo.toSpeed() = AiSpeed(
        promptTokens = lastPrefillTokenCount,
        promptSpeed = lastPrefillTokensPerSecond,
        generatedTokens = lastDecodeTokenCount,
        generationSpeed = lastDecodeTokensPerSecond,
    )

    /** The tool whose parameters are the answer; the app reads the call, never runs it. */
    private class AnswerTool(private val schema: String) : OpenApiTool {

        override fun getToolDescriptionJsonString(): String =
            """{"name": "$NAME", "description": "Gives the complete answer.", "parameters": $schema}"""

        override fun execute(paramsJsonString: String): String = ""

        companion object {
            const val NAME = "answer"
        }
    }

    private companion object {
        const val TOP_K = 40
        const val TOP_P = 0.95
        const val CANCEL_TIMEOUT_SECONDS = 10L

        /** The runtime reads tool arguments as JSON numbers, maps and lists; whole numbers come back as doubles. */
        fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
            is List<*> -> JsonArray(value.map(::toJson))
            is Boolean -> JsonPrimitive(value)
            is Double -> if (value % 1.0 == 0.0) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
