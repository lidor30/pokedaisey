package android.content.pm

/** In-app update: the preview may always "install". */
class PackageManager {
    fun canRequestPackageInstalls() = true
    fun queryIntentActivities(i: android.content.Intent, flags: Int): List<ResolveInfo> = emptyList()
}

class ResolveInfo { val activityInfo = ActivityInfo() }

class ActivityInfo { val packageName = ""; val name = "" }
