package com.reelmaker

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object Crumbs {
    fun add(ctx: Context, s: String) {
        try {
            val f = File(ctx.filesDir, "crumbs.txt")
            if (f.exists() && f.length() > 20000) f.delete()
            f.appendText((System.currentTimeMillis() % 1000000).toString() + " " + s + "\n")
        } catch (_: Throwable) {
        }
    }
}
