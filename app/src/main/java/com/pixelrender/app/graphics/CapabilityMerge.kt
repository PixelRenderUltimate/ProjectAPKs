package com.pixelrender.app.graphics

// Murni: tidak ada Android SDK, tidak ada Logger. Diuji di app/src/test.

/** Pendapat satu backend tentang satu parameter. */
data class BackendVerdict(
    val backend: BackendId,
    val deviceSupport: Support,
    val externalControl: Support,
    val evidence: String,
    val reason: String,
    val requires: String?
)

/**
 * Hasil gabungan. [verdicts] sengaja dipertahankan utuh supaya UI bisa
 * menampilkan pendapat tiap backend, bukan hanya kesimpulannya.
 */
data class Capability(
    val parameter: GraphicsParameter,
    val deviceSupport: Support,
    val externalControl: Support,
    val controlledBy: BackendId?,
    val verdicts: List<BackendVerdict>
) {
    val name: String get() = parameter.label
    val evidence: String get() = verdicts.firstOrNull()?.evidence.orEmpty()
    val reason: String
        get() = verdicts
            .firstOrNull { it.externalControl == externalControl }
            ?.reason
            ?: verdicts.firstOrNull()?.reason.orEmpty()
    val requires: String?
        get() = verdicts.firstOrNull { it.externalControl == externalControl }?.requires
}

object CapabilityMerge {

    /**
     * Menggabungkan pendapat semua backend menjadi satu baris per parameter.
     *
     * Aturan:
     *  - device dan control masing-masing diambil dari peringkat tertinggi
     *    ([Support.rank]): SUPPORTED > PARTIALLY > UNKNOWN > UNSUPPORTED.
     *  - [Capability.controlledBy] hanya diisi kalau ada backend yang benar-benar
     *    menyatakan SUPPORTED atau PARTIALLY_SUPPORTED.
     *  - Semua pendapat dipertahankan, urut dari control terkuat.
     *  - Parameter tanpa pendapat sama sekali tidak dimunculkan.
     */
    fun merge(byParameter: Map<GraphicsParameter, List<BackendVerdict>>): List<Capability> =
        byParameter.mapNotNull { (parameter, verdicts) ->
            if (verdicts.isEmpty()) return@mapNotNull null
            val device = verdicts.maxByOrNull { it.deviceSupport.rank }!!.deviceSupport
            val best = verdicts.maxByOrNull { it.externalControl.rank }!!
            Capability(
                parameter = parameter,
                deviceSupport = device,
                externalControl = best.externalControl,
                controlledBy = if (best.externalControl == Support.SUPPORTED ||
                    best.externalControl == Support.PARTIALLY_SUPPORTED
                ) best.backend else null,
                verdicts = verdicts.sortedByDescending { it.externalControl.rank }
            )
        }
}
