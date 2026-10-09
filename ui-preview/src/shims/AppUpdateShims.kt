package android.content.pm

/** In-app update: the preview may always "install". */
class PackageManager {
    fun canRequestPackageInstalls() = true
    fun queryIntentActivities(i: android.content.Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun getPackageArchiveInfo(path: String, flags: Int): PackageInfo? = null
    fun getPackageInfo(name: String, flags: Int) = PackageInfo()

    companion object {
        const val GET_SIGNATURES = 0x40
        const val GET_SIGNING_CERTIFICATES = 0x8000000
    }
}

class PackageInfo {
    val packageName = ""
    val versionCode = 0
    val longVersionCode = 0L
    val signingInfo: SigningInfo? = null
    val signatures: Array<Signature>? = null
}

class SigningInfo { val apkContentsSigners: Array<Signature> = emptyArray() }

class Signature { fun toCharsString() = "" }

class ResolveInfo { val activityInfo = ActivityInfo() }

class ActivityInfo { val packageName = ""; val name = ""; val applicationInfo = ApplicationInfo() }

class ApplicationInfo {
    val flags = 0
    companion object { const val FLAG_SYSTEM = 1 }
}
