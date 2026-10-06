package androidx.activity.result.contract

import android.net.Uri

abstract class ActivityResultContract<I, O>

object ActivityResultContracts {
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class GetContent : ActivityResultContract<String, Uri?>()
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>()
}
