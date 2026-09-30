package dev.hho.android

import dev.hho.android.di.BuildInfo
import dev.hho.android.ui.ScaffoldViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldViewModelTest {

    private val fakeBuildInfo = object : BuildInfo {
        override val applicationId = "dev.hho.android"
        override val versionName = "0.1.0"
        override val minSdk = 26
    }

    @Test
    fun `exposes build info in ui state`() {
        val state = ScaffoldViewModel(fakeBuildInfo).state

        assertEquals("dev.hho.android", state.applicationId)
        assertEquals(26, state.minSdk)
    }
}
