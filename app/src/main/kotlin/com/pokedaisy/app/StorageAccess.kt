package com.pokedaisy.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import java.io.File

/**
 * Folders the player picks outside this app's sandbox (their ROMs folder, a saves
 * folder): native code opens games and saves by raw path, which scoped storage
 * blocks without "All files access" on Android 11+, and the picker only hands
 * back a tree Uri, so it is resolved to that path.
 */
object StorageAccess {

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Opens Android's "All files access" page for this app. */
    fun requestAllFilesAccess(activity: ComponentActivity) {
        val intent = runCatching {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}"))
        }.getOrElse { Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }
        runCatching { activity.startActivity(intent) }
    }

    /** A picked folder's real path - only possible for folders on the device's own
     * storage or SD card (the `com.android.externalstorage.documents` provider). */
    fun treePath(treeUri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val (volume, rel) = parts
        val root = if (volume == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
        return File(root, rel).absolutePath
    }

    /** Every storage volume's root: internal storage, then SD cards / USB drives. */
    fun volumeRoots(): List<File> {
        val internal = Environment.getExternalStorageDirectory()
        val others = File("/storage").listFiles { f -> f.isDirectory && f.name != "emulated" && f.name != "self" }
            ?.sortedBy { it.name }.orEmpty()
        return listOf(internal) + others
    }
}
