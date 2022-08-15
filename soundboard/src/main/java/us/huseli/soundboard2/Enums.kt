package us.huseli.soundboard2

object Enums {
    @Suppress("unused")
    enum class RepressMode(val index: Int) {
        STOP(0),
        RESTART(1),
        OVERLAP(2),
        PAUSE(3),
    }

    enum class Orientation { PORTRAIT, LANDSCAPE }
}