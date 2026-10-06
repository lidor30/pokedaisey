package android.os

/** In the data module so its sources (GbaControls) see it too; a desktop is no handheld. */
object Build {
    const val MANUFACTURER = "desktop"
    const val BRAND = "desktop"
    const val MODEL = "ui-preview"
    object VERSION { const val SDK_INT = 34 }
    object VERSION_CODES { const val O = 26; const val R = 30 }
}
