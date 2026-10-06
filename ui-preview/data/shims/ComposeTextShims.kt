package androidx.compose.ui.text.font

/** Android's asset-backed Font(path, assets). */
fun Font(path: String, assetManager: android.content.AssetManager): Font =
    androidx.compose.ui.text.platform.Font(path, assetManager.open(path).readBytes())
