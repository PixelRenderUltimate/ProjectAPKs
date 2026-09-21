package com.pixelrender.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelrender.app.device.DeviceInfo
import com.pixelrender.app.device.DeviceInfoCollector
import com.pixelrender.app.device.GpuClassifier
import com.pixelrender.app.device.GpuIdentity
import com.pixelrender.app.diagnostics.DiagnosticReport
import com.pixelrender.app.game.GameDetector
import com.pixelrender.app.game.GameTarget
import com.pixelrender.app.graphics.BackendContext
import com.pixelrender.app.graphics.BackendId
import com.pixelrender.app.graphics.BackendRegistry
import com.pixelrender.app.graphics.BackendStatus
import com.pixelrender.app.graphics.Capability
import com.pixelrender.app.graphics.DisplayOverrideController
import com.pixelrender.app.graphics.DisplayOverrideController.Landing
import com.pixelrender.app.graphics.DisplayOverrideController.Outcome
import com.pixelrender.app.graphics.DisplayPlanner
import com.pixelrender.app.graphics.GlInfo
import com.pixelrender.app.graphics.GraphicsProfile
import com.pixelrender.app.graphics.GraphicsProfiles
import com.pixelrender.app.graphics.OpenGLCapability
import com.pixelrender.app.graphics.ProfileId
import com.pixelrender.app.graphics.ProfileResolver
import com.pixelrender.app.graphics.ProfileState
import com.pixelrender.app.graphics.ResolvedProfile
import com.pixelrender.app.graphics.SettingOutcome
import com.pixelrender.app.graphics.SettingsShortcut
import com.pixelrender.app.graphics.ShizukuBackend
import com.pixelrender.app.graphics.StandardAndroidBackend
import com.pixelrender.app.graphics.Support
import com.pixelrender.app.graphics.VulkanCapability
import com.pixelrender.app.graphics.VulkanInfo
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.shizuku.DisplayState
import com.pixelrender.app.shizuku.ProbeReport
import com.pixelrender.app.shizuku.ShizukuManager
import com.pixelrender.app.shizuku.ShizukuProbe
import com.pixelrender.app.shizuku.ShizukuState
import com.pixelrender.app.shizuku.ShizukuStatus
import com.pixelrender.app.shizuku.ShizukuUserServiceClient
import com.pixelrender.app.shizuku.UserServiceState
import com.pixelrender.app.shizuku.UserServiceStatus
import com.pixelrender.app.state.ActiveProfile
import com.pixelrender.app.state.BackupStore
import com.pixelrender.app.state.ChangeJournal
import com.pixelrender.app.state.DisplaySnapshot
import com.pixelrender.app.state.JournalState
import com.pixelrender.app.state.ManualVerdictStore
import com.pixelrender.app.state.OperationKind
import com.pixelrender.app.state.OperationRecord
import com.pixelrender.app.state.ProfileStore
import com.pixelrender.app.state.ReconcileAction
import com.pixelrender.app.state.RecoveryPolicy
import com.pixelrender.app.state.RecoveryState
import com.pixelrender.app.state.currentBootCount
import com.pixelrender.app.testing.TestEvidence
import com.pixelrender.app.testing.TestPlan
import com.pixelrender.app.testing.TestResult
import com.pixelrender.app.testing.TestVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class ConfirmState(val secondsLeft: Int, val description: String)

