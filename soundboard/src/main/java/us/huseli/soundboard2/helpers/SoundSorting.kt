package us.huseli.soundboard2.helpers

import android.content.Context
import us.huseli.soundboard2.R
import kotlin.math.absoluteValue

class SoundSorting(val parameter: Parameter, val order: Order) {
    enum class Parameter(val value: Int) {
        NAME(1),
        DURATION(2),
        TIME_ADDED(3),
    }

    enum class Order(val value: Int) {
        ASCENDING(1),
        DESCENDING(-1),
    }

    class SortParameterItem(val value: Parameter, private val label: String) {
        override fun toString() = label
    }

    override fun equals(other: Any?) = other is SoundSorting && other.order == order && other.parameter == parameter

    override fun hashCode() = parameter.value * order.value

    override fun toString() = "${parameter.name} / ${order.name}"

    companion object {
        private val sortParameters = listOf(
            Parameter.NAME,
            Parameter.DURATION,
            Parameter.TIME_ADDED,
        )

        fun fromInt(value: Int) = SoundSorting(
            Parameter.values().firstOrNull { it.value == value.absoluteValue } ?: Parameter.NAME,
            if (value < 0) Order.DESCENDING else Order.ASCENDING
        )

        fun listSortParameterItems(context: Context) = sortParameters.map {
            SortParameterItem(
                it, when (it) {
                    Parameter.NAME -> context.getString(R.string.name)
                    Parameter.DURATION -> context.getString(R.string.duration)
                    Parameter.TIME_ADDED -> context.getString(R.string.creation_time)
                }
            )
        }
    }
}