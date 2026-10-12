package com.reelmaker

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
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
    @Volatile var installed = false
    fun add(ctx: Context, s: String) {
        try {
            val f = File(ctx.filesDir, "crumbs.txt")
            if (f.exists() && f.length() > 20000) f.delete()
            f.appendText((System.currentTimeMillis() % 1000000).toString() + " " + s + "\n")
        } catch (_: Throwable) {
        }
    }

    /** Installs crash catcher, lifecycle log and hang watchdog. Called from the launcher screen. */
    fun install(app: Application) {
        if (installed) return
        installed = true
        add(app, "---- new process ----")
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                File(app.filesDir, "crash.txt").writeText(android.util.Log.getStackTraceString(e).take(12000))
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { add(a, "created " + a.javaClass.simpleName) }
            override fun onActivityStarted(a: Activity) { add(a, "started " + a.javaClass.simpleName) }
            override fun onActivityResumed(a: Activity) {
                add(a, "resumed " + a.javaClass.simpleName)
                a.window.decorView.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                    var done = false
                    override fun onDraw() {
                        if (!done) { done = true; add(a, "first draw " + a.javaClass.simpleName) }
                    }
                })
            }
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Thread {
            val h = Handler(Looper.getMainLooper())
            var reported = false
            while (true) {
                val ok = AtomicBoolean(false)
                h.post { ok.set(true) }
                try { Thread.sleep(4000) } catch (_: Throwable) {}
                if (!ok.get() && !reported) {
                    reported = true
                    val st = Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                    try { File(app.filesDir, "hang.txt").writeText("Main thread was blocked here:\n$st") } catch (_: Throwable) {}
                }
            }
        }.apply { isDaemon = true; start() }
    }
}

/** Launcher screen. Always shows what happened last time, then opens the app on tap. */
class LauncherActivity : Activity() {
    private fun reasonName(r: Int) = when (r) {
        1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"; 4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"; 8 -> "PERMISSION_CHANGE"; 9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"; 11 -> "USER_STOPPED"; 12 -> "DEPENDENCY_DIED"; 13 -> "OTHER"
        14 -> "FREEZER"; 15 -> "PACKAGE_STATE_CHANGE"; 16 -> "PACKAGE_UPDATED"; else -> "?"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Crumbs.install(application)
        val crash = File(filesDir, "crash.txt")
        val hang = File(filesDir, "hang.txt")
        val crumbsFile = File(filesDir, "crumbs.txt")
        val crumbs = if (crumbsFile.exists()) crumbsFile.readText() else ""
        val report = StringBuilder()
        report.append("Android ").append(Build.VERSION.RELEASE).append(" / SDK ").append(Build.VERSION.SDK_INT)
            .append(" / ").append(Build.MODEL).append("\n\n")
        if (crash.exists()) report.append("CRASH:\n").append(crash.readText()).append("\n\n")
        if (hang.exists()) report.append("HANG:\n").append(hang.readText()).append("\n\n")
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val infos = am.getHistoricalProcessExitReasons(packageName, 0, 6)
                report.append("PHONE'S RECORD OF LAST EXITS:\n")
                for (i in infos) {
                    report.append(" reason=").append(i.reason).append(" ").append(reasonName(i.reason))
                        .append(" status=").append(i.status).append(" desc=").append(i.description).append("\n")
                    if (i.reason == 6) {
                        try { report.append(i.traceInputStream?.bufferedReader()?.readText()?.take(3000) ?: "").append("\n") } catch (_: Throwable) {}
                    }
                }
                report.append("\n")
            } catch (e: Throwable) { report.append("exit info error: ").append(e.toString()).append("\n\n") }
        }
        report.append("STEPS (last lines):\n").append(crumbs.lines().takeLast(40).joinToString("\n"))
        val text = report.toString()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 48, 24, 24) }
        root.addView(TextView(this).apply {
            this.text = "Reel Maker diagnostics. Tap Open app. If it closes, open Reel Maker again and send Claude a screenshot of this screen."
            textSize = 16f
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            this.text = "Copy"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("report", text))
            }
        })
        row.addView(Button(this).apply {
            this.text = "Open app"
            setOnClickListener {
                crash.delete(); hang.delete()
                Crumbs.add(this@LauncherActivity, "user tapped Open app")
                startActivity(Intent(this@LauncherActivity, MainActivity::class.java))
            }
        })
        root.addView(row)
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply {
            this.text = text
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
