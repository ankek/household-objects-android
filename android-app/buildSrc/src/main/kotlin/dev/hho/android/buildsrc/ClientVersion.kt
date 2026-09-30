package dev.hho.android.buildsrc

object ClientVersion {
    private val STRICT_MAJOR_MINOR_PATCH = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")

    fun validate(raw: String): String {
        require(STRICT_MAJOR_MINOR_PATCH.matches(raw)) {
            "hhoClientVersion \"$raw\" is not a valid X-HHO-Client-Version value: must be exactly " +
                "MAJOR.MINOR.PATCH (three dot-separated non-negative integers, e.g. \"1.4.2\") with no " +
                "pre-release or build suffix. See server/internal/httpapi/middleware/clientversion.go " +
                "and assumption A147 in docs/sdlc/hho-ecosystem/assumptions.md."
        }
        return raw
    }
}
