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

    /**
     * Derives the Android versionCode from MAJOR.MINOR.PATCH as MAJOR * 10000 + MINOR * 100 + PATCH,
     * so every version bump also produces a higher versionCode (1.4.2 -> 10402).
     */
    fun versionCode(version: String): Int {
        val (major, minor, patch) = validate(version).split('.').map(String::toInt)
        require(minor < 100 && patch < 100) {
            "hhoClientVersion \"$version\" cannot be mapped to a versionCode: MINOR and PATCH must be below 100."
        }
        return major * 10_000 + minor * 100 + patch
    }
}
