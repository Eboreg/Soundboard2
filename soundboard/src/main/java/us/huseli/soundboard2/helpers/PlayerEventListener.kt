package us.huseli.soundboard2.helpers

interface PlayerEventListener {
    fun onPermanentError(error: String)
    fun onPlaybackPaused()
    fun onPlaybackStarted()
    fun onPlaybackStopped()
    fun onTemporaryError(error: String)
}