data class UiState(
    val scanning: Boolean = true,
    val probing: Boolean = false,
    val device: DeviceInfo? = null,
    val gl: GlInfo? = null,
    val vulkan: VulkanInfo? = null,
    val shizuku: ShizukuState = ShizukuState(),
    val userService: UserServiceState = UserServiceState(),
    val capabilities: List<Capability> = emptyList(),
    val backends: List<BackendStatus> = emptyList(),
    val selectedBackend: BackendId = BackendId.AUTO,
    val activeBackend: BackendId? = null,
    val shortcuts: List<SettingsShortcut> = emptyList(),
    val games: List<GameTarget> = emptyList(),
    val probe: ProbeReport? = null,
    val baselines: List<BackupStore.Entry> = emptyList(),
    val pendingChanges: List<BackupStore.Entry> = emptyList(),
    val pendingDisplay: DisplaySnapshot? = null,
    val applying: Boolean = false,
    val confirm: ConfirmState? = null,
    val selectedProfile: ProfileId = ProfileId.PIXEL_MEDIUM,
    val customScale: Float = 0.66f,
    val activeProfile: ActiveProfile? = null,
    val selectedGame: String? = null,
    // PHASE 6
    val journalState: JournalState = JournalState.NONE,
    val rebootedSinceConfirm: Boolean = false,
    val history: List<OperationRecord> = emptyList(),
    // PHASE 7
    val manualVerdicts: Map<String, TestVerdict> = emptyMap()
) {
    val availableBackends: List<String>
        get() = backends.filter { it.available }.map { it.name }

    val selectableBackends: List<BackendId>
        get() = listOf(BackendId.AUTO) + backends.filter { it.available }.map { it.id }

    val controllableCount: Int
        get() = capabilities.count { it.externalControl == Support.SUPPORTED }

    val installedGames: List<GameTarget>
        get() = games.filter { it.installed }

    val displayPlans: List<DisplayPlanner.Plan>
        get() = probe?.display?.takeIf { it.ok }
            ?.let { DisplayPlanner.plans(it.initialWidth, it.initialHeight, it.initialDensity) }
            .orEmpty()

    val profile: GraphicsProfile
        get() = GraphicsProfiles.forId(selectedProfile, customScale)

    val resolvedProfile: ResolvedProfile
        get() = ProfileResolver.resolve(
            profile = profile,
            capabilities = capabilities,
            activeBackend = activeBackend,
            panel = probe?.display,
            hasPendingChanges = pendingDisplay != null
        )

    /** Selama ada perubahan yang perlu dipulihkan, apply baru ditahan. */
    val applyBlockedReason: String?
        get() = when {
            applying -> "Sedang memproses"
            confirm != null -> "Menunggu konfirmasi perubahan sebelumnya"
            RecoveryPolicy.blocksNewApply(recovery) ->
                "Ada perubahan yang belum dikonfirmasi. Selesaikan pemulihan dulu."
            else -> resolvedProfile.blockedReason
        }

    val profileState: ProfileState
        get() {
            val active = activeProfile
            return when {
                confirm != null -> ProfileState.AWAITING_CONFIRMATION
                active != null && pendingDisplay != null -> {
                    val display = probe?.display
                    when {
                        display == null || !display.ok -> ProfileState.ACTIVE_UNVERIFIED
                        active.matches(display) -> ProfileState.ACTIVE
                        else -> ProfileState.DRIFTED
                    }
                }
                applyBlockedReason == null -> ProfileState.READY
                else -> ProfileState.NOT_READY
            }
        }

    val gpuIdentity: GpuIdentity
        get() = GpuClassifier.classify(
            glRenderer = gl?.renderer,
            glVendor = gl?.vendor,
            vulkanDeviceName = vulkan?.primaryDevice?.name,
            vulkanVendorId = vulkan?.primaryDevice?.vendorId
        )

    /** Semua bukti dibaca dari state aplikasi; tidak ada yang diisi tangan. */
    val testEvidence: TestEvidence
        get() = TestEvidence(
            gpuFamily = gpuIdentity.family,
            glAvailable = gl?.available == true,
            vulkanAvailable = vulkan?.available == true,
            vulkanNativeProbe = vulkan?.nativeProbeUsed == true,
            nativeProbeExpected = BuildConfig.NATIVE_VULKAN_PROBE,
            shizukuInstalled = shizuku.status != ShizukuStatus.NOT_INSTALLED,
            shizukuConnected = shizuku.connected,
            serviceUid = userService.serviceUid,
            pixelMediumBlockedReason = ProfileResolver.resolve(
                GraphicsProfiles.forId(ProfileId.PIXEL_MEDIUM, customScale),
                capabilities, activeBackend, probe?.display, pendingDisplay != null
            ).blockedReason,
            probeResults = probe?.results?.map { it.command to it.ok }.orEmpty(),
            displayReadable = probe?.display?.ok == true,
            gameInstalled = installedGames.isNotEmpty(),
            gameModesCaptured = probe?.gameModes?.isNotEmpty() == true,
            history = history
        )

    val testResults: List<TestResult>
        get() = TestPlan.evaluate(testEvidence, manualVerdicts)

    /** Dihitung dari catatan di disk, jadi tetap benar setelah force-close atau reboot. */
    val recovery: RecoveryState
        get() = RecoveryPolicy.evaluate(
            hasPending = pendingDisplay != null,
            confirming = confirm != null,
            applying = applying,
            journalState = journalState,
            rebootedSinceConfirm = rebootedSinceConfirm,
            shizukuRunning = shizuku.running
        )
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val shizukuManager = ShizukuManager(app)
    private val serviceClient = ShizukuUserServiceClient()
    private val backupStore = BackupStore(app)
    private val profileStore = ProfileStore(app)
    private val journal = ChangeJournal(app)
    private val manualVerdictStore = ManualVerdictStore(app)
    private val displayController = DisplayOverrideController(backupStore)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val logEntries = Logger.entries

    private val glExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pixelrender-gl-probe")
    }
    private val glDispatcher = glExecutor.asCoroutineDispatcher()

    private var countdownJob: Job? = null

    init {
        // State tersimpan dimuat SEBELUM listener Shizuku dipasang, supaya
        // keputusan auto-bind melihat perubahan yang tertunda sejak awal.
        reconcileStoredState()
        refreshStoredState()

        shizukuManager.register()
        announceRecovery()
        viewModelScope.launch {
            shizukuManager.state.collect { s ->
                _state.update { it.copy(shizuku = s) }
                rebuildMatrix()
                maybeAutoBind(s)
            }
        }
        viewModelScope.launch {
            serviceClient.state.collect { s ->
                _state.update { it.copy(userService = s) }
                rebuildMatrix()
                if (s.bound) {
                    refreshDisplayState()
                    maybeAutoRevert()
                }
            }
        }
        runDetection()
    }

    // ------------------------------------------------------------ pemulihan saat start

    /** Merapikan catatan yang saling bertentangan, tanpa pernah menulis ke perangkat. */
    private fun reconcileStoredState() {
        val action = RecoveryPolicy.reconcile(
            hasPending = backupStore.pendingDisplay() != null,
            journalState = journal.state,
            hasActiveProfile = profileStore.active() != null
        )
        when (action) {
            ReconcileAction.CLEAR_STALE_RECORDS -> {
                Logger.w("Catatan profil tanpa target restore dibersihkan")
                profileStore.clearActive()
                journal.state = JournalState.NONE
                journal.previous = null
            }
            ReconcileAction.ADOPT_AS_CONFIRMED -> {
                journal.state = JournalState.CONFIRMED
                journal.acknowledgedBootCount = currentBootCount(getApplication())
                Logger.i("Perubahan dari versi sebelumnya dicatat sebagai sudah dikonfirmasi")
            }
            ReconcileAction.NOTHING -> Unit
        }
    }

    private fun announceRecovery() {
        // Status Shizuku dibaca langsung, bukan dari UiState yang mungkin belum diperbarui.
        val s = _state.value.copy(shizuku = shizukuManager.state.value)
        when (s.recovery) {
            RecoveryState.UNCONFIRMED -> Logger.w(
                "Ditemukan perubahan yang tidak pernah dikonfirmasi",
                "Akan dikembalikan otomatis begitu user service Shizuku terhubung"
            )
            RecoveryState.REBOOTED -> Logger.w(
                "Perangkat sudah reboot sejak profil diterapkan",
                "Override resolusi bertahan setelah reboot. Menunggu keputusan user."
            )
            RecoveryState.SHIZUKU_LOST -> Logger.w(
                "Ada perubahan aktif tetapi Shizuku tidak berjalan",
                "Target restore: ${s.pendingDisplay?.describe()}"
            )
            RecoveryState.NONE -> Unit
        }
    }

    /**
     * Service hanya di-bind otomatis kalau ada perubahan yang perlu
     * diverifikasi atau dipulihkan. Di luar itu, proses privileged tidak
     * dijalankan sampai user memintanya.
     */
    private fun maybeAutoBind(s: ShizukuState) {
        if (!s.connected) return
        if (backupStore.pendingDisplay() == null) return
        if (serviceClient.state.value.status != UserServiceStatus.NOT_BOUND) return
        Logger.i("Ada perubahan aktif, menghubungkan user service untuk verifikasi")
        serviceClient.bind(s)
    }

    /**
     * Kontrak konfirmasi: tidak dikonfirmasi berarti dikembalikan. Kontrak itu
     * tetap berlaku walaupun aplikasi sempat mati di tengah hitung mundur.
     */
    private fun maybeAutoRevert() {
        if (!RecoveryPolicy.shouldAutoRevert(_state.value.recovery)) return
        returnDisplay(
            OperationKind.AUTO_REVERT,
            "perubahan tidak pernah dikonfirmasi",
            toPrevious = true
        )
    }

    fun acknowledgeReboot() {
        journal.acknowledgedBootCount = currentBootCount(getApplication())
        refreshStoredState()
        Logger.ok("Profil dipertahankan setelah reboot")
    }

    // ------------------------------------------------------------ deteksi

    fun runDetection() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            Logger.i("=== Deteksi perangkat dimulai ===")

            val app = getApplication<Application>()

            val device = withContext(Dispatchers.Default) { DeviceInfoCollector.collect(app) }
            _state.update { it.copy(device = device) }

            val gl = withContext(glDispatcher) { OpenGLCapability.queryBlocking(app) }
            _state.update { it.copy(gl = gl) }

            val vk = withContext(Dispatchers.Default) { VulkanCapability.query(app) }
            _state.update { it.copy(vulkan = vk) }

            val games = withContext(Dispatchers.Default) { GameDetector.detect(app) }
            _state.update { it.copy(games = games) }
            ensureSelectedGame(games)

            val shizuku = shizukuManager.refresh()
            _state.update { it.copy(scanning = false, shizuku = shizuku) }
            rebuildMatrix()
            Logger.i("=== Deteksi perangkat selesai ===")
        }
    }

    private fun ensureSelectedGame(games: List<GameTarget>) {
        val current = profileStore.selectedGame
        val installed = games.filter { it.installed }
        if (current == null || installed.none { it.packageName == current }) {
            val pick = installed.firstOrNull()?.packageName
            profileStore.selectedGame = pick
            _state.update { it.copy(selectedGame = pick) }
        }
    }

    // ------------------------------------------------------------ Shizuku

    fun bindUserService() = serviceClient.bind(shizukuManager.refresh())

    fun unbindUserService() = serviceClient.unbind()

    fun runShizukuProbe() {
        if (!_state.value.userService.bound) {
            Logger.e("Probe dibatalkan", "User service belum terhubung")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(probing = true) }
            val games = _state.value.games
            val report = withContext(Dispatchers.IO) { ShizukuProbe.run(serviceClient, games) }
            saveBaseline(report)
            _state.update { it.copy(probing = false, probe = report) }
            refreshStoredState()
            rebuildMatrix()
        }
    }

    private fun saveBaseline(report: ProbeReport) {
        val display = report.display ?: return
        if (!display.ok || backupStore.pendingDisplay() != null) return
        backupStore.recordBaseline("display.size", "${display.baseWidth}x${display.baseHeight}")
        backupStore.recordBaseline("display.density", display.baseDensity.toString())
        backupStore.recordBaseline("display.physicalSize", "${display.initialWidth}x${display.initialHeight}")
        backupStore.recordBaseline("display.physicalDensity", display.initialDensity.toString())
    }

    // ------------------------------------------------------------ backend

    fun selectBackend(id: BackendId) {
        _state.update { it.copy(selectedBackend = id) }
        rebuildMatrix()
    }

    private fun backendContext(): BackendContext? {
        val s = _state.value
        return BackendContext(
            app = getApplication(),
            device = s.device ?: return null,
            gl = s.gl ?: return null,
            vulkan = s.vulkan ?: return null,
            shizuku = s.shizuku,
            userService = s.userService,
            probe = s.probe,
            privileged = if (s.userService.bound) serviceClient else null
        )
    }

    private fun rebuildMatrix() {
        val ctx = backendContext() ?: return
        val statuses = BackendRegistry.statuses(ctx)
        val caps = BackendRegistry.matrix(ctx)
        val active = BackendRegistry.resolve(_state.value.selectedBackend, ctx)
        _state.update {
            it.copy(
                capabilities = caps,
                backends = statuses,
                activeBackend = active.id,
                shortcuts = active.settingsShortcuts(ctx)
                    .ifEmpty { StandardAndroidBackend.settingsShortcuts(ctx) }
            )
        }
    }

    // ------------------------------------------------------------ profil

    fun selectProfile(id: ProfileId) {
        profileStore.selectedProfile = id
        _state.update { it.copy(selectedProfile = id) }
    }

    fun setCustomScale(scale: Float) {
        if (scale !in DisplayPlanner.SCALE_STEPS) return
        profileStore.customScale = scale
        profileStore.selectedProfile = ProfileId.CUSTOM
        _state.update { it.copy(customScale = scale, selectedProfile = ProfileId.CUSTOM) }
    }

    fun selectGame(packageName: String) {
        profileStore.selectedGame = packageName
        _state.update { it.copy(selectedGame = packageName) }
    }

    fun applyProfile() {
        val s = _state.value
        if (s.selectedProfile == ProfileId.DEFAULT) {
            restore()
            return
        }
        s.applyBlockedReason?.let {
            Logger.e("[ERROR] Profil ${s.profile.id.label} tidak bisa diterapkan", it)
            return
        }
        val resolved = s.resolvedProfile
        val plan = resolved.plan ?: return
        val ctx = backendContext() ?: return

        // Titik kembali untuk "Kembalikan": profil terkonfirmasi yang aktif sekarang.
        val previous = if (journal.state == JournalState.CONFIRMED) profileStore.active() else null
        logResolution(resolved)

        viewModelScope.launch {
            _state.update { it.copy(applying = true) }
            journal.beginApply(previous)

            val outcome = withContext(Dispatchers.IO) {
                displayController.apply(ctx, ShizukuBackend, plan, previous)
            }
            val label = resolved.profile.id.label

            when (outcome) {
                is Outcome.Done -> {
                    profileStore.setActive(
                        ActiveProfile(
                            id = resolved.profile.id,
                            width = plan.width,
                            height = plan.height,
                            density = plan.density,
                            appliedAt = System.currentTimeMillis()
                        )
                    )
                    journal.state = JournalState.AWAITING_CONFIRMATION
                    _state.update { it.copy(applying = false) }
                    refreshStoredState()
                    // Hitung mundur dimulai sebelum suspend apa pun, supaya
                    // tidak ada celah di mana perubahan ini terlihat "yatim".
                    startConfirmation("$label - ${plan.label}")
                    val after = refreshDisplayState()
                    record(OperationKind.APPLY, label, plan.label, after, true, outcome.description)
                    Logger.ok("Profile applied: $label", outcome.description)
                }
                is Outcome.Failed -> {
                    settle(outcome.landedOn, previous)
                    _state.update { it.copy(applying = false) }
                    refreshStoredState()
                    val after = refreshDisplayState()
                    val landing = landingNote(outcome.landedOn, previous)
                    record(OperationKind.APPLY, label, plan.label, after, false, "${outcome.reason}. $landing")
                    Logger.e("[ERROR] Parameter tidak bisa diubah pada perangkat ini", "${outcome.reason}. $landing")
                }
                Outcome.NothingToRestore -> _state.update { it.copy(applying = false) }
            }
        }
    }

    private fun logResolution(resolved: ResolvedProfile) {
        Logger.i(
            "=== Profil ${resolved.profile.id.label}: ${resolved.applyCount} dari " +
                    "${resolved.settings.size} bagian akan diterapkan ==="
        )
        resolved.settings.forEach { setting ->
            val line = "${setting.parameter.label} (${setting.intent}): ${setting.outcome.label}"
            when (setting.outcome) {
                SettingOutcome.WILL_APPLY -> Logger.ok(line, setting.detail)
                SettingOutcome.NO_API -> Logger.w(line, setting.detail)
                else -> Logger.i(line, setting.detail)
            }
        }
    }

    // ------------------------------------------------------------ konfirmasi & kembali

    private fun startConfirmation(description: String) {
        countdownJob?.cancel()
        _state.update { it.copy(confirm = ConfirmState(CONFIRM_SECONDS, description)) }
        countdownJob = viewModelScope.launch {
            for (seconds in CONFIRM_SECONDS downTo 1) {
                _state.update { it.copy(confirm = ConfirmState(seconds, description)) }
                delay(1_000)
            }
            _state.update { it.copy(confirm = null) }
            Logger.w("Tidak dikonfirmasi dalam $CONFIRM_SECONDS detik")
            returnDisplay(OperationKind.REVERT, "timeout konfirmasi", toPrevious = true)
        }
    }

    fun confirmKeep() {
        cancelCountdown()
        journal.state = JournalState.CONFIRMED
        journal.previous = null
        journal.acknowledgedBootCount = currentBootCount(getApplication())
        refreshStoredState()
        Logger.ok("Perubahan dipertahankan", "Bisa dikembalikan kapan saja lewat RESTORE DEFAULT")
    }

    /** Membatalkan apply terakhir: kembali ke profil sebelumnya kalau ada. */
    fun revertNow() {
        cancelCountdown()
        returnDisplay(OperationKind.REVERT, "dikembalikan user", toPrevious = true)
    }

    /** RESTORE DEFAULT: selalu ke kondisi sebelum PixelRender menulis. */
    fun restore() {
        cancelCountdown()
        if (backupStore.pendingDisplay() == null) {
            profileStore.clearActive()
            journal.state = JournalState.NONE
            journal.previous = null
            refreshStoredState()
            Logger.ok("Tidak ada yang perlu di-restore", "Perangkat sudah dalam kondisi default")
            return
        }
        returnDisplay(OperationKind.RESTORE, "RESTORE DEFAULT", toPrevious = false)
    }

    private fun returnDisplay(kind: OperationKind, reason: String, toPrevious: Boolean) {
        val ctx = backendContext()
        if (ctx?.privileged == null) {
            refreshStoredState()
            Logger.e(
                "${kind.label} tidak bisa jalan: user service belum terhubung",
                "Target restore tetap tersimpan. Jalankan Shizuku, atau: " +
                        "adb shell wm size reset && adb shell wm density reset"
            )
            return
        }
        val previous = if (toPrevious) journal.previous else null
        val originalDescription = backupStore.pendingDisplay()?.describe() ?: "kondisi asli"

        viewModelScope.launch {
            _state.update { it.copy(applying = true) }
            Logger.i("${kind.label} ($reason)")

            val outcome = withContext(Dispatchers.IO) {
                if (toPrevious) displayController.revert(ctx, ShizukuBackend, previous)
                else displayController.restore(ctx, ShizukuBackend)
            }
            val landing = when (outcome) {
                is Outcome.Done -> outcome.landedOn
                is Outcome.Failed -> outcome.landedOn
                Outcome.NothingToRestore -> Landing.ORIGINAL
            }
            settle(landing, previous)

            _state.update { it.copy(applying = false) }
            refreshStoredState()
            val after = refreshDisplayState()

            val expected = if (landing == Landing.PREVIOUS_PROFILE) previous?.target ?: "-"
            else originalDescription
            val verified = outcome is Outcome.Done || outcome is Outcome.NothingToRestore
            val note = when (outcome) {
                is Outcome.Done -> outcome.description
                is Outcome.Failed -> outcome.reason
                Outcome.NothingToRestore -> "Tidak ada perubahan tertunda"
            }
            record(kind, reason, expected, after, verified, note)
            if (!verified) Logger.e("[ERROR] ${kind.label} gagal", note)
        }
    }

    /**
     * Menyelaraskan catatan dengan tempat perangkat benar-benar berakhir.
     * Kalau tidak berhasil kembali ke mana pun (null), catatan sengaja
     * dibiarkan, supaya pemulihan bisa dicoba lagi saat Shizuku tersedia.
     */
    private fun settle(landing: Landing?, previous: ActiveProfile?) {
        when (landing) {
            Landing.ORIGINAL -> {
                profileStore.clearActive()
                journal.state = JournalState.NONE
                journal.previous = null
            }
            Landing.PREVIOUS_PROFILE -> {
                previous?.let { profileStore.setActive(it) }
                journal.state = JournalState.CONFIRMED
                journal.previous = null
            }
            Landing.NEW_TARGET, null -> Unit
        }
    }

    private fun landingNote(landing: Landing?, previous: ActiveProfile?): String = when (landing) {
        Landing.ORIGINAL -> "Perangkat sudah dikembalikan ke kondisi asli"
        Landing.PREVIOUS_PROFILE -> "Perangkat kembali ke ${previous?.id?.label ?: "profil sebelumnya"}"
        Landing.NEW_TARGET -> ""
        null -> "Perangkat BELUM berhasil dikembalikan; target restore tetap disimpan"
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(confirm = null) }
    }

    private fun record(
        kind: OperationKind,
        label: String,
        expected: String,
        after: DisplayState?,
        verified: Boolean,
        note: String
    ) {
        val actual = when {
            after == null -> "tidak terbaca"
            !after.ok -> "tidak terbaca: ${after.error}"
            else -> "${after.current} @${after.baseDensity}dpi" +
                    if (!after.anyOverride) " (bawaan panel)" else ""
        }
        journal.record(
            OperationRecord(kind, label, expected, actual, verified, note, System.currentTimeMillis())
        )
        _state.update { it.copy(history = journal.history()) }
    }

    fun clearHistory() {
        journal.clearHistory()
        _state.update { it.copy(history = emptyList()) }
    }

    private suspend fun refreshDisplayState(): DisplayState? {
        val fresh = withContext(Dispatchers.IO) { serviceClient.readDisplayState(0) } ?: return null
        _state.update { s -> s.copy(probe = (s.probe ?: ProbeReport()).copy(display = fresh)) }
        rebuildMatrix()
        return fresh
    }

    // ------------------------------------------------------------ umum

    fun clearStoredState() {
        backupStore.clearBaselines()
        refreshStoredState()
    }

    private fun refreshStoredState() {
        val boot = currentBootCount(getApplication())
        val journalState = journal.state
        val acknowledged = journal.acknowledgedBootCount
        _state.update {
            it.copy(
                baselines = backupStore.baselines(),
                pendingChanges = backupStore.pendingChanges(),
                pendingDisplay = backupStore.pendingDisplay(),
                selectedProfile = profileStore.selectedProfile,
                customScale = profileStore.customScale,
                activeProfile = profileStore.active(),
                selectedGame = profileStore.selectedGame,
                journalState = journalState,
                rebootedSinceConfirm = RecoveryPolicy.rebootedSinceConfirm(
                    journalState, boot, acknowledged
                ),
                history = journal.history(),
                manualVerdicts = manualVerdictStore.all()
            )
        }
    }

    // ------------------------------------------------------------ PHASE 7

    fun setManualVerdict(id: String, verdict: TestVerdict?) {
        manualVerdictStore.set(id, verdict)
        _state.update { it.copy(manualVerdicts = manualVerdictStore.all()) }
        Logger.i("Uji manual $id: ${verdict?.label ?: "direset"}")
    }

    fun buildDiagnosticReport(): String {
        val s = _state.value
        val summary = TestPlan.summary(s.testResults)
        Logger.i(
            "Laporan diagnostik dibuat untuk ${TestPlan.cell(s.testEvidence)}",
            summary.entries.joinToString(", ") { "${it.key.label} ${it.value}" }
        )
        return DiagnosticReport.build(s, Logger.entries.value)
    }

    fun requestShizukuPermission(activity: Activity?) = shizukuManager.requestPermission(activity)

    fun refreshShizuku() {
        shizukuManager.refresh()
    }

    fun clearLog() = Logger.clear()

    override fun onCleared() {
        countdownJob?.cancel()
        serviceClient.unbind()
        shizukuManager.unregister()
        glExecutor.shutdown()
        super.onCleared()
    }

    private companion object {
        const val CONFIRM_SECONDS = 15
    }
}
