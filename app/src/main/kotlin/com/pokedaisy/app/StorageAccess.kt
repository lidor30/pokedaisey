package com.pokedaisy.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * Folders the player picks outside this app's sandbox (their ROMs folder, a saves
 * folder): native code opens games and saves by raw path, which scoped storage
 * blocks without "All files access" on Android 11+, and the picker only hands
 * back a tree Uri, so it is resolved to that path. Android 8-10 have no All files
 * access: there it's the storage permission ("photos, media and files"), with
 * requestLegacyExternalStorage keeping Android 10's scoped storage off.
 */
object StorageAccess {

    // Spelled out (= Manifest.permission.*, PackageManager.PERMISSION_GRANTED): ui-preview compiles this file.
    private const val READ_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    private const val WRITE_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
    private const val GRANTED = 0
    private const val REQUEST_STORAGE = 4701

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(WRITE_STORAGE) == GRANTED

    /**
     * Asks for it: Android's "All files access" page for this app on 11+, the
     * storage permission dialog before. Either way the activity pauses and
     * resumes, which is where the callers carry on.
     */
    fun requestAllFilesAccess(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            activity.requestPermissions(arrayOf(READ_STORAGE, WRITE_STORAGE), REQUEST_STORAGE)
            return
        }
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

    /**
     * The folder picker, opened on the device's own storage rather than Downloads
     * with that storage listed: Android 10's picker hides it until "Show internal
     * storage" is picked from its menu, so all a player found there was Downloads.
     */
    class PickFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input ?: INTERNAL_STORAGE)
                .putExtra("android.provider.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
    }

    private val INTERNAL_STORAGE: Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:")

    /** Every storage volume's root: internal storage, then SD cards / USB drives. */
    fun volumeRoots(): List<File> {
        val internal = Environment.getExternalStorageDirectory()
        val others = File("/storage").listFiles { f -> f.isDirectory && f.name != "emulated" && f.name != "self" }
            ?.sortedBy { it.name }.orEmpty()
        return listOf(internal) + others
    }
}
