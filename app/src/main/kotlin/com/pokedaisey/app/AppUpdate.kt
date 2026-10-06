package com.pokedaisey.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionButton
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.OptionOverlay
import com.pokedaisey.app.companion.ui.OptionTitleWindow
import java.io.File

/**
 * The in-app update, for one activity: check GitHub ([AppUpdater]), offer the
 * newer release, download its APK into the cache and hand it to Android's
 * installer - asking first for "install unknown apps" if it isn't allowed yet,
 * and carrying on in [onResume] once the player comes back from that page.
 */
class AppUpdateFlow(private val activity: ComponentActivity) {
    /** The release on offer; the dialog shows while non-null. */
    var release by mutableStateOf<AppUpdater.Release?>(null)
    /** 0..1 while downloading. */
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf<String?>(null)
    var checking by mutableStateOf(false)
        private set
    private var pendingApk: File? = null

    private val updatesDir get() = File(activity.cacheDir, "updates")

    /** Looks for a newer release off the UI thread; [manual] reports "up to date" / offline too. */
    fun check(manual: Boolean = false) {
        if (checking) return
        checking = true
        Thread({
            // An APK we already offered is spent once it's installed (or abandoned).
            updatesDir.listFiles()?.forEach { if (pendingApk != it) it.delete() }
            val found = AppUpdater.check(BuildConfig.VERSION_NAME)
            activity.runOnUiThread {
                checking = false
                if (found != null) {
                    error = null
                    release = found
                } else if (manual) {
                    Toast.makeText(activity, "PokéDaisey ${BuildConfig.VERSION_NAME} is the newest version", Toast.LENGTH_SHORT).show()
                }
            }
        }, "pokedaisey-update-check").apply { isDaemon = true; start() }
    }

    fun dismiss() {
        if (progress != null) return
        release = null
        error = null
    }

    fun openReleasePage() {
        val url = release?.pageUrl ?: AppUpdater.RELEASES_PAGE
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun update() {
        val r = release ?: return
        if (progress != null) return
        error = null
        progress = 0f
        Thread({
            val apk = File(updatesDir, "PokeDaisey-${r.version}.apk")
            val ok = AppUpdater.download(r, apk) { p -> activity.runOnUiThread { progress = p } }
            activity.runOnUiThread {
                progress = null
                if (ok) install(apk) else error = "THE DOWNLOAD FAILED - CHECK THE CONNECTION AND TRY AGAIN"
            }
        }, "pokedaisey-update-download").apply { isDaemon = true; start() }
    }

    /** Back from Android's "install unknown apps" page: install the APK it was for. */
    fun onResume() {
        val apk = pendingApk ?: return
        if (canInstall()) {
            pendingApk = null
            launchInstaller(apk)
        }
    }

    private fun canInstall() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun install(apk: File) {
        if (!canInstall()) {
            pendingApk = apk
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                )
            }
            Toast.makeText(activity, "Allow PokéDaisey to install apps, then come back", Toast.LENGTH_LONG).show()
            return
        }
        launchInstaller(apk)
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(intent) }
            .onSuccess { release = null }
            .onFailure { error = "ANDROID'S INSTALLER DIDN'T OPEN - GET THE APK FROM THE RELEASE PAGE" }
    }
}

/** [flow]'s offer: what's new, then LATER / RELEASE PAGE / UPDATE (a bar while it downloads). */
@Composable
fun UpdateDialog(flow: AppUpdateFlow, m: GbaTextMetrics, small: GbaTextMetrics) {
    val r = flow.release ?: return
    val downloading = flow.progress
    OptionOverlay(onDismiss = { flow.dismiss() }, modifier = Modifier.widthIn(max = 720.dp)) {
        Column {
            OptionTitleWindow("UPDATE AVAILABLE", m)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = m.u * 6, vertical = m.u * 2)) {
                    GbaText(
                        "PokéDaisey ${r.version} is out - you have ${BuildConfig.VERSION_NAME}.",
                        OptionColors.label, OptionColors.labelShadow, m, maxLines = Int.MAX_VALUE,
                    )
                    if (r.notes.isNotEmpty()) {
                        Spacer(Modifier.height(m.u * 4))
                        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            GbaText(r.notes, OptionColors.muted, OptionColors.mutedShadow, small, maxLines = Int.MAX_VALUE)
                        }
                    }
                    flow.error?.let {
                        Spacer(Modifier.height(m.u * 4))
                        GbaText(it, OptionColors.value, OptionColors.valueShadow, small, maxLines = Int.MAX_VALUE)
                    }
                    if (downloading != null) {
                        Spacer(Modifier.height(m.u * 4))
                        GbaText("DOWNLOADING… ${(downloading * 100).toInt()}%", OptionColors.label, OptionColors.labelShadow, m)
                        Spacer(Modifier.height(m.u * 3))
                        SyncProgressBar(downloading, m)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = m.u * 6, bottom = m.u * 2),
                        horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                    ) {
                        OptionButton("LATER", m, enabled = downloading == null, modifier = Modifier.weight(1f), onClick = { flow.dismiss() })
                        OptionButton("RELEASE PAGE", m, modifier = Modifier.weight(1f), onClick = { flow.openReleasePage() })
                        OptionButton(
                            "UPDATE", m, emphasis = true, enabled = downloading == null,
                            modifier = Modifier.weight(1f), onClick = { flow.update() },
                        )
                    }
                }
            }
        }
    }
}
