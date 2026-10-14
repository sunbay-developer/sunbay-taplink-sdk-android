package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import java.util.Collections
import java.util.IdentityHashMap

/** One final recovery outcome per observer instance, even when it registered in multiple roles. */
internal fun notifyDistinctConnectionErrors(
    listeners: List<ConnectionListener>,
    error: ConnectionError,
    onListenerException: (Exception) -> Unit
) {
    val notified = Collections.newSetFromMap(IdentityHashMap<ConnectionListener, Boolean>())
    listeners.forEach { listener ->
        if (notified.add(listener)) {
            try {
                listener.onError(error)
            } catch (exception: Exception) {
                onListenerException(exception)
            }
        }
    }
}
