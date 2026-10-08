package androidx.activity

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

/** onCreate() runs normally; its setContent {} lands in [androidx.activity.compose.Captured]. */
open class ComponentActivity : Context() {
    val intent: Intent = Intent()
    val packageName = "com.pokedaisy.app"
    val contentResolver = android.content.ContentResolver()
    val packageManager = android.content.pm.PackageManager()
    open fun onCreate(savedInstanceState: android.os.Bundle?) {}
    open fun onResume() {}
    open fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    open fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    fun finish() {}
    fun startActivity(i: Intent) {}
    fun requestPermissions(p: Array<String>, code: Int) {}
    fun runOnUiThread(r: () -> Unit) = r()
    override fun getSystemService(n: String): Any? = android.content.ClipboardManager()
    fun <I, O> registerForActivityResult(c: ActivityResultContract<I, O>, cb: (O) -> Unit): ActivityResultLauncher<I> =
        ActivityResultLauncher()
}
