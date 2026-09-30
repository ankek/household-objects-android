package dev.hho.android.testing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.FileInputStream

class AirplaneModeRule : ExternalResource() {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    override fun before() {
        shell("cmd connectivity airplane-mode enable")
        shell("svc wifi disable")
        shell("svc data disable")
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (hasInternet()) {
            check(SystemClock.elapsedRealtime() < deadline) {
                "Device still reports an active internet-capable network ${TIMEOUT_MILLIS}ms after enabling airplane mode"
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    override fun after() {
        shell("cmd connectivity airplane-mode disable")
        shell("svc wifi enable")
        shell("svc data enable")
    }

    fun hasInternet(): Boolean {
        val cm = instrumentation.targetContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 250L
    }
}
