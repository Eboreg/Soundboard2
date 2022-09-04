package us.huseli.soundboard2.helpers

import kotlin.math.absoluteValue

class SoundSorting(val parameter: Parameter, val order: Order) {
    enum class Parameter(val value: Int) {
        CUSTOM(1),
        NAME(2),
        DURATION(3),
        TIME_ADDED(4),
    }

    enum class Order(val value: Int) {
        ASCENDING(1),
        DESCENDING(-1),
    }

    override fun toString(): String {
        return "${parameter.name} / ${order.name}"
    }

    companion object {
        fun fromInt(value: Int) = SoundSorting(
            Parameter.values().firstOrNull { it.value == value.absoluteValue } ?: Parameter.CUSTOM,
            if (value < 0) Order.DESCENDING else Order.ASCENDING
        )
    }
}