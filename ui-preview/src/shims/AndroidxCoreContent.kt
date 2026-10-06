package androidx.core.content

object FileProvider {
    fun getUriForFile(c: android.content.Context, authority: String, f: java.io.File): android.net.Uri =
        android.net.Uri.parse("content://$authority/${f.name}")
}
