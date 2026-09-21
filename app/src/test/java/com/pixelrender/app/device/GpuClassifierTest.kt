package com.pixelrender.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuClassifierTest {

    private fun family(renderer: String?, vendor: String? = null, vk: String? = null, id: Int? = null) =
        GpuClassifier.classify(renderer, vendor, vk, id).family

    @Test
    fun realRendererStrings() {
        assertEquals(GpuFamily.ADRENO, family("Adreno (TM) 730", "Qualcomm", "Adreno (TM) 730", 0x5143))
        assertEquals(GpuFamily.MALI, family("Mali-G78 MC24", "ARM", "Mali-G78 MC24", 0x13B5))
        assertEquals(GpuFamily.MALI, family("Mali-G57 MC2", "ARM"))
        assertEquals(GpuFamily.IMMORTALIS, family("Immortalis-G715 MC11", "ARM", "Immortalis-G715 MC11", 0x13B5))
        assertEquals(GpuFamily.XCLIPSE, family("Samsung Xclipse 920", "Samsung"))
        assertEquals(GpuFamily.POWERVR, family("PowerVR Rogue GE8320", "Imagination Technologies"))
        assertEquals(GpuFamily.POWERVR, family("IMG BXM-8-256", "Imagination Technologies"))
        assertEquals(GpuFamily.MALEOON, family("Maleoon 910", "HUAWEI"))
    }

    /** Immortalis dilaporkan dengan vendor ARM yang sama dengan Mali: urutan cek penting. */
    @Test
    fun immortalisIsNotMistakenForMali() {
        assertEquals(GpuFamily.IMMORTALIS, family("Immortalis-G720 MC12", "ARM", null, 0x13B5))
    }

    @Test
    fun angleIsDetectedWithoutLosingTheRealGpu() {
        val id = GpuClassifier.classify(
            "ANGLE (Samsung Xclipse 940) on Vulkan 1.3.264", "Samsung", "Samsung Xclipse 940", 0x144D
        )
        assertEquals(GpuFamily.XCLIPSE, id.family)
        assertTrue(id.viaAngle)
        assertFalse(GpuClassifier.classify("Adreno (TM) 740", "Qualcomm", null, null).viaAngle)
    }

    /** Emulator sering menyebut GPU host; tetap harus dianggap software. */
    @Test
    fun emulatorsAreSoftware() {
        assertEquals(GpuFamily.SOFTWARE, family(
            "ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero) (0x0000C0DE)), SwiftShader driver-5.0.0)"
        ))
        assertEquals(GpuFamily.SOFTWARE, family("Android Emulator OpenGL ES Translator (NVIDIA GeForce RTX 3080)"))
    }

    @Test
    fun vendorIdFallbackAndUnknowns() {
        assertEquals(GpuFamily.ADRENO, family(null, null, null, 0x5143))
        assertEquals(GpuFamily.POWERVR, family(null, null, null, 0x1010))
        assertEquals(GpuFamily.UNKNOWN, family(null, null, null, null))
        assertEquals(GpuFamily.OTHER, family("Vivante GC7000", "Vivante", null, 0x9999))
    }
}
