package org.opensources.umai.llm.data

/** Hears how a generation goes; returning false stops it. */
internal fun interface GenerationListener {
    /**
     * [promptDone] of [promptTotal] prompt tokens are read, then [generated]
     * tokens are written.
     */
    fun onProgress(promptDone: Int, promptTotal: Int, generated: Int): Boolean
}

/**
 * The llama.cpp functions of `llm_bridge.cpp`. Text crosses the bridge as
 * UTF-8 bytes. A handle is a loaded model with its context; `0` is none.
 */
internal object LlamaNative {

    /** Loads the native library; false when this device has no build of it. */
    fun load(): Boolean = runCatching { System.loadLibrary("umai_llm") }.isSuccess

    external fun nativeInit(nativeLibDir: String)

    external fun nativeSystemInfo(): String

    external fun nativeLoad(path: String, contextSize: Int, threads: Int): Long

    external fun nativeFree(handle: Long)

    external fun nativeContextSize(handle: Long): Int

    external fun nativeFormat(handle: Long, system: ByteArray, user: ByteArray): ByteArray?

    external fun nativeTokenCount(handle: Long, text: ByteArray): Int

    external fun nativeSchemaToGrammar(schema: ByteArray): ByteArray?

    external fun nativeGenerate(
        handle: Long,
        prompt: ByteArray,
        grammar: ByteArray?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        listener: GenerationListener,
    ): ByteArray?
}
