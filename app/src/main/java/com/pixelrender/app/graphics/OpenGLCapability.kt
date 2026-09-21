package com.pixelrender.app.graphics

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.pixelrender.app.logging.Logger

data class GlInfo(
    val available: Boolean,
    val vendor: String = "",
    val renderer: String = "",
    val versionString: String = "",
    val glslVersion: String = "",
    val esMajor: Int = 0,
    val esMinor: Int = 0,
    val maxTextureSize: Int = 0,
    val maxSamples: Int = 0,
    val anisotropySupported: Boolean = false,
    val maxAnisotropy: Float = 0f,
    val extensions: List<String> = emptyList(),
    val error: String? = null
) {
    val esVersion: String get() = if (esMajor > 0) "$esMajor.$esMinor" else "unknown"

    /** GL_TEXTURE_MIN_LOD / GL_TEXTURE_MAX_LOD tersedia sejak GLES 3.0. */
    val lodClampSupported: Boolean get() = esMajor >= 3

    /** LOD bias eksplisit hanya lewat ekstensi (bukan core di GLES). */
    val lodBiasSupported: Boolean
        get() = extensions.any { it.equals("GL_EXT_texture_lod_bias", true) }
}

/**
 * Membaca kemampuan OpenGL ES perangkat lewat context EGL offscreen (PBuffer 1x1).
 *
 * PENTING: semua yang dibaca di sini adalah kemampuan GPU seperti yang terlihat
 * DARI PROSES APLIKASI INI. Ini bukan, dan tidak bisa menjadi, kontrol atas
 * state GL proses aplikasi lain.
 */
object OpenGLCapability {

    private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
    private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
    private const val GL_MAX_SAMPLES = 0x8D57
    private const val GL_SHADING_LANGUAGE_VERSION = 0x8B8C

    /** Harus dipanggil dari SATU thread (EGL context terikat ke thread). */
    fun queryBlocking(context: Context): GlInfo {
        val declared = declaredEsVersion(context)
        Logger.i("ActivityManager reports GLES $declared")

        var display: EGLDisplay? = null
        var eglContext: EGLContext? = null
        var surface: EGLSurface? = null

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                return GlInfo(false, error = "eglGetDisplay() mengembalikan EGL_NO_DISPLAY")
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return GlInfo(false, error = "eglInitialize() gagal (0x${eglErrorHex()})")
            }

            var clientVersion = 3
            var config = chooseConfig(display, EGL_OPENGL_ES3_BIT_KHR)
            if (config == null) {
                clientVersion = 2
                config = chooseConfig(display, EGL14.EGL_OPENGL_ES2_BIT)
            }
            if (config == null) {
                return GlInfo(false, error = "Tidak ada EGLConfig yang cocok")
            }

            eglContext = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE), 0
            )
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                return GlInfo(false, error = "eglCreateContext() gagal (0x${eglErrorHex()})")
            }

            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                return GlInfo(false, error = "eglCreatePbufferSurface() gagal (0x${eglErrorHex()})")
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
                return GlInfo(false, error = "eglMakeCurrent() gagal (0x${eglErrorHex()})")
            }

            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            val versionStr = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            val glsl = GLES20.glGetString(GL_SHADING_LANGUAGE_VERSION) ?: ""
            val extensions = (GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: "")
                .split(' ')
                .filter { it.isNotBlank() }
                .sorted()

            val (major, minor) = parseEsVersion(versionStr, clientVersion)

            val tmpInt = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, tmpInt, 0)
            val maxTexture = tmpInt[0]

            var maxSamples = 0
            if (major >= 3) {
                GLES20.glGetIntegerv(GL_MAX_SAMPLES, tmpInt, 0)
                if (GLES20.glGetError() == GLES20.GL_NO_ERROR) maxSamples = tmpInt[0]
            }

            val aniso = extensions.any { it.equals("GL_EXT_texture_filter_anisotropic", true) }
            var maxAniso = 0f
            if (aniso) {
                val tmpFloat = FloatArray(1)
                GLES20.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, tmpFloat, 0)
                if (GLES20.glGetError() == GLES20.GL_NO_ERROR) maxAniso = tmpFloat[0]
            }

            val info = GlInfo(
                available = true,
                vendor = vendor,
                renderer = renderer,
                versionString = versionStr,
                glslVersion = glsl,
                esMajor = major,
                esMinor = minor,
                maxTextureSize = maxTexture,
                maxSamples = maxSamples,
                anisotropySupported = aniso,
                maxAnisotropy = maxAniso,
                extensions = extensions
            )

            Logger.ok("GPU: $renderer", "vendor: $vendor")
            Logger.i("OpenGL ES ${info.esVersion}", versionStr)
            Logger.i(
                "GL limits",
                "maxTextureSize=$maxTexture, maxSamples=$maxSamples, " +
                        "anisotropic=${if (aniso) "yes (max ${maxAniso.toInt()}x)" else "no"}, " +
                        "extensions=${extensions.size}"
            )
            return info
        } catch (t: Throwable) {
            Logger.e("OpenGL probe gagal", t.message ?: t.javaClass.simpleName)
            return GlInfo(false, error = t.message ?: t.javaClass.simpleName)
        } finally {
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface)
                }
                if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, eglContext)
                }
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun chooseConfig(display: EGLDisplay, renderableType: Int): EGLConfig? {
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        return if (ok && num[0] > 0) configs[0] else null
    }

    private fun parseEsVersion(versionString: String, fallbackMajor: Int): Pair<Int, Int> {
        val match = Regex("""OpenGL ES\s+(\d+)\.(\d+)""").find(versionString)
        return if (match != null) {
            (match.groupValues[1].toIntOrNull() ?: fallbackMajor) to
                    (match.groupValues[2].toIntOrNull() ?: 0)
        } else {
            fallbackMajor to 0
        }
    }

    private fun declaredEsVersion(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "unknown"
        val v = am.deviceConfigurationInfo.reqGlEsVersion
        return "${(v shr 16) and 0xFFFF}.${v and 0xFFFF}"
    }

    private fun eglErrorHex(): String = Integer.toHexString(EGL14.eglGetError())
}
