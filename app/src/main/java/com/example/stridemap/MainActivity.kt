package com.example.stridemap

import android.Manifest
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.stridemap.core.MapOverlayText
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackOrdering
import com.example.stridemap.core.TrackSortField
import com.example.stridemap.core.TrackState
import com.example.stridemap.location.LocationRequestSpec
import com.example.stridemap.session.SetupBlocker
import com.example.stridemap.storage.DirectTrackRecoveryLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val StrideMapDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FB996),
    onPrimary = Color(0xFF102116),
    primaryContainer = Color(0xFF254632),
    onPrimaryContainer = Color(0xFFD5E8D5),
    secondary = Color(0xFFAAB8A8),
    onSecondary = Color(0xFF182016),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE6ECE6),
    surface = Color(0xFF151A17),
    onSurface = Color(0xFFE6ECE6),
    surfaceVariant = Color(0xFF222B25),
    onSurfaceVariant = Color(0xFFC2CBC1),
    outline = Color(0xFF6F7D70),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val StrideMapLightColorScheme = lightColorScheme(
    primary = Color(0xFF426B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4D8C1),
    onPrimaryContainer = Color(0xFF0E2013),
    secondary = Color(0xFF59685A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8F2),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFE4EAE0),
    onSurfaceVariant = Color(0xFF424A42),
    outline = Color(0xFF737C72),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsm()
        StrideMapRepository.initialize(applicationContext)
        setContent { StrideMapApp() }
    }

    override fun onResume() {
        super.onResume()
        val hadAllFilesRecoveryAccess = StrideMapRepository.state.hasAllFilesRecoveryAccess
        StrideMapRepository.refreshSetup()
        if (!hadAllFilesRecoveryAccess && StrideMapRepository.state.hasAllFilesRecoveryAccess) {
            StrideMapRepository.scanTracks(false)
        }
    }

    private fun configureOsm() {
        Configuration.getInstance().userAgentValue = "com.example.stridemap"
        Configuration.getInstance().osmdroidBasePath = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrideMapApp() {
    var tabBackStack by rememberSaveable { mutableStateOf(listOf(AppTab.Capture.name)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appState = StrideMapRepository.state
    val selectedTab = AppTab.valueOf(tabBackStack.lastOrNull() ?: AppTab.Capture.name)

    fun navigateToTab(tab: AppTab) {
        if (selectedTab != tab) tabBackStack = tabBackStack + tab.name
    }

    fun navigateBackWithinApp() {
        when {
            showSettings && settingsPage != SettingsPage.Root -> settingsPage = SettingsPage.Root
            showSettings -> {
                settingsPage = SettingsPage.Root
                showSettings = false
            }
            tabBackStack.size > 1 -> tabBackStack = tabBackStack.dropLast(1)
        }
    }

    BackHandler(enabled = showSettings || tabBackStack.size > 1) {
        navigateBackWithinApp()
    }

    LaunchedEffect(appState.transientMessage) {
        appState.transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            StrideMapRepository.clearTransientMessage()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.List) StrideMapRepository.scanTracks(false)
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) StrideMapDarkColorScheme else StrideMapLightColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showSettings) {
                SettingsScreen(
                    page = settingsPage,
                    onPageChange = { settingsPage = it },
                    onBack = ::navigateBackWithinApp,
                )
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { navigateToTab(tab) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    },
                ) { padding ->
                    when (selectedTab) {
                        AppTab.Capture -> CaptureScreen(
                            padding,
                            onOpenSettings = { settingsPage = SettingsPage.Root; showSettings = true },
                            onCaptureStarted = { if (appState.settings.afterStartDestination == AfterStartDestination.Map) navigateToTab(AppTab.Map) },
                        )
                        AppTab.List -> TrackListScreen(padding, navigate = ::navigateToTab)
                        AppTab.Map -> TrackMapScreen(padding, onNavigate = ::navigateToTab)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(SettingsGearIcon, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(page: SettingsPage, onPageChange: (SettingsPage) -> Unit, onBack: () -> Unit) {
    when (page) {
        SettingsPage.Root -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Close settings") } },
                )
            },
        ) { padding ->
            SettingsRootPage(
                padding,
                onCaptureDefaults = { onPageChange(SettingsPage.CaptureDefaults) },
                onTracksDefaults = { onPageChange(SettingsPage.TracksDefaults) },
                onMapDefaults = { onPageChange(SettingsPage.MapDefaults) },
                onStorageInfo = { onPageChange(SettingsPage.StorageInfo) },
            )
        }
        SettingsPage.CaptureDefaults -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Capture defaults") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Back") } },
                )
            },
        ) { padding ->
            CaptureDefaultsSettingsPage(padding, onGpsPolling = { onPageChange(SettingsPage.GpsPolling) })
        }
        SettingsPage.GpsPolling -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GPS polling interval") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Back") } },
                )
            },
        ) { padding ->
            GpsPollingSettingsPage(padding)
        }
        SettingsPage.TracksDefaults -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tracks defaults") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Back") } },
                )
            },
        ) { padding ->
            TracksDefaultsSettingsPage(padding)
        }
        SettingsPage.MapDefaults -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Map defaults") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Back") } },
                )
            },
        ) { padding ->
            MapDefaultsSettingsPage(padding)
        }
        SettingsPage.StorageInfo -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Storage / App info") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(BackArrowIcon, contentDescription = "Back") } },
                )
            },
        ) { padding ->
            StorageInfoSettingsPage(padding)
        }
    }
}

