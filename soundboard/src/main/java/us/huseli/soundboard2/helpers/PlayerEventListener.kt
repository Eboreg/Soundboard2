package us.huseli.soundboard2.helpers

interface PlayerEventListener {
    fun onPlaybackStarted(currentPosition: Int, duration: Int)
    fun onPlaybackStopped()
    fun onPlaybackPaused(currentPosition: Int, duration: Int)
    fun onTemporaryError(error: String)
    fun onPermanentError(error: String)
}