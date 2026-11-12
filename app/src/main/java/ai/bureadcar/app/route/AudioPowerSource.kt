package ai.bureadcar.app.route

import android.car.Car
import android.car.hardware.power.CarPowerManager
import android.car.hardware.power.CarPowerPolicyFilter
import android.car.hardware.power.PowerComponent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/** Observes audio power policy. Screen-off is an additional conservative stop condition. */
class AudioPowerSource(context: Context, private val onEnabled: (Boolean) -> Unit) {
    private val context = context.applicationContext
    private val power = this.context.getSystemService(PowerManager::class.java)
    private var policyEnabled: Boolean? = null
    private var released = false
    private val car = runCatching { Car.createCar(this.context) }.getOrNull()
    private val manager = runCatching {
        car?.getCarManager(Car.POWER_SERVICE) as? CarPowerManager
    }.getOrNull()
    private val listener = CarPowerManager.CarPowerPolicyListener { policy ->
        policyEnabled = policy.isComponentEnabled(PowerComponent.AUDIO)
        publish()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { publish() }
    }

    init {
        ContextCompat.registerReceiver(
            this.context, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runCatching {
            manager?.addPowerPolicyListener(
                this.context.mainExecutor,
                CarPowerPolicyFilter.Builder().setComponents(PowerComponent.AUDIO).build(),
                listener,
            )
            policyEnabled = manager?.currentPowerPolicy?.isComponentEnabled(PowerComponent.AUDIO)
        }.onFailure {
            // This fallback is screen state, not proof that all vehicle Suspend states are observed.
            Log.w("BuReadCar", "Audio power policy unavailable; using screen-state fallback")
        }
        publish()
    }

    private fun publish() {
        if (!released) onEnabled(power.isInteractive && policyEnabled != false)
    }

    fun release() {
        released = true
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { manager?.removePowerPolicyListener(listener) }
        runCatching { car?.disconnect() }
    }
}
