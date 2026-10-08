package android.net

class Uri private constructor(private val s: String) {
    val scheme: String? get() = s.substringBefore(':', "").ifEmpty { null }
    val authority: String? get() = s.substringAfter("://", "").substringBefore('/').ifEmpty { null }
    val path: String? get() = "/" + s.substringAfter("://", "").substringAfter('/', "")
    val lastPathSegment: String? get() = path?.substringAfterLast('/')?.ifEmpty { null }

    companion object { fun parse(s: String) = Uri(s) }

    override fun toString() = s
}
