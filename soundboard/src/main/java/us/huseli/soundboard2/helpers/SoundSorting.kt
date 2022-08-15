package us.huseli.soundboard2.helpers

class SoundSorting(val parameter: Parameter, val order: Order) {
    enum class Order { ASCENDING, DESCENDING }

    enum class Parameter {
        UNDEFINED,
        NAME,
        DURATION,
        TIME_ADDED,
    }
}