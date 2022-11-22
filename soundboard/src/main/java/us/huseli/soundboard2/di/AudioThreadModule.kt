package us.huseli.soundboard2.di

import android.os.Handler
import android.os.HandlerThread
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object AudioThreadModule {
    @Provides
    @Singleton
    fun provideAudioThreadHandler(): Handler {
        val audioThread = HandlerThread("audio")
        audioThread.priority = Thread.MAX_PRIORITY
        audioThread.start()
        return Handler(audioThread.looper)
    }
}