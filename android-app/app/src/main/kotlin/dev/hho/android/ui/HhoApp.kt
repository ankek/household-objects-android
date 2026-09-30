package dev.hho.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hho.android.ui.connect.ConnectScreen
import dev.hho.android.ui.deeplink.DeepLinkNotFoundScreen
import dev.hho.android.ui.deeplink.DeepLinkResolution
import dev.hho.android.ui.deeplink.DeepLinkViewModel
import dev.hho.android.ui.deeplink.ItemDeepLink
import dev.hho.android.ui.home.HomeScreen
import dev.hho.android.ui.items.ItemDetailScreen
import dev.hho.android.ui.items.ItemFormScreen
import dev.hho.android.ui.items.ItemListScreen
import dev.hho.android.ui.login.LoginScreen
import dev.hho.android.ui.photos.PhotoCaptureScreen
import dev.hho.android.ui.receiving.ReceivingScreen
import dev.hho.android.ui.scan.ScanScreen
import dev.hho.android.ui.syncstatus.ConflictLogScreen
import dev.hho.android.ui.syncstatus.SyncProblemViewModel
import dev.hho.android.ui.syncstatus.SyncStatusScreen

@Composable
fun HhoApp(
    pendingDeepLink: ItemDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    deepLinkViewModel: DeepLinkViewModel = hiltViewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Connect) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var formItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var formPrefillKind by rememberSaveable { mutableStateOf<String?>(null) }
    var formPrefillValue by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDeepLink, destination) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (destination == AppDestination.Connect || destination == AppDestination.Login) {
            return@LaunchedEffect
        }
        when (val resolution = deepLinkViewModel.resolve(link)) {
            is DeepLinkResolution.Found -> {
                selectedItemId = resolution.itemId
                destination = AppDestination.ItemDetail
            }
            is DeepLinkResolution.NotFound -> destination = AppDestination.DeepLinkNotFound
        }
        onDeepLinkHandled()
    }
    when (destination) {
        AppDestination.Connect ->
            ConnectScreen(onConnected = { destination = AppDestination.Login })
        AppDestination.Login ->
            LoginScreen(onLoggedIn = { destination = AppDestination.Home })
        AppDestination.Home -> {
            val syncProblem by hiltViewModel<SyncProblemViewModel>().hasProblem.collectAsState()
            HomeScreen(
                onViewItems = { destination = AppDestination.ItemList },
                onReceiveDelivery = { destination = AppDestination.Receiving },
                onSyncStatus = { destination = AppDestination.SyncStatus },
                syncProblem = syncProblem,
            )
        }
        AppDestination.SyncStatus ->
            SyncStatusScreen(
                onBack = { destination = AppDestination.Home },
                onConflictLog = { destination = AppDestination.ConflictLog },
            )
        AppDestination.ConflictLog ->
            ConflictLogScreen(onBack = { destination = AppDestination.SyncStatus })
        AppDestination.Receiving ->
            ReceivingScreen(
                onBack = { destination = AppDestination.Home },
                onCheckedIn = { destination = AppDestination.Home },
            )
        AppDestination.ItemList ->
            ItemListScreen(
                onScan = { destination = AppDestination.Scan },
                onNewItem = {
                    formItemId = null
                    formPrefillKind = null
                    formPrefillValue = null
                    destination = AppDestination.ItemForm
                },
                onItemClick = { id ->
                    selectedItemId = id
                    destination = AppDestination.ItemDetail
                },
            )
        AppDestination.ItemDetail ->
            ItemDetailScreen(
                itemId = requireNotNull(selectedItemId) {
                    "AppDestination.ItemDetail reached with no selectedItemId set"
                },
                onBack = {
                    selectedItemId = null
                    destination = AppDestination.ItemList
                },
                onAddPhoto = { destination = AppDestination.PhotoCapture },
                onEdit = {
                    formItemId = selectedItemId
                    formPrefillKind = null
                    formPrefillValue = null
                    destination = AppDestination.ItemForm
                },
            )
        AppDestination.PhotoCapture ->
            PhotoCaptureScreen(
                itemId = requireNotNull(selectedItemId) {
                    "AppDestination.PhotoCapture reached with no selectedItemId set"
                },
                onDone = { destination = AppDestination.ItemDetail },
                onCancel = { destination = AppDestination.ItemDetail },
            )
        AppDestination.ItemForm ->
            ItemFormScreen(
                itemId = formItemId,
                prefillIdentifierKind = formPrefillKind,
                prefillIdentifierValue = formPrefillValue,
                onSaved = { id ->
                    selectedItemId = id
                    destination = AppDestination.ItemDetail
                },
                onCancel = {
                    destination = if (formItemId != null) AppDestination.ItemDetail else AppDestination.ItemList
                },
            )
        AppDestination.Scan ->
            ScanScreen(
                onViewItem = { id ->
                    selectedItemId = id
                    destination = AppDestination.ItemDetail
                },
                onCreateItem = { kind, value ->
                    formItemId = null
                    formPrefillKind = kind
                    formPrefillValue = value
                    destination = AppDestination.ItemForm
                },
                onBack = { destination = AppDestination.ItemList },
            )
        AppDestination.DeepLinkNotFound ->
            DeepLinkNotFoundScreen(onBack = { destination = AppDestination.ItemList })
    }
}

private enum class AppDestination {
    Connect,
    Login,
    Home,
    ItemList,
    ItemDetail,
    ItemForm,
    PhotoCapture,
    Scan,
    Receiving,
    SyncStatus,
    ConflictLog,
    DeepLinkNotFound,
}
