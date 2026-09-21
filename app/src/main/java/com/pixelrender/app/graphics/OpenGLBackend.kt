package com.pixelrender.app.graphics

/**
 * Melaporkan kemampuan GPU seperti yang terlihat lewat OpenGL ES dari proses
 * aplikasi ini. Murni pembacaan; externalControl selalu UNSUPPORTED karena
 * context GL tidak pernah bisa menyeberangi batas proses.
 */
object OpenGLBackend : GraphicsBackend {

    override val id = BackendId.OPENGL
    override val role = BackendRole.DETECTION_ONLY
    override val priority = 20

    override fun availability(ctx: BackendContext): BackendAvailability {
        val gl = ctx.gl
        return if (gl.available) {
            BackendAvailability(true, "OpenGL ES ${gl.esVersion} pada ${gl.renderer}")
        } else {
            BackendAvailability(false, gl.error ?: "Context EGL tidak bisa dibuat")
        }
    }

    override fun capabilities(ctx: BackendContext): List<BackendCapability> {
        val gl = ctx.gl
        if (!gl.available) return emptyList()

        return listOf(
            BackendCapability(
                parameter = GraphicsParameter.TEXTURE_FILTERING,
                deviceSupport = Support.SUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = "GL_NEAREST dan GL_LINEAR selalu ada di GLES ${gl.esVersion}",
                reason = PER_PROCESS_REASON
            ),
            BackendCapability(
                parameter = GraphicsParameter.ANISOTROPIC_FILTERING,
                deviceSupport = if (gl.anisotropySupported) Support.SUPPORTED else Support.UNSUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = if (gl.anisotropySupported)
                    "GL_EXT_texture_filter_anisotropic ada, maksimum ${gl.maxAnisotropy.toInt()}x"
                else "GL_EXT_texture_filter_anisotropic tidak ada",
                reason = PER_PROCESS_REASON
            ),
            BackendCapability(
                parameter = GraphicsParameter.MIPMAP_LOD,
                deviceSupport = if (gl.lodClampSupported) Support.SUPPORTED
                else Support.PARTIALLY_SUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = buildString {
                    append(
                        if (gl.lodClampSupported) "GL_TEXTURE_MIN_LOD dan MAX_LOD ada (GLES 3.0+)"
                        else "LOD clamp butuh GLES 3.0, perangkat ini GLES ${gl.esVersion}"
                    )
                    append("; LOD bias eksplisit: ")
                    append(if (gl.lodBiasSupported) "GL_EXT_texture_lod_bias ada" else "tidak ada")
                },
                reason = PER_PROCESS_REASON
            ),
            BackendCapability(
                parameter = GraphicsParameter.MSAA,
                deviceSupport = if (gl.maxSamples >= 2) Support.SUPPORTED else Support.UNSUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = "GL_MAX_SAMPLES = ${gl.maxSamples}",
                reason = "$PER_PROCESS_REASON MSAA juga ditentukan saat game membuat framebuffer."
            )
        )
    }
}
