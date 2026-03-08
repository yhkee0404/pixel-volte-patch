package dev.bluehouse.enablevolte

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationTargetException

class SubscriptionActionGate(
    private val moder: SubscriptionModer,
    private val onAccessLost: (SubscriptionAccessStatus) -> Unit,
) {
    private fun handleActionFailure(error: Throwable) {
        when (val status = moder.validateAccess()) {
            SubscriptionAccessStatus.VALID ->
                when (error) {
                    is InvocationTargetException -> throw error.targetException
                    else -> throw error
                }
            else -> onAccessLost(status)
        }
    }

    fun run(action: () -> Unit) {
        when (val status = moder.validateAccess()) {
            SubscriptionAccessStatus.VALID ->
                try {
                    action()
                } catch (error: IllegalStateException) {
                    handleActionFailure(error)
                } catch (error: IllegalArgumentException) {
                    handleActionFailure(error)
                } catch (error: InvocationTargetException) {
                    handleActionFailure(error)
                } catch (error: NoSuchMethodError) {
                    handleActionFailure(error)
                } catch (error: NoSuchMethodException) {
                    handleActionFailure(error)
                } catch (error: SecurityException) {
                    handleActionFailure(error)
                }
            else -> onAccessLost(status)
        }
    }

    fun <T> guard(action: (T) -> Unit): (T) -> Unit =
        { value ->
            run {
                action(value)
            }
        }

    fun guard(action: () -> Unit): () -> Unit =
        {
            run(action)
        }

    fun launch(
        scope: CoroutineScope,
        action: suspend () -> Unit,
    ) {
        when (val status = moder.validateAccess()) {
            SubscriptionAccessStatus.VALID ->
                scope.launch {
                    try {
                        action()
                    } catch (error: IllegalStateException) {
                        handleActionFailure(error)
                    } catch (error: IllegalArgumentException) {
                        handleActionFailure(error)
                    } catch (error: InvocationTargetException) {
                        handleActionFailure(error)
                    } catch (error: NoSuchMethodError) {
                        handleActionFailure(error)
                    } catch (error: NoSuchMethodException) {
                        handleActionFailure(error)
                    } catch (error: SecurityException) {
                        handleActionFailure(error)
                    }
                }
            else -> onAccessLost(status)
        }
    }
}

val LocalSubscriptionActionGate = compositionLocalOf<SubscriptionActionGate?> { null }

@Composable
fun rememberSubscriptionActionGate(
    moder: SubscriptionModer,
    onAccessLost: (SubscriptionAccessStatus) -> Unit,
): SubscriptionActionGate =
    remember(moder, onAccessLost) {
        SubscriptionActionGate(moder, onAccessLost)
    }
