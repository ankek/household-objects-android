package dev.hho.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dev.hho.android.data.photoqueue.CameraXPhotoCapture
import dev.hho.android.data.photoqueue.PhotoCapture

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class PhotoCaptureModule {
    @Binds
    @ViewModelScoped
    abstract fun bindPhotoCapture(impl: CameraXPhotoCapture): PhotoCapture
}
