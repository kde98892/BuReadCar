package ai.bureadcar.app.route

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the platform currently forbids. Apply this, not speed, per the Pleos driving guide. */
data class UxLimits(val setupBlocked: Boolean = false, val limitText: Boolean = false)

class UxRestrictionSource(context: Context) {
    private val _limits = MutableStateFlow(UxLimits())
    val limits: StateFlow<UxLimits> = _limits

    private val car: Car? = runCatching { Car.createCar(context.applicationContext) }.getOrNull()
    private val manager: CarUxRestrictionsManager? = runCatching {
        car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager?
    }.getOrNull()

    init {
        runCatching {
            manager?.registerListener { apply(it) }
            manager?.currentCarUxRestrictions?.let(::apply)
        }
    }

    private fun apply(restrictions: CarUxRestrictions) {
        val active = restrictions.activeRestrictions
        val required = restrictions.isRequiresDistractionOptimization
        _limits.value = UxLimits(
            setupBlocked = required && active and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0,
            limitText = required && active and
                (CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT or CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH) != 0,
        )
    }

    fun release() {
        runCatching {
            manager?.unregisterListener()
            car?.disconnect()
        }
    }
}
