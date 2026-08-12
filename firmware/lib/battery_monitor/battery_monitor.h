#pragma once
#include <Adafruit_MAX1704X.h>

// Reads the Adafruit MAX17048 fuel gauge (STEMMA QT, shares the I2C bus with
// the SEN54 at a different address: 0x36 vs the SEN54's 0x69).
//
// HONESTY NOTE — the MAX17048's state of charge is a ModelGauge voltage-curve
// estimate, not a coulomb count. It re-converges over the first minutes after
// power-up rather than being accurate instantly, and drifts slightly with
// cell age/temperature. Fine for "roughly how much battery is left" and for
// comparing against its own recent history; not a substitute for a proper
// gas gauge if this is ever billed for or safety-critical.
//
// The nRF52840 has no charger-status pin exposed to this driver board, so
// "charging" is inferred rather than read directly: USB VBUS present AND the
// gauge's own charge rate is positive. Both the boolean and the raw charge
// rate go out on the wire, so the inference is auditable rather than hidden.
class BatteryMonitor {
public:
    struct Status {
        float volts;
        float soc_percent;
        float rate_percent_per_hour;  // negative while discharging
        bool  charging;
    };

    // Brings up the gauge on the shared I2C bus. Returns false if the gauge
    // is not present or does not respond — deliberately not fatal to the
    // caller, mirroring how the SEN54 path treats a missing sensor.
    bool begin();

    // Populates `out` with the latest reading. Returns false if the gauge
    // was never successfully started.
    bool read(Status& out);

private:
    Adafruit_MAX17048 gauge_;
    bool ready_ = false;
};
