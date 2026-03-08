package dev.bluehouse.enablevolte

import android.os.Bundle
import android.telephony.SubscriptionInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.components.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.Home
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

data class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

val NavDestination.depth: Int get() = this.route?.let { route -> route.count { it == '/' } + 1 } ?: 0

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        setContent {
            EnableVoLTETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    PixelIMSApp()
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp() {
    val context = LocalContext.current.applicationContext
    val navController = rememberNavController()
    val carrierModer = remember(context) { CarrierModer(context) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val scope = rememberCoroutineScope()
    var loading by rememberSaveable { mutableStateOf(true) }
    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var permissionRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var permissionResultReceived by rememberSaveable { mutableStateOf(false) }
    var refreshHomeOnResume by remember { mutableStateOf(false) }

    var subscriptions by remember { mutableStateOf(listOf<SubscriptionInfo>()) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val currentRoute = currentBackStackEntry?.destination?.route
    val isHomeRoute = currentRoute == "home"

    fun navigateToHome() {
        navController.navigate("home") {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    fun refreshRootState() {
        if (loadJob?.isActive == true) {
            return
        }

        loading = true
        loadJob =
            scope.launch {
                try {
                    val shizukuStatus = getShizukuStatus()
                    shizukuEnabled = shizukuStatus != ShizukuStatus.STOPPED
                    shizukuGranted = shizukuStatus == ShizukuStatus.GRANTED

                    if (shizukuStatus != ShizukuStatus.GRANTED) {
                        subscriptions = emptyList()
                        SubscriptionModer.recreateDependencies()
                        if (shizukuStatus == ShizukuStatus.NOT_GRANTED &&
                            !permissionRequestInFlight &&
                            !permissionResultReceived &&
                            !Shizuku.shouldShowRequestPermissionRationale()
                        ) {
                            permissionRequestInFlight = true
                            requestShizukuPermission(0)
                        }
                        return@launch
                    }

                    subscriptions =
                        try {
                            withContext(Dispatchers.Default) {
                                carrierModer.setAirplaneMode(false)
                                carrierModer.setImsRegistrationState(true)
                                carrierModer.subscriptions
                            }
                        } catch (_: IllegalStateException) {
                            emptyList()
                        }
                } finally {
                    loading = false
                    loadJob = null
                }
            }
    }

    fun recoverSubscriptionScreen() {
        navigateToHome()
    }

    DisposableEffect(Unit) {
        val listener =
            Shizuku.OnRequestPermissionResultListener { _, _ ->
                scope.launch {
                    permissionRequestInFlight = false
                    permissionResultReceived = true
                }
            }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }
    DisposableEffect(isHomeRoute) {
        if (isHomeRoute) {
            refreshRootState()
        }
        onDispose {}
    }
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                refreshHomeOnResume = isHomeRoute
            }
            Lifecycle.Event.ON_RESUME -> {
                if (refreshHomeOnResume && isHomeRoute) {
                    refreshRootState()
                }
                refreshHomeOnResume = false
            }
            else -> {}
        }
    }

    if (loading) {
        InfiniteLoadingDialog()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            currentBackStackEntry?.destination?.label?.toString() ?: stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    },
                    navigationIcon = {
                        if (currentBackStackEntry?.destination?.depth?.let { it > 1 } == true) {
                            IconButton(onClick = {
                                navController.popBackStack()
                            }, colors = IconButtonDefaults.filledIconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentBackStackEntry?.destination?.route == "home") {
                            IconButton(onClick = {
                                refreshRootState()
                            }, colors = IconButtonDefaults.filledIconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh contents",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                )
            },
            bottomBar = {
                if (currentBackStackEntry?.destination?.depth?.let { it == 1 } == true) {
                    NavigationBar {
                        val currentDestination = currentBackStackEntry?.destination
                        val items =
                            arrayListOf(
                                Screen("home", stringResource(R.string.home), Icons.Filled.Home),
                            )
                        for (subscription in subscriptions) {
                            items.add(
                                Screen("config${subscription.subscriptionId}", subscription.uniqueName, Icons.Filled.Settings),
                            )
                        }

                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = {
                                    Text(screen.title)
                                },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        // Pop up to the start destination of the graph to
                                        // avoid building up a large stack of destinations
                                        // on the back stack as users select items
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of the same destination when
                                        // reselecting the same item
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
                composable("home", context.resources.getString(R.string.home)) {
                    Home(
                        subscriptions = subscriptions,
                        shizukuEnabled = shizukuEnabled,
                        shizukuGranted = shizukuGranted,
                    )
                }
                for (subscription in subscriptions) {
                    navigation(
                        startDestination = "config${subscription.subscriptionId}",
                        route = "config${subscription.subscriptionId}root",
                    ) {
                        composable("config${subscription.subscriptionId}", context.resources.getString(R.string.sim_config)) {
                            Config(
                                navController = navController,
                                subId = subscription.subscriptionId,
                                onInvalidAccess = {
                                    recoverSubscriptionScreen()
                                },
                            )
                        }
                        composable("config${subscription.subscriptionId}/dump", context.resources.getString(R.string.config_dump_viewer)) {
                            DumpedConfig(
                                subId = subscription.subscriptionId,
                                onInvalidAccess = {
                                    recoverSubscriptionScreen()
                                },
                            )
                        }
                        composable("config${subscription.subscriptionId}/edit", context.resources.getString(R.string.expert_mode)) {
                            Editor(
                                subId = subscription.subscriptionId,
                                onInvalidAccess = {
                                    recoverSubscriptionScreen()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Preview
@Composable
fun PixelIMSAppPreview() {
    EnableVoLTETheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            PixelIMSApp()
        }
    }
}
