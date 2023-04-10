package us.huseli.soundboard2.helpers

interface PlayerEventListener {
    fun onPermanentError(error: String)
    fun onPlaybackPaused(currentPosition: Int, duration: Int)
    fun onPlaybackStarted(currentPosition: Int, duration: Int)
    fun onPlaybackStopped()
    fun onTemporaryError(error: String)
}