@Composable
private fun SettingsRootPage(
    padding: PaddingValues,
    onCaptureDefaults: () -> Unit,
    onTracksDefaults: () -> Unit,
    onMapDefaults: () -> Unit,
    onStorageInfo: () -> Unit,
) {
    val appState = StrideMapRepository.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Defaults", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsNavRow("Capture defaults", "${appState.settings.defaultMovementType.label}, ${appState.settings.afterStartDestination.label}", onCaptureDefaults)
            SettingsNavRow("Tracks defaults", "${appState.settings.defaultTrackMovementFilter?.label ?: "All"}, ${appState.settings.defaultTrackSortField.displayLabel()}, ${sortDirectionLabel(appState.settings.defaultTrackSortField, appState.settings.defaultTrackSortAscending)}", onTracksDefaults)
            SettingsNavRow("Map defaults", "Follow live ${onOff(appState.settings.followLiveByDefault)}, point dots ${onOff(appState.settings.showSavedPointDots)}", onMapDefaults)
        }
        Text("Storage / App", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsNavRow("Storage / App info", appState.trackFolder?.displayPath ?: "Documents/StrideMap/Tracks", onStorageInfo)
        }
        Text(
            "These settings affect future captures only. Saved track points still use the fixed rule: first valid point, then at least 1 second and 10 meters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsNavRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(SettingsGearIcon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
    )
}

@Composable
private fun CaptureDefaultsSettingsPage(padding: PaddingValues, onGpsPolling: () -> Unit) {
    val appState = StrideMapRepository.state
    var movementDialog by remember { mutableStateOf(false) }
    var afterStartDialog by remember { mutableStateOf(false) }

    if (movementDialog) MovementTypeDialog(appState.settings.defaultMovementType, { movementDialog = false }) {
        StrideMapRepository.setDefaultMovementType(it)
        movementDialog = false
    }
    if (afterStartDialog) AfterStartDialog(appState.settings.afterStartDestination, { afterStartDialog = false }) {
        StrideMapRepository.setAfterStartDestination(it)
        afterStartDialog = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsNavRow("Default movement type", appState.settings.defaultMovementType.label) { movementDialog = true }
            SettingsNavRow("GPS polling interval", "Provider hints • Walk ${formatPollingInterval(appState.settings.gpsPollingIntervalMillis(MovementType.Walk))}", onGpsPolling)
            SettingsNavRow("After starting recording", appState.settings.afterStartDestination.label) { afterStartDialog = true }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Default note / template") },
                supportingContent = { Text("Used to prefill the next idle capture") },
            )
            OutlinedTextField(
                value = appState.settings.defaultCaptureNote,
                onValueChange = StrideMapRepository::setDefaultCaptureNote,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                label = { Text("Default note") },
                placeholder = { Text("Evening walk") },
                maxLines = 2,
            )
        }
        Text("Capture defaults apply to future capture setup and do not alter an active recording.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TracksDefaultsSettingsPage(padding: PaddingValues) {
    val appState = StrideMapRepository.state
    var filterDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var orderDialog by remember { mutableStateOf(false) }

    if (filterDialog) MovementFilterDialog(appState.settings.defaultTrackMovementFilter, { filterDialog = false }) {
        StrideMapRepository.setDefaultTrackMovementFilter(it)
        filterDialog = false
    }
    if (sortDialog) TrackSortDialog(appState.settings.defaultTrackSortField, { sortDialog = false }) {
        StrideMapRepository.setDefaultTrackSortField(it)
        sortDialog = false
    }
    if (orderDialog) TrackOrderDialog(appState.settings.defaultTrackSortField, appState.settings.defaultTrackSortAscending, { orderDialog = false }) {
        StrideMapRepository.setDefaultTrackSortAscending(it)
        orderDialog = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsNavRow("Default movement filter", appState.settings.defaultTrackMovementFilter?.label ?: "All") { filterDialog = true }
            SettingsNavRow("Default sort", appState.settings.defaultTrackSortField.displayLabel()) { sortDialog = true }
            SettingsNavRow("Default order", sortDirectionLabel(appState.settings.defaultTrackSortField, appState.settings.defaultTrackSortAscending)) { orderDialog = true }
        }
        Text("These defaults seed the Tracks controls when the screen is freshly opened; changing controls on the Tracks screen remains local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapDefaultsSettingsPage(padding: PaddingValues) {
    val appState = StrideMapRepository.state
    var widthDialog by remember { mutableStateOf(false) }
    if (widthDialog) RouteLineWidthDialog(appState.settings.routeLineWidth, { widthDialog = false }) {
        StrideMapRepository.setRouteLineWidth(it)
        widthDialog = false
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow("Follow live track by default", "Initial live-map behavior", appState.settings.followLiveByDefault, StrideMapRepository::setFollowLiveByDefault)
            SettingsToggleRow("Show saved point dots", "Intermediate saved-point markers", appState.settings.showSavedPointDots, StrideMapRepository::setShowSavedPointDots)
            SettingsNavRow("Route line thickness", "${appState.settings.routeLineWidth.roundToInt()} px") { widthDialog = true }
        }
    }
}

