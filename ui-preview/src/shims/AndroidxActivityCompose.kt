package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable

/** The last content an activity passed to setContent {}. */
object Captured { var content: (@Composable () -> Unit)? = null }

fun ComponentActivity.setContent(content: @Composable () -> Unit) { Captured.content = content }

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

@Composable
fun <I, O> rememberLauncherForActivityResult(c: ActivityResultContract<I, O>, cb: (O) -> Unit): ActivityResultLauncher<I> =
    ActivityResultLauncher()
