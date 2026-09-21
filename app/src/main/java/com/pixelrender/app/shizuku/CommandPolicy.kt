package com.pixelrender.app.shizuku

/**
 * Allowlist command. Ini satu-satunya tempat yang boleh memutuskan sebuah
 * command layak dijalankan lewat Shizuku.
 *
 * Aturannya:
 *  - Command tidak pernah dirangkai lewat shell. Selalu argv terpisah
 *    (ProcessBuilder), jadi tidak ada shell interpolation sama sekali.
 *  - Hanya command yang terdaftar persis di [RULES] yang lolos.
 *  - Setiap argumen bebas harus cocok dengan regex-nya sendiri.
 *  - Allowlist ini sengaja tidak berisi satu pun command yang menulis.
 *    Mulai PHASE 4, penulisan hanya lewat method bertipe di user service
 *    yang punya validasi sendiri (DisplayWritePolicy), bukan lewat shell.
 *  - Validasi dijalankan dua kali: di proses aplikasi sebelum dikirim, dan
 *    sekali lagi di dalam user service sebelum dieksekusi. Proses aplikasi
 *    tidak dipercaya oleh service.
 */
object CommandPolicy {

    /** Dinaikkan setiap kali daftar aturan berubah. Dicek lintas proses. */
    const val POLICY_VERSION = 4

    /** Tahap pengembangan aktif. Command bertanda writes ditolak di bawah 4. */
    const val ACTIVE_PHASE = 4

    /**
     * Mulai fase ini method tulis bertipe di user service (ukuran, density,
     * clear) diizinkan. Allowlist command shell di bawah TETAP tidak berisi
     * satu pun command yang menulis: tidak ada jalur tulis lewat shell.
     */
    const val PHASE_WRITES_ALLOWED = 4

    data class Verdict(
        val allowed: Boolean,
        val reason: String,
        val writes: Boolean = false,
        val description: String = ""
    )

    private data class Rule(
        val prefix: List<String>,
        val argPatterns: List<Regex> = emptyList(),
        val writes: Boolean = false,
        val description: String
    ) {
        val size: Int get() = prefix.size + argPatterns.size
        val display: String
            get() = (prefix + argPatterns.map { "<arg>" }).joinToString(" ")
    }

    private val NAMESPACE = Regex("^(global|system|secure)$")
    private val SETTING_KEY = Regex("^[A-Za-z0-9_.\\-]{1,64}$")
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    private val ALLOWED_PROPS = setOf(
        "ro.build.type",
        "ro.hardware",
        "ro.board.platform",
        "ro.soc.manufacturer",
        "ro.soc.model",
        "ro.hardware.vulkan",
        "ro.hardware.egl",
        "debug.hwui.renderer"
    )
    private val PROP_KEY = Regex("^[a-z0-9._]{1,64}$")

    /**
     * PHASE 2 sengaja hanya berisi command baca. Tidak ada satu pun aturan
     * yang mengubah state perangkat.
     */
    private val RULES = listOf(
        Rule(
            prefix = listOf("id"),
            description = "Verifikasi UID tempat command benar-benar dijalankan"
        ),
        Rule(
            prefix = listOf("wm", "size"),
            description = "Baca ukuran display fisik dan override yang aktif"
        ),
        Rule(
            prefix = listOf("wm", "density"),
            description = "Baca density display fisik dan override yang aktif"
        ),
        Rule(
            prefix = listOf("settings", "get"),
            argPatterns = listOf(NAMESPACE, SETTING_KEY),
            description = "Baca satu nilai Settings"
        ),
        Rule(
            prefix = listOf("device_config", "get", "game_overlay"),
            argPatterns = listOf(PACKAGE_NAME),
            description = "Baca Game Mode intervention untuk satu package"
        ),
        Rule(
            prefix = listOf("device_config", "list", "game_overlay"),
            description = "Lihat apakah namespace Game Mode intervention terbaca sama sekali"
        ),
        Rule(
            prefix = listOf("cmd", "game", "list-modes"),
            argPatterns = listOf(PACKAGE_NAME),
            description = "Baca Game Mode yang tersedia untuk satu package"
        ),
        Rule(
            prefix = listOf("cmd", "game", "list-configs"),
            argPatterns = listOf(PACKAGE_NAME),
            description = "Baca konfigurasi intervention (termasuk downscale) satu package"
        ),
        Rule(
            prefix = listOf("getprop"),
            argPatterns = listOf(PROP_KEY),
            description = "Baca satu system property dari daftar yang diizinkan"
        )
    )

    /** Karakter yang tidak boleh muncul di argumen mana pun, sebagai jaring pengaman. */
    private val FORBIDDEN = Regex("""[;&|<>`$\n\r\t\\"']""")

    fun validate(command: List<String>): Verdict {
        if (command.isEmpty()) {
            return Verdict(false, "Command kosong")
        }
        command.forEachIndexed { index, arg ->
            if (arg.isBlank()) {
                return Verdict(false, "Argumen ke-$index kosong")
            }
            if (FORBIDDEN.containsMatchIn(arg)) {
                return Verdict(false, "Argumen ke-$index mengandung karakter terlarang")
            }
            if (arg.length > 256) {
                return Verdict(false, "Argumen ke-$index terlalu panjang")
            }
        }

        val rule = RULES.firstOrNull { r ->
            command.size == r.size && command.take(r.prefix.size) == r.prefix
        } ?: return Verdict(
            false,
            "Tidak ada di allowlist: '${command.joinToString(" ")}'"
        )

        val freeArgs = command.drop(rule.prefix.size)
        freeArgs.forEachIndexed { index, arg ->
            if (!rule.argPatterns[index].matches(arg)) {
                return Verdict(false, "Argumen '$arg' tidak cocok dengan pola yang diizinkan")
            }
        }

        if (rule.prefix.firstOrNull() == "getprop" && freeArgs.firstOrNull() !in ALLOWED_PROPS) {
            return Verdict(false, "Property '${freeArgs.firstOrNull()}' tidak ada di allowlist")
        }

        if (rule.writes && ACTIVE_PHASE < PHASE_WRITES_ALLOWED) {
            return Verdict(
                false,
                "Command menulis ditolak pada PHASE $ACTIVE_PHASE",
                writes = true,
                description = rule.description
            )
        }

        return Verdict(true, "OK", rule.writes, rule.description)
    }

    fun allowlist(): List<Pair<String, String>> =
        RULES.map { it.display to it.description }
}
