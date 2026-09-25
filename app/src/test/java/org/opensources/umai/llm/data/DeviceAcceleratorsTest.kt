package org.opensources.umai.llm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.umai.llm.domain.AiBackend

/** A backend counts as used only once the runtime has loaded its driver. */
class DeviceAcceleratorsTest {

    @Test
    fun `the TPU needs both the dispatch library and the phone's TPU driver`() {
        val both = setOf("libLiteRtDispatch_GoogleTensor.so", "libedgetpu_litert.so", "liblitertlm_jni.so")

        assertNull(DeviceAccelerators.missingDriver(AiBackend.TPU, both))
        assertEquals(
            "libedgetpu_litert.so",
            DeviceAccelerators.missingDriver(AiBackend.TPU, setOf("libLiteRtDispatch_GoogleTensor.so")),
        )
        assertEquals(
            "libLiteRtDispatch_GoogleTensor.so",
            DeviceAccelerators.missingDriver(AiBackend.TPU, setOf("liblitertlm_jni.so")),
        )
    }

    @Test
    fun `the GPU needs an OpenCL driver, the CPU nothing`() {
        assertNull(DeviceAccelerators.missingDriver(AiBackend.GPU, setOf("libOpenCL-pixel.so")))
        assertEquals("libOpenCL.so", DeviceAccelerators.missingDriver(AiBackend.GPU, setOf("libvulkan.so")))
        assertNull(DeviceAccelerators.missingDriver(AiBackend.CPU, emptySet()))
    }
}
