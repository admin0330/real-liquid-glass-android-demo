package io.github.admin0330.liquidmusic.update.network

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UpdateCancelledException : Exception("Update download was cancelled")

class UpdateCancellation {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<CancelHandle?>()

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        active.getAndSet(null)?.cancel()
    }

    fun throwIfCancelled() {
        if (isCancelled) throw UpdateCancelledException()
    }

    internal fun attach(handle: CancelHandle) {
        throwIfCancelled()
        check(active.compareAndSet(null, handle)) { "A cancellation token cannot serve concurrent requests" }
        if (isCancelled && active.compareAndSet(handle, null)) {
            handle.cancel()
            throw UpdateCancelledException()
        }
    }

    internal fun detach(handle: CancelHandle) {
        active.compareAndSet(handle, null)
    }
}

internal fun interface CancelHandle {
    fun cancel()
}
