package dev.hho.android.data.auth

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

fun interface DeviceLabelProvider {
    fun currentLabel(): String
}

@Singleton
class DefaultDeviceLabelProvider
    @Inject
    constructor() : DeviceLabelProvider {
        override fun currentLabel(): String =
            listOfNotNull(Build.MANUFACTURER?.trim()?.ifBlank { null }, Build.MODEL?.trim()?.ifBlank { null })
                .joinToString(" ")
                .ifBlank { "Android device" }
    }
