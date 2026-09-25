package org.opensources.umai.llm.data

import android.os.Build
import org.opensources.umai.llm.domain.AiBackend
import org.opensources.umai.llm.domain.DeviceProfile
import org.opensources.umai.llm.domain.TensorChip
import java.io.File

/**
 * What this phone offers the model, read without loading anything, and the
 * proof, once a model is loaded, that its backend's driver really is in use.
 */
object DeviceAccelerators {

    /** Shipped with the app (FetchTensorDispatch in app/build.gradle.kts). */
    const val TPU_DISPATCH_LIBRARY = "libLiteRtDispatch_GoogleTensor.so"

    /** The phone's own TPU driver, which the dispatch library hands the model to. */
    const val TPU_DRIVER = "libedgetpu_litert.so"

    /** The names LiteRT looks the GPU driver up by. */
    val GPU_DRIVERS = setOf("libOpenCL.so", "libOpenCL-pixel.so", "libOpenCL-car.so")

    private val VENDOR_LIBRARY_DIRS = listOf("/vendor/lib64", "/system_ext/lib64")

    fun profile(nativeLibraryDir: String, tpuGuard: TpuCrashGuard): DeviceProfile {
        val chip = TensorChip.of(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
        val socName = Build.SOC_MODEL.takeUnless { it.isBlank() || it == Build.UNKNOWN } ?: Build.HARDWARE
        return DeviceProfile(
            socName = socName,
            tensorChip = chip,
            tpuReachable = chip != null &&
                !tpuGuard.crashedBefore &&
                File(nativeLibraryDir, TPU_DISPATCH_LIBRARY).isFile &&
                VENDOR_LIBRARY_DIRS.any { File(it, TPU_DRIVER).isFile },
        )
    }

    /**
     * The driver [backend] needs that this process has not loaded, or null
     * when it is loaded. A runtime that accepted a backend but runs the model
     * elsewhere never loads its driver: the app never loads these libraries
     * itself, so their presence proves the backend is used.
     */
    fun missingDriver(backend: AiBackend, loaded: Set<String> = loadedLibraries()): String? = when (backend) {
        AiBackend.TPU -> listOf(TPU_DISPATCH_LIBRARY, TPU_DRIVER).firstOrNull { it !in loaded }
        AiBackend.GPU -> if (GPU_DRIVERS.any { it in loaded }) null else GPU_DRIVERS.first()
        AiBackend.CPU -> null
    }

    /** The file names of the shared libraries mapped in this process. */
    private fun loadedLibraries(): Set<String> = runCatching {
        File("/proc/self/maps").useLines { lines ->
            lines.map { it.substringAfterLast('/') }.filter { it.endsWith(".so") }.toSet()
        }
    }.getOrDefault(emptySet())
}

/**
 * Remembers a TPU that took the app down. A dispatch library that does not
 * match the phone's TPU driver crashes in native code, which no exception
 * handler catches: a marker is written before the TPU loads a model and
 * removed once it has. Found at the next start, it keeps the TPU aside, for
 * this build of the app on this build of the system only, and the model runs
 * on the GPU or the CPU instead.
 */
class TpuCrashGuard(private val marker: File, private val build: String) {

    val crashedBefore: Boolean = runCatching { marker.readText() == build }.getOrDefault(false)

    fun <T> loading(block: () -> T): T {
        marker.parentFile?.mkdirs()
        marker.writeText(build)
        try {
            return block()
        } finally {
            marker.delete()
        }
    }
}
