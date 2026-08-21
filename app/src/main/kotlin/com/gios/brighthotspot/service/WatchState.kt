package com.gios.brighthotspot.service

import com.gios.brighthotspot.core.Phase
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A window into the running watcher for the UI, and nothing more. The service owns the
 * loop; the screen only reflects it, so the two share this rather than the screen reaching
 * into the service. All fields are plain observable state -- no logic lives here.
 */
object WatchState {
    val running = MutableStateFlow(false)
    val phase = MutableStateFlow(Phase.IDLE)
    /** Last thing that happened, in words, for the one status line the home screen shows. */
    val lastEvent = MutableStateFlow("")
    /** Devices currently joined to the AP, or -1 when it cannot be read. */
    val clients = MutableStateFlow(-1)
}
