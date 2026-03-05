package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.os.BaseBundle
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.get
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException

enum class ShizukuStatus {
    GRANTED,
    NOT_GRANTED,
    STOPPED,
}

fun checkShizukuPermission(code: Int): ShizukuStatus =
    if (Shizuku.getBinder() != null) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.GRANTED
        } else {
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(0)
            }
            ShizukuStatus.NOT_GRANTED
        }
    } else {
        ShizukuStatus.STOPPED
    }

val SubscriptionInfo.uniqueName: String
    get() = "${this.displayName} (SIM ${this.simSlotIndex + 1})"

fun getLatestAppVersion(handler: (String) -> Unit) {
    "https://api.github.com/repos/kyujin-cho/pixel-volte-patch/releases"
        .httpGet()
        .header("X-GitHub-Api-Version", "2022-11-28")
        .responseJson { _, _, result ->
            when (result) {
                is Result.Failure -> {
                    Log.e("Utils:getLatestAppVersion", "Network request failed: ${result.error.message}", result.error)
                    handler("0.0.0")
                }
                is Result.Success -> {
                    try {
                        handler(
                            result
                                .get()
                                .array()
                                .getJSONObject(0)
                                .getString("tag_name"),
                        )
                    } catch (e: Exception) {
                        Log.e("Utils:getLatestAppVersion", "JSON parsing failed", e)
                        handler("0.0.0")
                    }
                }
            }
        }
}

fun NavGraphBuilder.composable(
    route: String,
    label: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(@JvmSuppressWildcards NavBackStackEntry) -> Unit,
) {
    addDestination(
        ComposeNavigator.Destination(provider[ComposeNavigator::class], content).apply {
            this.route = route
            this.label = label
            arguments.forEach { (argumentName, argument) ->
                addArgument(argumentName, argument)
            }
            deepLinks.forEach { deepLink ->
                addDeepLink(deepLink)
            }
        },
    )
}

/**
 * Creates a new [PersistableBundle] from the specified [Bundle].
 * Will ignore all values that are not persistable, according
 * to [.isPersistableBundleType].
 */
fun toPersistableBundle(bundle: Bundle): PersistableBundle {
    val persistableBundle = PersistableBundle()
    for (key in bundle.keySet()) {
        val value = bundle.get(key)
        if (isPersistableBundleType(value)) {
            putIntoBundle(persistableBundle, key, value!!)
        }
    }
    return persistableBundle
}

/**
 * Checks if the specified object can be put into a [PersistableBundle].
 *
 * @see [PersistableBundle Implementation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/PersistableBundle.java.49)
 */
fun isPersistableBundleType(value: Any?): Boolean =
    (
        (value is PersistableBundle) ||
            (value is Int) || (value is IntArray) ||
            (value is Long) || (value is LongArray) ||
            (value is Double) || (value is DoubleArray) ||
            (value is String) || (value is Array<*> && value.isArrayOf<String>()) ||
            (value is Boolean) || (value is BooleanArray)
    )

/**
 * Attempts to insert the specified key value pair into the specified bundle.
 *
 * @throws IllegalArgumentException if the value type can not be put into the bundle.
 */
@Throws(IllegalArgumentException::class)
fun putIntoBundle(
    baseBundle: BaseBundle,
    key: String?,
    value: Any?,
) {
    requireNotNull(value != null) { "Unable to determine type of null values" }
    if (value is Int) {
        baseBundle.putInt(key, value)
    } else if (value is IntArray) {
        baseBundle.putIntArray(key, value)
    } else if (value is Long) {
        baseBundle.putLong(key, value)
    } else if (value is LongArray) {
        baseBundle.putLongArray(key, value)
    } else if (value is Double) {
        baseBundle.putDouble(key, value)
    } else if (value is DoubleArray) {
        baseBundle.putDoubleArray(key, value)
    } else if (value is String) {
        baseBundle.putString(key, value)
    } else if (value is Array<*> && value.isArrayOf<String>()) {
        baseBundle.putStringArray(key, value as Array<String?>)
    } else if (value is Boolean) {
        baseBundle.putBoolean(key, value)
    } else if (value is BooleanArray) {
        baseBundle.putBooleanArray(key, value)
    } else {
        throw IllegalArgumentException(
            ("Objects of type ${value?.javaClass?.simpleName ?: "Unknown"} can not be put into a ${BaseBundle::class.java.simpleName}"),
        )
    }
}

class AsyncTryScope {
    internal var beforeBlock: () -> Unit = {}
    internal var asyncBlock: suspend () -> Unit = {}
    internal var okBlock: () -> Unit = {}
    internal var errorBlock: () -> Unit = {}
    internal var alwaysBlock: () -> Unit = {}

    fun before(block: () -> Unit) {
        beforeBlock = block
    }

    fun async(block: suspend () -> Unit) {
        asyncBlock = block
    }

    fun ok(block: () -> Unit) {
        okBlock = block
    }

    fun error(block: () -> Unit) {
        errorBlock = block
    }

    fun always(block: () -> Unit) {
        alwaysBlock = block
    }
}

fun asyncTry(
    scope: CoroutineScope,
    block: AsyncTryScope.() -> Unit,
) {
    val scopeBlock = AsyncTryScope().apply(block)
    scope.launch {
        var t: Throwable? = null
        try {
            scopeBlock.beforeBlock()
            withContext(Dispatchers.Default) {
                scopeBlock.asyncBlock()
            }
            return@launch scopeBlock.okBlock()
        } catch (e: IllegalStateException) {
            t = e
        } catch (e: IllegalArgumentException) {
            t = e
        } catch (e: InvocationTargetException) {
            t = e.targetException
        } catch (e: NoSuchMethodError) {
            t = e
        } catch (e: NoSuchMethodException) {
            t = e
        } catch (e: SecurityException) {
            t = e
        } finally {
            t?.let {
                Log.e("Utils", "asyncUpdate error", it)
                scopeBlock.errorBlock()
            }
            scopeBlock.alwaysBlock()
        }
    }
}