@Composable
private fun StorageInfoSettingsPage(padding: PaddingValues) {
    val appState = StrideMapRepository.state
    val context = LocalContext.current
    val openAllFilesAccessSettings = {
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { context.startActivity(appSettingsIntent) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(headlineContent = { Text("Track folder path") }, supportingContent = { Text(appState.trackFolder?.displayPath ?: "Documents/StrideMap/Tracks") })
            ListItem(
                modifier = Modifier.clickable { StrideMapRepository.scanTracks(false) },
                headlineContent = { Text("Refresh / rescan tracks") },
                supportingContent = { Text(if (appState.isScanning) "Scanning…" else "Read GPX files from the track and recovery folders") },
                trailingContent = { Icon(RefreshTracksIcon, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable { openAllFilesAccessSettings() },
                headlineContent = { Text("Recover existing tracks") },
                supportingContent = {
                    Text(
                        if (appState.hasAllFilesRecoveryAccess) {
                            "Direct scan enabled: ${DirectTrackRecoveryLocation.DisplayPath}"
                        } else {
                            "Grant All files access to scan ${DirectTrackRecoveryLocation.DisplayPath}"
                        },
                    )
                },
                trailingContent = { Icon(AppTab.List.icon, contentDescription = null) },
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(headlineContent = { Text("App package") }, supportingContent = { Text(context.packageName) })
            ListItem(headlineContent = { Text("Debug info") }, supportingContent = { Text("Tracks: ${appState.displayEntries.size} • Folder ready: ${appState.trackFolder?.isWritable == true}") })
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Text(onOff(checked)) },
    )
}

@Composable
private fun MovementTypeDialog(current: MovementType, onDismiss: () -> Unit, onSelected: (MovementType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default movement type") },
        text = { Column { MovementType.entries.forEach { type -> TextButton(onClick = { onSelected(type) }, modifier = Modifier.fillMaxWidth()) { Text(if (type == current) "${type.label} current" else type.label) } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AfterStartDialog(current: AfterStartDestination, onDismiss: () -> Unit, onSelected: (AfterStartDestination) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("After starting recording") },
        text = { Column { AfterStartDestination.entries.forEach { destination -> TextButton(onClick = { onSelected(destination) }, modifier = Modifier.fillMaxWidth()) { Text(if (destination == current) "${destination.label} current" else destination.label) } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MovementFilterDialog(current: MovementType?, onDismiss: () -> Unit, onSelected: (MovementType?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default movement filter") },
        text = {
            Column {
                TextButton(onClick = { onSelected(null) }, modifier = Modifier.fillMaxWidth()) { Text(if (current == null) "All current" else "All") }
                MovementType.entries.forEach { type -> TextButton(onClick = { onSelected(type) }, modifier = Modifier.fillMaxWidth()) { Text(if (type == current) "${type.label} current" else type.label) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TrackSortDialog(current: TrackSortField, onDismiss: () -> Unit, onSelected: (TrackSortField) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default sort") },
        text = { Column { TrackSortField.entries.forEach { field -> TextButton(onClick = { onSelected(field) }, modifier = Modifier.fillMaxWidth()) { Text(if (field == current) "${field.displayLabel()} current" else field.displayLabel()) } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TrackOrderDialog(field: TrackSortField, currentAscending: Boolean, onDismiss: () -> Unit, onSelected: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default order") },
        text = {
            Column {
                listOf(false, true).forEach { ascending ->
                    TextButton(onClick = { onSelected(ascending) }, modifier = Modifier.fillMaxWidth()) { Text(if (ascending == currentAscending) "${sortDirectionLabel(field, ascending)} current" else sortDirectionLabel(field, ascending)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RouteLineWidthDialog(current: Float, onDismiss: () -> Unit, onSelected: (Float) -> Unit) {
    val options = listOf(4f, 6f, 8f, 12f, 16f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route line thickness") },
        text = { Column { options.forEach { width -> TextButton(onClick = { onSelected(width) }, modifier = Modifier.fillMaxWidth()) { Text(if (width == current) "${width.roundToInt()} px current" else "${width.roundToInt()} px") } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GpsPollingSettingsPage(padding: PaddingValues) {
    val appState = StrideMapRepository.state
    var editingType by remember { mutableStateOf<MovementType?>(null) }

    editingType?.let { type ->
        GpsPollingIntervalDialog(
            type = type,
            currentMillis = appState.settings.gpsPollingIntervalMillis(type),
            onDismiss = { editingType = null },
            onSelected = { millis ->
                StrideMapRepository.setGpsPollingInterval(type, millis)
                editingType = null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Choose how often Android should try to provide GPS updates for each movement type. This is only a provider request hint for future captures, not the saved point interval.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            MovementType.entries.forEach { type ->
                ListItem(
                    modifier = Modifier.clickable { editingType = type },
                    leadingContent = { Icon(movementIcon(type), contentDescription = null, tint = movementTypeColor(type)) },
                    headlineContent = { Text(type.label) },
                    supportingContent = { Text("GPS update interval") },
                    trailingContent = { Text(formatPollingInterval(appState.settings.gpsPollingIntervalMillis(type))) },
                )
            }
        }
        Text(
            "Saved points remain distance-targeted: first valid point, then ≥1 s and ≥10 m from the last saved point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GpsPollingIntervalDialog(type: MovementType, currentMillis: Long, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    val options = remember(type, currentMillis) {
        (listOf(1_000L, 2_000L, 3_000L, 5_000L, 9_000L, 15_000L, 30_000L, 60_000L, currentMillis, LocationRequestSpec.defaultIntervalMillisForMovement(type)))
            .map(LocationRequestSpec::clampIntervalMillis)
            .distinct()
            .sorted()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${type.label} GPS update interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { millis ->
                    TextButton(onClick = { onSelected(millis) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (millis == currentMillis) "${formatPollingInterval(millis)} current" else formatPollingInterval(millis))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class SettingsPage { Root, CaptureDefaults, GpsPolling, TracksDefaults, MapDefaults, StorageInfo }

@Composable
private fun CaptureScreen(padding: PaddingValues, onOpenSettings: () -> Unit, onCaptureStarted: () -> Unit) {
    val context = LocalContext.current
    val appState = StrideMapRepository.state
    var showStopDialog by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        StrideMapRepository.refreshSetup()
    }

    if (showStopDialog) {
        StopCaptureDialog(onDismiss = { showStopDialog = false }) {
            showStopDialog = false
            StrideMapRepository.requestStopService()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Start a private route record for a walk, ride, run, or trip.",
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionButton(onClick = onOpenSettings)
        }
        OutlinedTextField(
            value = appState.captureMessage,
            onValueChange = StrideMapRepository::setMessage,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            placeholder = { Text("Evening walk") },
            supportingText = { Text("Optional private label") },
            maxLines = 2,
            enabled = appState.liveTrack == null,
        )
        MovementSelector(appState.movementType, enabled = appState.liveTrack == null)
        if (appState.liveTrack == null) {
            Button(
                onClick = {
                    if (StrideMapRepository.startCapture()) onCaptureStarted()
                },
                enabled = appState.readiness.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(StartControlIcon, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Start recording") }
        } else {
            Button(onClick = { showStopDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(StopControlIcon, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Stop recording") }
        }
        appState.liveTrack?.let { LiveStats(it, appState.lastAccuracyMeters) }
        if (appState.liveTrack == null) {
            SetupChecklist(
                appState = appState,
                onGrantLocation = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
                },
                onBackgroundSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
                onLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementSelector(selected: MovementType?, enabled: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Movement type", style = MaterialTheme.typography.titleMedium)
            Text("Walk is selected by default. Change it before recording if needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MovementType.entries.forEach { type ->
                    val movementColor = movementTypeColor(type)
                    val contentColor = movementTypeOnColor(type)
                    FilterChip(
                        selected = selected == type,
                        onClick = { if (enabled) StrideMapRepository.setMovementType(type) },
                        label = { Text(type.label) },
                        leadingIcon = { Icon(movementIcon(type), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            iconColor = movementColor,
                            selectedContainerColor = movementColor,
                            selectedLabelColor = contentColor,
                            selectedLeadingIconColor = contentColor,
                        ),
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupChecklist(
    appState: AppState,
    onGrantLocation: () -> Unit,
    onBackgroundSettings: () -> Unit,
    onLocationSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (appState.readiness.canStart) {
                Text("Ready to capture", style = MaterialTheme.typography.titleMedium)
                Text("Precise location, background access, and saving are set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("Before you start", style = MaterialTheme.typography.titleMedium)
            ChecklistRow(
                "Movement type",
                if (SetupBlocker.MovementTypeMissing in appState.readiness.blockers) "Needs action: choose walk, run, bike, car, or train."
                else "Ready: ${appState.movementType?.label ?: "Walk"} selected. Walk is the default and can be changed before recording.",
            )
            val approximateOnly = SetupBlocker.ApproximateOnlyLocation in appState.readiness.blockers
            val preciseMissing = SetupBlocker.PreciseLocationMissing in appState.readiness.blockers
            ChecklistRow(
                "Precise location",
                when {
                    approximateOnly -> "Needs action: location is approximate only. Allow precise location for clean route capture."
                    preciseMissing -> "Needs action: allow precise location for clean route capture."
                    else -> "Ready: precise location granted."
                },
                if (approximateOnly || preciseMissing) "Grant" else null,
                onGrantLocation,
            )
            ChecklistRow("Background location", if (SetupBlocker.BackgroundLocationMissing in appState.readiness.blockers) "Needs action: allow all-the-time location so recording continues with screen off." else "Ready: background location granted.", if (SetupBlocker.BackgroundLocationMissing in appState.readiness.blockers) "Open settings" else null, onBackgroundSettings)
            ChecklistRow("Notifications", if (SetupBlocker.NotificationPermissionMissing in appState.readiness.blockers) "Needs action: allow notifications so recording can stay visible." else "Ready: notifications allowed.", if (SetupBlocker.NotificationPermissionMissing in appState.readiness.blockers) "Grant" else null, onGrantLocation)
            ChecklistRow("Location services", if (SetupBlocker.DeviceLocationDisabled in appState.readiness.blockers) "Needs action: turn on device location services." else "Ready: device location services enabled.", if (SetupBlocker.DeviceLocationDisabled in appState.readiness.blockers) "Open" else null, onLocationSettings)
            ChecklistRow("Private app storage", if (SetupBlocker.AppDirectoriesUnavailable in appState.readiness.blockers) "Needs action: Android could not prepare private recovery storage." else "Ready: private recovery storage is available.")
            ChecklistRow(
                "Track export folder",
                if (SetupBlocker.StorageFolderUnavailable in appState.readiness.blockers) "Needs action: Android Documents storage is unavailable." else "Ready: tracks can be saved to ${appState.trackFolder?.displayPath ?: "Documents/StrideMap/Tracks"}.",
            )
            ChecklistRow("Background recording", "Ready when Android allows user-started recording from this screen.")
        }
    }
}

@Composable
private fun ChecklistRow(title: String, status: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (status.startsWith("Ready")) "✓" else "!", modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && action != null) TextButton(onClick = action) { Text(actionLabel) }
    }
}

@Composable
private fun LiveStats(track: Track, lastAccuracyMeters: Double?) {
    val elapsedSeconds = rememberLiveElapsedSeconds(track)
    val movementColor = movementTypeColor(track.movementType)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("● Recording ${track.movementType.label}", style = MaterialTheme.typography.titleMedium, color = movementColor)
            if (track.points.isEmpty()) Text("Waiting for first GPS point…")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDistance(track.distanceMeters))
                Text(formatDuration(elapsedSeconds))
                Text(lastAccuracyMeters?.let { "±${it.roundToInt()} m" } ?: "Accuracy --")
                Text("${track.points.size} points")
            }
            if ((lastAccuracyMeters ?: 0.0) > 25.0) {
                AssistChip(onClick = {}, label = { Text("Low GPS accuracy: ±${lastAccuracyMeters!!.roundToInt()} m. Point will still be saved with metadata.") })
            }
            Text("Saving to: ${track.fileName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StopCaptureDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val track = StrideMapRepository.state.liveTrack ?: return
    val empty = track.points.isEmpty()
    val elapsedSeconds = rememberLiveElapsedSeconds(track)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (empty) "Discard empty capture?" else "Stop capture?") },
        text = {
            Text(
                if (empty) "No GPS points have been saved yet. Stopping now will discard this capture."
                else "${formatDistance(track.distanceMeters)} • ${formatDuration(elapsedSeconds)} • ${track.points.size} points\n${track.fileName}",
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep recording") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (empty) "Discard" else "Stop and save") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackListScreen(padding: PaddingValues, navigate: (AppTab) -> Unit) {
    val appState = StrideMapRepository.state
    var filter by rememberSaveable { mutableStateOf(appState.settings.defaultTrackMovementFilter) }
    var sortField by rememberSaveable { mutableStateOf(appState.settings.defaultTrackSortField) }
    var ascending by rememberSaveable { mutableStateOf(appState.settings.defaultTrackSortAscending) }
    var movementMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var orderMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val displayEntries = appState.displayEntries
    val valid = TrackOrdering.sort(displayEntries.filterIsInstance<ParsedTrackEntry.Valid>().map { it.track }, filter, sortField, ascending)
    val malformed = displayEntries.filterIsInstance<ParsedTrackEntry.Malformed>()
    var malformedDetails by remember { mutableStateOf<ParsedTrackEntry.Malformed?>(null) }

    malformedDetails?.let { entry ->
        AlertDialog(
            onDismissRequest = { malformedDetails = null },
            title = { Text("Could not read GPX") },
            text = { Text("${entry.error.fileName}\n${entry.error.safeSummary}") },
            confirmButton = { TextButton(onClick = { malformedDetails = null }) { Text("OK") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (appState.isScanning) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.width(24.dp).height(24.dp)); Spacer(Modifier.width(8.dp)); Text("Loading tracks…") }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { movementMenuExpanded = true }) {
                        filter?.let { Icon(movementIcon(it), contentDescription = null, tint = movementTypeColor(it), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                        Text("Movement: ${filter?.label ?: "All"}")
                        Spacer(Modifier.width(4.dp))
                        Icon(DropdownIcon, contentDescription = null)
                    }
                    DropdownMenu(expanded = movementMenuExpanded, onDismissRequest = { movementMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("All") }, onClick = { filter = null; movementMenuExpanded = false })
                        MovementType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                leadingIcon = { Icon(movementIcon(type), contentDescription = null, tint = movementTypeColor(type)) },
                                onClick = { filter = type; movementMenuExpanded = false },
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { sortMenuExpanded = true }) { Text("Sort: ${sortField.displayLabel()}"); Spacer(Modifier.width(4.dp)); Icon(DropdownIcon, contentDescription = null) }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        TrackSortField.entries.forEach { field -> DropdownMenuItem(text = { Text(field.displayLabel()) }, onClick = { sortField = field; sortMenuExpanded = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { orderMenuExpanded = true }) { Text(sortDirectionLabel(sortField, ascending)); Spacer(Modifier.width(4.dp)); Icon(DropdownIcon, contentDescription = null) }
                    DropdownMenu(expanded = orderMenuExpanded, onDismissRequest = { orderMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(sortDirectionLabel(sortField, false)) }, onClick = { ascending = false; orderMenuExpanded = false })
                        DropdownMenuItem(text = { Text(sortDirectionLabel(sortField, true)) }, onClick = { ascending = true; orderMenuExpanded = false })
                    }
                }
            }
            if (valid.isEmpty() && malformed.isEmpty() && !appState.isScanning) {
                EmptyListState { navigate(AppTab.Capture) }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(valid, key = { it.id }) { track -> TrackRow(track, selected = appState.selectedTrack?.id == track.id) { StrideMapRepository.selectTrack(track); navigate(AppTab.Map) } }
                    items(malformed, key = { it.error.fileName }) { entry -> MalformedRow(entry.error.fileName, entry.error.safeSummary) { malformedDetails = entry } }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) {
    val displayDurationSeconds = rememberDisplayDurationSeconds(track)
    val movementColor = movementTypeColor(track.movementType)
    val movementContentColor = movementTypeOnColor(track.movementType)
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        track.state == TrackState.Live -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        ListItem(
            headlineContent = { Text(track.message.ifBlank { "${track.movementType.label} capture" }) },
            supportingContent = { Text("${formatInstant(track.createdAt)} • ${formatDuration(displayDurationSeconds)} • ${track.points.size} points${if (track.state == TrackState.Interrupted) " • Capture ended unexpectedly" else ""}") },
            leadingContent = {
                AssistChip(
                    onClick = {},
                    label = { Text(track.movementType.label) },
                    leadingIcon = { Icon(movementIcon(track.movementType), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = movementColor,
                        labelColor = movementContentColor,
                        leadingIconContentColor = movementContentColor,
                    ),
                )
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDistance(track.distanceMeters), style = MaterialTheme.typography.titleMedium)
                    if (track.state != TrackState.Stopped) AssistChip(onClick = {}, label = { Text(track.state.serialized.replaceFirstChar { it.uppercase() }) })
                }
            },
            overlineContent = if (selected || track.state == TrackState.Live) ({ Text(listOfNotNull(if (selected) "Selected" else null, if (track.state == TrackState.Live) "Live" else null).joinToString(" • ")) }) else null,
        )
    }
}

@Composable
private fun MalformedRow(fileName: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(headlineContent = { Text("Could not read $fileName") }, supportingContent = { Text(summary) }, leadingContent = { Text("!") })
    }
}

@Composable
private fun EmptyListState(onCapture: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No tracks yet", style = MaterialTheme.typography.headlineSmall)
        Text("Start a capture to create your first GPX track.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCapture) { Text("Go to Capture") }
    }
}

@Composable
private fun TrackMapScreen(padding: PaddingValues, onNavigate: (AppTab) -> Unit) {
    val appState = StrideMapRepository.state
    val track = appState.displayTrack
    var followLive by rememberSaveable { mutableStateOf(appState.settings.followLiveByDefault) }
    if (track == null) {
        Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No track selected", style = MaterialTheme.typography.headlineSmall)
            Text("Start a capture or choose a track from Tracks.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onNavigate(AppTab.Capture) }) { Text("Start capture") }; OutlinedButton(onClick = { onNavigate(AppTab.List) }) { Text("Open Tracks") } }
        }
        return
    }
    if (track.state == TrackState.Live && track.points.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Live capture waiting", style = MaterialTheme.typography.headlineSmall)
            Text("Recording has started. The map will appear after the first GPS point is saved.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onNavigate(AppTab.Capture) }) { Text("Open capture") }
        }
        return
    }
    if (track.points.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Track has no points", style = MaterialTheme.typography.headlineSmall)
            Text("This GPX has metadata but no saved route points to show on the map.")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onNavigate(AppTab.List) }) { Text("Open Tracks") }
        }
        return
    }
    Box(Modifier.fillMaxSize().padding(padding)) {
        OsmRouteMap(
            track = track,
            followLive = followLive,
            showSavedPointDots = appState.settings.showSavedPointDots,
            routeLineWidth = appState.settings.routeLineWidth,
            onGesture = { followLive = false },
        )
        TrackInfoCard(
            track,
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )
        if (appState.liveTrack != null && appState.selectedTrack != null) {
            ExtendedFloatingActionButton(onClick = { StrideMapRepository.showLiveTrack(); followLive = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), text = { Text("Show live") }, icon = { Icon(CaptureTabIcon, contentDescription = null) })
        } else if (track.state == TrackState.Live && !followLive) {
            ExtendedFloatingActionButton(onClick = { followLive = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), text = { Text("Follow live location") }, icon = { Icon(MapTabIcon, contentDescription = null) })
        }
    }
}

@Composable
private fun OsmRouteMap(track: Track, followLive: Boolean, showSavedPointDots: Boolean, routeLineWidth: Float, onGesture: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routeColor = movementTypeColor(track.movementType).toArgb()
    val routeCasingColor = Color(0xCC101412).toArgb()
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            minZoomLevel = 3.0
        }
    }
    var lastViewportKey by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDetach() }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
        view.setOnTouchListener { _, _ -> onGesture(); false }
        val viewportKey = routeViewportKey(track, followLive)
        val shouldUpdateViewport = viewportKey != lastViewportKey
        renderTrack(view, track, followLive, shouldUpdateViewport, routeColor, routeCasingColor, showSavedPointDots, routeLineWidth)
        if (shouldUpdateViewport && view.width > 0 && view.height > 0) lastViewportKey = viewportKey
    })
}

private fun renderTrack(mapView: MapView, track: Track, followLive: Boolean, updateViewport: Boolean, routeColor: Int, routeCasingColor: Int, showSavedPointDots: Boolean, routeLineWidth: Float) {
    mapView.overlays.clear()
    val points = track.points.map { GeoPoint(it.latitude, it.longitude) }
    if (points.size > 1) {
        mapView.overlays.add(Polyline().apply { setPoints(points); outlinePaint.strokeWidth = routeLineWidth + 4f; outlinePaint.color = routeCasingColor })
        mapView.overlays.add(Polyline().apply { setPoints(points); outlinePaint.strokeWidth = routeLineWidth; outlinePaint.color = routeColor })
    }
    if (points.size == 1) {
        val label = if (track.state == TrackState.Live) "Latest live point" else "Start"
        mapView.overlays.add(marker(mapView, points.single(), label, "Only saved point", MarkerKind.Latest, routeColor))
    } else if (points.size > 1) {
        if (showSavedPointDots) {
            points.drop(1).dropLast(1).forEachIndexed { index, point ->
                mapView.overlays.add(marker(mapView, point, "Saved point ${index + 2}", "Intermediate saved point", MarkerKind.SavedPoint, routeColor))
            }
        }
        mapView.overlays.add(marker(mapView, points.first(), "Start", "First saved point", MarkerKind.Start, routeColor))
        mapView.overlays.add(
            marker(
                mapView,
                points.last(),
                if (track.state == TrackState.Live) "Latest live point" else "End",
                if (track.state == TrackState.Live) "Most recent saved point" else "Final saved point",
                if (track.state == TrackState.Live) MarkerKind.Latest else MarkerKind.End,
                routeColor,
            ),
        )
    }
    if (updateViewport && points.isNotEmpty() && (track.state != TrackState.Live || followLive)) {
        mapView.post { applyRouteViewport(mapView, points, centerOnLatest = track.state == TrackState.Live && followLive) }
    }
    mapView.invalidate()
}

private fun routeViewportKey(track: Track, followLive: Boolean): String {
    if (track.points.isEmpty() || (track.state == TrackState.Live && !followLive)) return "${track.id}:${track.state}:${followLive}:no-viewport"
    val north = track.points.maxOf { it.latitude }
    val south = track.points.minOf { it.latitude }
    val east = track.points.maxOf { it.longitude }
    val west = track.points.minOf { it.longitude }
    val latest = track.points.last()
    return listOf(track.id, track.state, followLive, track.points.size, latest.timestamp, north, south, east, west).joinToString(":")
}

private fun applyRouteViewport(mapView: MapView, points: List<GeoPoint>, centerOnLatest: Boolean) {
    if (mapView.width <= 0 || mapView.height <= 0) return
    val targetCenter = if (centerOnLatest) points.last() else points.first()
    if (points.size == 1) {
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(targetCenter)
        return
    }
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    if (abs(north - south) < 0.000001 && abs(east - west) < 0.000001) {
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(targetCenter)
        return
    }
    runCatching {
        mapView.zoomToBoundingBox(BoundingBox(north, east, south, west), false, 96)
        if (centerOnLatest) mapView.controller.setCenter(points.last())
    }.onFailure {
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(targetCenter)
    }
}

private fun marker(mapView: MapView, point: GeoPoint, label: String, detail: String, kind: MarkerKind, movementColor: Int): Marker = Marker(mapView).apply {
    position = point
    title = label
    snippet = detail
    icon = markerDrawable(mapView, kind, movementColor)
    setAnchor(Marker.ANCHOR_CENTER, if (kind == MarkerKind.SavedPoint || kind == MarkerKind.Latest) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
}

private enum class MarkerKind { SavedPoint, Start, End, Latest }

private fun markerDrawable(mapView: MapView, kind: MarkerKind, movementColor: Int): GradientDrawable {
    val density = mapView.context.resources.displayMetrics.density
    fun px(dp: Float): Int = (dp * density).roundToInt()
    val (shape, sizeDp, fill, stroke) = when (kind) {
        MarkerKind.SavedPoint -> MarkerStyle(GradientDrawable.OVAL, 6f, 0xFFFFFFFF.toInt(), movementColor)
        MarkerKind.Start -> MarkerStyle(GradientDrawable.OVAL, 18f, movementColor, 0xFF101412.toInt())
        MarkerKind.End -> MarkerStyle(GradientDrawable.RECTANGLE, 18f, movementColor, 0xFFFFFFFF.toInt())
        MarkerKind.Latest -> MarkerStyle(GradientDrawable.OVAL, 22f, 0xFFFFFFFF.toInt(), movementColor)
    }
    return GradientDrawable().apply {
        this.shape = shape
        setColor(fill)
        setStroke(px(if (kind == MarkerKind.SavedPoint) 1f else 3f), stroke)
        setSize(px(sizeDp), px(sizeDp))
        if (kind == MarkerKind.End) cornerRadius = px(3f).toFloat()
    }
}

private data class MarkerStyle(val shape: Int, val sizeDp: Float, val fill: Int, val stroke: Int)

@Composable
private fun TrackInfoCard(track: Track, modifier: Modifier = Modifier) {
    val displayDurationSeconds = rememberDisplayDurationSeconds(track)
    val movementColor = movementTypeColor(track.movementType)
    Card(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = movementIcon(track.movementType),
                contentDescription = track.movementType.label,
                tint = movementColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = MapOverlayText.summary(track, displayDurationSeconds),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberDisplayDurationSeconds(track: Track): Long = if (track.state == TrackState.Live) rememberLiveElapsedSeconds(track) else track.durationSeconds

@Composable
private fun rememberLiveElapsedSeconds(track: Track): Long {
    var now by remember(track.id) { mutableStateOf(Instant.now()) }
    LaunchedEffect(track.id) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    return track.elapsedSecondsAt(now)
}

private fun sortDirectionLabel(field: TrackSortField, ascending: Boolean): String = when (field) {
    TrackSortField.Date -> if (ascending) "Oldest first" else "Newest first"
    TrackSortField.Distance -> if (ascending) "Shortest first" else "Longest first"
}

private fun TrackSortField.displayLabel(): String = when (this) {
    TrackSortField.Date -> "Date"
    TrackSortField.Distance -> "Distance"
}

private val TimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
private fun formatInstant(instant: java.time.Instant): String = TimeFormatter.format(instant)
private fun formatDistance(meters: Double): String = if (meters >= 1000) "%.2f km".format(meters / 1000.0) else "${meters.roundToInt()} m"
private fun formatPollingInterval(millis: Long): String = "%.1f s".format(millis / 1_000.0)
private fun onOff(value: Boolean): String = if (value) "On" else "Off"
private fun formatDuration(seconds: Long): String {
    val duration = Duration.ofSeconds(seconds)
    return "%02d:%02d:%02d".format(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart())
}

private fun movementIcon(type: MovementType): ImageVector = when (type) {
    MovementType.Walk -> WalkIcon
    MovementType.Run -> RunIcon
    MovementType.Bike -> BikeIcon
    MovementType.Car -> CarIcon
    MovementType.Train -> TrainIcon
}

@Composable
private fun movementTypeColor(type: MovementType): Color {
    val dark = isSystemInDarkTheme()
    return when (type) {
        MovementType.Walk -> if (dark) Color(0xFF8FB996) else Color(0xFF2E7D4F)
        MovementType.Run -> if (dark) Color(0xFFFF8A65) else Color(0xFFC2412D)
        MovementType.Bike -> if (dark) Color(0xFF4DD0E1) else Color(0xFF007C89)
        MovementType.Car -> if (dark) Color(0xFFFFC857) else Color(0xFF9A6500)
        MovementType.Train -> if (dark) Color(0xFFB7A7FF) else Color(0xFF6750C8)
    }
}

@Composable
private fun movementTypeOnColor(type: MovementType): Color = if (isSystemInDarkTheme()) Color(0xFF101412) else Color.White

private val BackArrowIcon: ImageVector = ImageVector.Builder(
    name = "BackArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }
}.build()

private val SettingsGearIcon: ImageVector = ImageVector.Builder(
    name = "SettingsGear",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.5f, 3.5f)
        lineTo(13.5f, 3.5f)
        lineTo(14.2f, 6f)
        lineTo(16f, 6.7f)
        lineTo(18.2f, 5.4f)
        lineTo(20.3f, 7.5f)
        lineTo(19f, 9.7f)
        lineTo(19.8f, 11.5f)
        lineTo(22f, 12f)
        lineTo(19.8f, 12.5f)
        lineTo(19f, 14.3f)
        lineTo(20.3f, 16.5f)
        lineTo(18.2f, 18.6f)
        lineTo(16f, 17.3f)
        lineTo(14.2f, 18f)
        lineTo(13.5f, 20.5f)
        lineTo(10.5f, 20.5f)
        lineTo(9.8f, 18f)
        lineTo(8f, 17.3f)
        lineTo(5.8f, 18.6f)
        lineTo(3.7f, 16.5f)
        lineTo(5f, 14.3f)
        lineTo(4.2f, 12.5f)
        lineTo(2f, 12f)
        lineTo(4.2f, 11.5f)
        lineTo(5f, 9.7f)
        lineTo(3.7f, 7.5f)
        lineTo(5.8f, 5.4f)
        lineTo(8f, 6.7f)
        lineTo(9.8f, 6f)
        close()
        moveTo(12f, 8.5f)
        curveTo(10.1f, 8.5f, 8.5f, 10.1f, 8.5f, 12f)
        curveTo(8.5f, 13.9f, 10.1f, 15.5f, 12f, 15.5f)
        curveTo(13.9f, 15.5f, 15.5f, 13.9f, 15.5f, 12f)
        curveTo(15.5f, 10.1f, 13.9f, 8.5f, 12f, 8.5f)
        close()
    }
}.build()

private val StartControlIcon: ImageVector = ImageVector.Builder(
    name = "StartControl",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.5f)
        curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
        curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
        curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
        curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(9.5f, 7.5f)
        lineTo(17f, 12f)
        lineTo(9.5f, 16.5f)
        close()
    }
}.build()

private val StopControlIcon: ImageVector = ImageVector.Builder(
    name = "StopControl",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8f, 3.5f)
        lineTo(16f, 3.5f)
        lineTo(20.5f, 8f)
        lineTo(20.5f, 16f)
        lineTo(16f, 20.5f)
        lineTo(8f, 20.5f)
        lineTo(3.5f, 16f)
        lineTo(3.5f, 8f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(8.5f, 8.5f)
        lineTo(15.5f, 8.5f)
        lineTo(15.5f, 15.5f)
        lineTo(8.5f, 15.5f)
        close()
    }
}.build()

private val DropdownIcon: ImageVector = ImageVector.Builder(
    name = "Dropdown",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 9f)
        lineTo(12f, 14f)
        lineTo(17f, 9f)
        close()
    }
}.build()

private val RefreshTracksIcon: ImageVector = ImageVector.Builder(
    name = "RefreshTracks",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(19f, 7.5f)
        curveTo(17.4f, 5.4f, 14.9f, 4.2f, 12f, 4.2f)
        curveTo(7.7f, 4.2f, 4.2f, 7.7f, 4.2f, 12f)
        curveTo(4.2f, 16.3f, 7.7f, 19.8f, 12f, 19.8f)
        curveTo(15.4f, 19.8f, 18.3f, 17.6f, 19.4f, 14.5f)
        moveTo(19f, 7.5f)
        lineTo(19f, 3.8f)
        moveTo(19f, 7.5f)
        lineTo(15.3f, 7.5f)
    }
}.build()

private val WalkIcon: ImageVector = ImageVector.Builder(
    name = "Walk",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 5f); lineTo(12f, 5.1f)
        moveTo(11f, 8.5f); lineTo(9.5f, 13f); lineTo(7.5f, 18.5f)
        moveTo(10.3f, 11f); lineTo(14f, 12.5f)
        moveTo(9.5f, 13f); lineTo(13f, 15f); lineTo(15.5f, 19f)
    }
}.build()

private val RunIcon: ImageVector = ImageVector.Builder(
    name = "Run",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13f, 5f); lineTo(13f, 5.1f)
        moveTo(12f, 8f); lineTo(9.5f, 11f); lineTo(13f, 12.5f); lineTo(10.5f, 19f)
        moveTo(13f, 12.5f); lineTo(17.5f, 11.5f)
        moveTo(11.2f, 15f); lineTo(16.5f, 18.5f)
    }
}.build()

private val BikeIcon: ImageVector = ImageVector.Builder(
    name = "Bike",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 17f); curveTo(5.3f, 17f, 4f, 15.7f, 4f, 14f); curveTo(4f, 12.3f, 5.3f, 11f, 7f, 11f); curveTo(8.7f, 11f, 10f, 12.3f, 10f, 14f); curveTo(10f, 15.7f, 8.7f, 17f, 7f, 17f)
        moveTo(17f, 17f); curveTo(15.3f, 17f, 14f, 15.7f, 14f, 14f); curveTo(14f, 12.3f, 15.3f, 11f, 17f, 11f); curveTo(18.7f, 11f, 20f, 12.3f, 20f, 14f); curveTo(20f, 15.7f, 18.7f, 17f, 17f, 17f)
        moveTo(7f, 14f); lineTo(11f, 9f); lineTo(14f, 14f); lineTo(10f, 14f); lineTo(13f, 9f); lineTo(16f, 9f)
    }
}.build()

private val CarIcon: ImageVector = ImageVector.Builder(
    name = "Car",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 13f); lineTo(7f, 8f); lineTo(17f, 8f); lineTo(19f, 13f); lineTo(19f, 17f); lineTo(5f, 17f); close()
        moveTo(7f, 17f); lineTo(7f, 18f)
        moveTo(17f, 17f); lineTo(17f, 18f)
        moveTo(8f, 13f); lineTo(16f, 13f)
    }
}.build()

private val TrainIcon: ImageVector = ImageVector.Builder(
    name = "Train",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 5f); lineTo(17f, 5f); lineTo(17f, 16f); lineTo(7f, 16f); close()
        moveTo(9f, 8f); lineTo(15f, 8f)
        moveTo(9f, 12f); lineTo(15f, 12f)
        moveTo(9f, 19f); lineTo(15f, 19f)
        moveTo(9f, 16f); lineTo(7f, 19f)
        moveTo(15f, 16f); lineTo(17f, 19f)
    }
}.build()

private val CaptureTabIcon: ImageVector = ImageVector.Builder(
    name = "CaptureTab",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(12f, 3.8f)
        curveTo(7.47f, 3.8f, 3.8f, 7.47f, 3.8f, 12f)
        curveTo(3.8f, 16.53f, 7.47f, 20.2f, 12f, 20.2f)
        curveTo(16.53f, 20.2f, 20.2f, 16.53f, 20.2f, 12f)
        curveTo(20.2f, 7.47f, 16.53f, 3.8f, 12f, 3.8f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 8.4f)
        curveTo(10.01f, 8.4f, 8.4f, 10.01f, 8.4f, 12f)
        curveTo(8.4f, 13.99f, 10.01f, 15.6f, 12f, 15.6f)
        curveTo(13.99f, 15.6f, 15.6f, 13.99f, 15.6f, 12f)
        curveTo(15.6f, 10.01f, 13.99f, 8.4f, 12f, 8.4f)
        close()
    }
}.build()

private val TracksTabIcon: ImageVector = ImageVector.Builder(
    name = "TracksTab",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6f, 5.5f)
        lineTo(18f, 5.5f)
        moveTo(5f, 10f)
        curveTo(8.5f, 8.8f, 11.5f, 11.2f, 15f, 10f)
        curveTo(16.2f, 9.6f, 17.4f, 9.4f, 19f, 9.5f)
        moveTo(5f, 14.5f)
        curveTo(8.8f, 13.1f, 11.2f, 16f, 15f, 14.5f)
        curveTo(16.5f, 13.9f, 17.7f, 13.8f, 19f, 14f)
        moveTo(6f, 18.5f)
        lineTo(18f, 18.5f)
    }
}.build()

private val MapTabIcon: ImageVector = ImageVector.Builder(
    name = "MapTab",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4.5f, 6f)
        lineTo(9f, 4.5f)
        lineTo(15f, 6.5f)
        lineTo(19.5f, 5f)
        lineTo(19.5f, 18f)
        lineTo(15f, 19.5f)
        lineTo(9f, 17.5f)
        lineTo(4.5f, 19f)
        close()
        moveTo(9f, 4.5f)
        lineTo(9f, 17.5f)
        moveTo(15f, 6.5f)
        lineTo(15f, 19.5f)
    }
}.build()

private enum class AppTab(val label: String, val icon: ImageVector) {
    Capture("Capture", CaptureTabIcon),
    List("Tracks", TracksTabIcon),
    Map("Map", MapTabIcon),
}
