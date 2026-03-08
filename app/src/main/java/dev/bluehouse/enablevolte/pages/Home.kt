package dev.bluehouse.enablevolte.pages

import android.content.Intent
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.BooleanPropertyView
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.components.StringPropertyView
import dev.bluehouse.enablevolte.getLatestAppVersion
import dev.bluehouse.enablevolte.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer

const val TAG = "HomeActivity:Home"

@Suppress("ktlint:standard:function-naming")
@Composable
fun Home(
    subscriptions: List<SubscriptionInfo>,
    shizukuEnabled: Boolean,
    shizukuGranted: Boolean,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isIMSRegistered by remember(subscriptions) { mutableStateOf(listOf<Boolean>()) }
    var newerVersion by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(subscriptions, shizukuGranted) {
        loading = true
        try {
            if (!shizukuGranted) {
                isIMSRegistered = emptyList()
            } else {
                val imsStates =
                    withContext(Dispatchers.Default) {
                        subscriptions.map { subscription ->
                            try {
                                SubscriptionModer(context, subscription.subscriptionId).isIMSRegistered
                            } catch (_: IllegalStateException) {
                                false
                            }
                        }
                    }
                isIMSRegistered = imsStates
            }
            getLatestAppVersion {
                Log.d(TAG, "Fetched version $it")
                val latest = SemVer.parse(it)
                val current = SemVer.parse(BuildConfig.VERSION_NAME)
                if (latest > current) {
                    newerVersion = it
                }
            }
        } catch (_: IllegalStateException) {
            isIMSRegistered = emptyList()
        } finally {
            loading = false
        }
    }

    if (loading) {
        InfiniteLoadingDialog()
    } else {
        Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
            HeaderText(text = stringResource(R.string.version))
            if (newerVersion.isNotEmpty()) {
                ClickablePropertyView(
                    label = BuildConfig.VERSION_NAME,
                    value = stringResource(R.string.newer_version_available, newerVersion),
                ) {
                    val url = "https://github.com/kyujin-cho/pixel-volte-patch/releases/tag/$newerVersion"
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = url.toUri()
                    context.startActivity(i, null)
                }
            } else {
                StringPropertyView(label = BuildConfig.VERSION_NAME, value = stringResource(R.string.running_latest_version))
            }
            HeaderText(text = stringResource(R.string.permissions_capabilities))
            BooleanPropertyView(label = stringResource(R.string.shizuku_service_running), toggled = shizukuEnabled)
            BooleanPropertyView(label = stringResource(R.string.shizuku_permission_granted), toggled = shizukuGranted)
            BooleanPropertyView(label = stringResource(R.string.sim_detected), toggled = subscriptions.isNotEmpty())

            for (idx in subscriptions.indices) {
                HeaderText(text = stringResource(R.string.ims_status_for, subscriptions[idx].uniqueName))
                BooleanPropertyView(
                    label = stringResource(R.string.ims_status),
                    toggled = isIMSRegistered.getOrNull(idx) ?: false,
                    trueLabel = stringResource(R.string.registered),
                    falseLabel = stringResource(R.string.unregistered),
                )
            }
        }
    }
}
