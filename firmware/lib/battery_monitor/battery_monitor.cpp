#include "battery_monitor.h"

#include <Arduino.h>
#include <Wire.h>

namespace {

// Below this, chargeRate() readings are noise around zero rather than a
// genuine trickle charge — without a floor, the CHARGING flag would flicker
// on and off while the board sits idle on USB power with a full battery.
constexpr float CHARGE_RATE_EPS = 0.5f;  // %/hr

bool vbus_present() {
    return (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
}

} // namespace

bool BatteryMonitor::begin() {
    Wire.begin();
    ready_ = gauge_.begin(&Wire);
    return ready_;
}

bool BatteryMonitor::read(Status& out) {
    if (!ready_) {
        return false;
    }

    out.volts = gauge_.cellVoltage();
    out.soc_percent = gauge_.cellPercent();
    out.rate_percent_per_hour = gauge_.chargeRate();
    out.charging = vbus_present() && out.rate_percent_per_hour > CHARGE_RATE_EPS;
    return true;
}
