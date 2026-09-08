package com.example.transport

/**
 * UERI Emergency Trigger Service Abstraction
 *
 * Current implementation: AppEmergencyTrigger (One-click SOS touch trigger)
 * Future roadmap: SystemEmergencyTrigger (Hardware power key sequence 5x, Lock screen tile,
 * WearOS wrist gesture, or vehicle telematics trigger).
 */
interface EmergencyTriggerService {
    val triggerSource: String
    fun onTriggerActivated(callback: () -> Unit)
}

class AppEmergencyTrigger : EmergencyTriggerService {
    override val triggerSource: String = "App In-UI One-Click SOS"

    private var activeCallback: (() -> Unit)? = null

    override fun onTriggerActivated(callback: () -> Unit) {
        this.activeCallback = callback
    }

    fun fire() {
        activeCallback?.invoke()
    }
}

class SystemEmergencyTrigger : EmergencyTriggerService {
    override val triggerSource: String = "OS-Level / Hardware Sequence Trigger (Conceptual)"

    override fun onTriggerActivated(callback: () -> Unit) {
        // Future production implementation: BroadcastReceiver for hardware power button 5x tap
    }
}
