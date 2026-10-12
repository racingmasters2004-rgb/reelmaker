package com.reelmaker

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlin.system.exitProcess

/** Saves any crash to a file, then ends the process. */
class ReelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                File(filesDir, "crash.txt").writeText(android.util.Log.getStackTraceString(e).take(12000))
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}
