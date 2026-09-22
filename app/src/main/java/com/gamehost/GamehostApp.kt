package com.gamehost

import android.app.Application
import android.content.Context
import com.gamehost.render.DissolveMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Note what is *not* configured here: a custom Coil `ImageLoader`.
 *
 * Coil's defaults are already right for this app. Its disk cache sits in the network
 * path, so local `content://` documents are read straight from the user's files and
 * never duplicated into app storage — which is what §16 Trap 3 asks for, without a line
 * of configuration. The default memory cache (a fraction of app memory) is appropriate
 * for one or two full-screen decodes, and hardware bitmaps stay enabled, keeping large
 * images in graphics memory rather than on the Java heap. They are only ever drawn,
 * never read back, so the usual objection to them does not apply.
 *
 * Tune this only if a real device shows memory pressure.
 */
class GamehostApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)

        // Generate the watercolor noise field off the main thread, once, before the first
        // transition asks for it. It is an immutable process-wide value, so warming it
        // early cannot make the two windows disagree — it only avoids a hitch on the GM's
        // first slot tap.
        graph.appScope.launch(Dispatchers.Default) { DissolveMask.warm() }
    }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as GamehostApp).graph
