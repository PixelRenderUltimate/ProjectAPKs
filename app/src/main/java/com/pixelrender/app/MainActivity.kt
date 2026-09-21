package com.pixelrender.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.ui.AboutScreen
import com.pixelrender.app.ui.AdvancedScreen
import com.pixelrender.app.ui.DeviceInfoScreen
import com.pixelrender.app.ui.HomeScreen
import com.pixelrender.app.ui.LogScreen
import com.pixelrender.app.ui.ProfilesScreen
import com.pixelrender.app.ui.ShizukuScreen
import com.pixelrender.app.ui.theme.PixelRenderTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    PROFILES("Profil", Icons.Filled.Star),
    DEVICE("Device", Icons.Filled.Phone),
    ADVANCED("Advanced", Icons.Filled.Settings),
    SHIZUKU("Shizuku", Icons.Filled.Lock)
}

/** Logs dan About dibuka dari top bar supaya bottom bar tidak sesak. */
private enum class Overlay { NONE, LOGS, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelRenderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PixelRenderRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixelRenderRoot() {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logs by viewModel.logEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Diminta tepat saat relevan: begitu perubahan dipertahankan dan akan
    // bertahan setelah reboot, bukan saat aplikasi pertama dibuka.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Logger.ok("Izin notifikasi diberikan", "PixelRender bisa mengingatkan setelah reboot")
        } else {
            Logger.w("Izin notifikasi ditolak", "Pengingat setelah reboot hanya muncul di dalam aplikasi")
        }
    }

    // Status Shizuku bisa berubah saat user berpindah ke aplikasi Shizuku
    // dan kembali, jadi kita cek ulang setiap kali resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshShizuku()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (overlay) {
                            Overlay.LOGS -> "Logs"
                            Overlay.ABOUT -> "About"
                            Overlay.NONE -> "PixelRender"
                        }
                    )
                },
                actions = {
                    IconButton(onClick = {
                        overlay = if (overlay == Overlay.LOGS) Overlay.NONE else Overlay.LOGS
                    }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logs")
                    }
                    IconButton(onClick = {
                        overlay = if (overlay == Overlay.ABOUT) Overlay.NONE else Overlay.ABOUT
                    }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = overlay == Overlay.NONE && tab == entry,
                        onClick = {
                            overlay = Overlay.NONE
                            tab = entry
                        },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) }
                    )
                }
            }
        }
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            when (overlay) {
                Overlay.ABOUT -> AboutScreen()
                Overlay.LOGS -> LogScreen(logs, onClear = viewModel::clearLog)
                Overlay.NONE -> when (tab) {
                    Tab.HOME -> HomeScreen(
                        state = state,
                        onSelectGame = viewModel::selectGame,
                        onOpenProfiles = { tab = Tab.PROFILES },
                        onApply = viewModel::applyProfile,
                        onRestore = viewModel::restore,
                        onRescan = viewModel::runDetection,
                        onRevertNow = viewModel::revertNow,
                        onAcknowledgeReboot = viewModel::acknowledgeReboot
                    )
                    Tab.PROFILES -> ProfilesScreen(
                        state = state,
                        onSelectProfile = viewModel::selectProfile,
                        onCustomScale = viewModel::setCustomScale,
                        onApply = viewModel::applyProfile
                    )
                    Tab.DEVICE -> DeviceInfoScreen(
                        state = state,
                        onManualVerdict = viewModel::setManualVerdict,
                        buildReport = viewModel::buildDiagnosticReport
                    )
                    Tab.ADVANCED -> AdvancedScreen(state, onSelectBackend = viewModel::selectBackend)
                    Tab.SHIZUKU -> ShizukuScreen(
                        state = state,
                        onGrant = { viewModel.requestShizukuPermission(activity) },
                        onRefresh = viewModel::refreshShizuku,
                        onBind = viewModel::bindUserService,
                        onUnbind = viewModel::unbindUserService,
                        onProbe = viewModel::runShizukuProbe,
                        onRestore = viewModel::restore,
                        onClearState = viewModel::clearStoredState,
                        onClearHistory = viewModel::clearHistory
                    )
                }
            }
        }
    }

    // Tidak bisa ditutup dengan tap di luar: pilihannya hanya pertahankan
    // atau kembalikan, dan diam berarti kembalikan.
    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Pertahankan resolusi ini?") },
            text = {
                Text(
                    "${confirm.description}\n\nKembali otomatis dalam " +
                            "${confirm.secondsLeft} detik."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.confirmKeep()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Pertahankan") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::revertNow) { Text("Kembalikan") }
            }
        )
    }
}
