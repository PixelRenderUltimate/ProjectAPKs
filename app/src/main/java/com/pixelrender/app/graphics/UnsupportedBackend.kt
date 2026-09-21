package com.pixelrender.app.graphics

/**
 * Dipakai hanya kalau tidak ada backend lain yang tersedia. Fungsinya adalah
 * menjawab dengan jujur bahwa tidak ada yang bisa dilakukan, bukan diam-diam
 * gagal.
 */
object UnsupportedBackend : GraphicsBackend {

    override val id = BackendId.UNSUPPORTED
    override val role = BackendRole.DETECTION_ONLY
    override val priority = 0

    override fun availability(ctx: BackendContext) =
        BackendAvailability(true, "Fallback terakhir")

    override fun capabilities(ctx: BackendContext): List<BackendCapability> =
        GraphicsParameter.entries.map { parameter ->
            BackendCapability(
                parameter = parameter,
                deviceSupport = Support.UNKNOWN,
                externalControl = Support.UNSUPPORTED,
                evidence = "Tidak ada backend yang tersedia",
                reason = "Deteksi GPU gagal dan tidak ada akses privileged, jadi tidak " +
                        "ada satu pun parameter yang bisa dipastikan maupun diubah."
            )
        }

    override fun apply(ctx: BackendContext, changes: List<ParameterChange>): BackendResult =
        BackendResult.Rejected("Tidak ada backend yang bisa menerapkan perubahan")

    override fun restore(ctx: BackendContext, original: List<ParameterChange>): BackendResult =
        BackendResult.Rejected("Tidak ada yang bisa dikembalikan")
}
