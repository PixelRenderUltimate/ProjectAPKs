package com.pixelrender.app.graphics

import com.pixelrender.app.logging.Logger

data class BackendStatus(
    val backend: GraphicsBackend,
    val availability: BackendAvailability
) {
    val id: BackendId get() = backend.id
    val name: String get() = backend.id.displayName
    val available: Boolean get() = availability.available
}

object BackendRegistry {

    private val backends: List<GraphicsBackend> = listOf(
        ShizukuBackend,
        VulkanBackend,
        OpenGLBackend,
        StandardAndroidBackend
    )

    fun statuses(ctx: BackendContext): List<BackendStatus> =
        backends
            .map { BackendStatus(it, it.availability(ctx)) }
            .sortedByDescending { it.backend.priority }

    fun available(ctx: BackendContext): List<GraphicsBackend> =
        statuses(ctx).filter { it.available }.map { it.backend }

    /**
     * Mode Auto memilih backend tersedia dengan prioritas tertinggi.
     * Kalau tidak ada satu pun, hasilnya [UnsupportedBackend], bukan null.
     */
    fun resolve(selection: BackendId, ctx: BackendContext): GraphicsBackend {
        if (selection != BackendId.AUTO) {
            val chosen = backends.firstOrNull { it.id == selection }
            if (chosen != null && chosen.availability(ctx).available) return chosen
            Logger.w(
                "Backend ${selection.displayName} dipilih tetapi tidak tersedia",
                "Jatuh kembali ke mode Auto"
            )
        }
        return available(ctx).firstOrNull() ?: UnsupportedBackend
    }

    /** Menggabungkan pendapat semua backend yang tersedia menjadi satu matriks. */
    fun matrix(ctx: BackendContext): List<Capability> {
        val active = available(ctx).ifEmpty { listOf(UnsupportedBackend) }

        val byParameter = LinkedHashMap<GraphicsParameter, MutableList<BackendVerdict>>()
        GraphicsParameter.entries.forEach { byParameter[it] = mutableListOf() }

        active.forEach { backend ->
            backend.capabilities(ctx).forEach { cap ->
                byParameter.getValue(cap.parameter) += BackendVerdict(
                    backend = backend.id,
                    deviceSupport = cap.deviceSupport,
                    externalControl = cap.externalControl,
                    evidence = cap.evidence,
                    reason = cap.reason,
                    requires = cap.requires
                )
            }
        }

        val merged = CapabilityMerge.merge(byParameter)

        logSummary(active, merged)
        return merged
    }

    private fun logSummary(active: List<GraphicsBackend>, caps: List<Capability>) {
        Logger.i(
            "Backend aktif: " + active.joinToString(" > ") { "${it.id.displayName}(${it.role.label})" }
        )
        caps.forEach { cap ->
            val owner = cap.controlledBy?.displayName ?: "-"
            val line = "${cap.name}: device=${cap.deviceSupport}, " +
                    "control=${cap.externalControl} via $owner"
            when (cap.externalControl) {
                Support.SUPPORTED -> Logger.ok(line)
                Support.UNSUPPORTED -> Logger.w(line)
                else -> Logger.i(line)
            }
        }
        val controllable = caps.count { it.externalControl == Support.SUPPORTED }
        Logger.i(
            "Ringkasan: $controllable dari ${caps.size} parameter benar-benar dapat " +
                    "dikontrol untuk aplikasi lain pada perangkat ini"
        )
    }
}
