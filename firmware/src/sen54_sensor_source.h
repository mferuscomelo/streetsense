#pragma once
#include "sensor_source.h"

// Stub for the real Sensirion SEN54 (PM/VOC) + PDM mic (noise) sensor path.
// Not wired up this slice — see docs/future-work.md. Implementing read()
// against the SEN54 I2C driver and the PDM mic's dB(A) estimate is the
// entire migration; nothing else in the firmware, app, or backend changes,
// since both sources produce the same Reading and the wire format is fixed.
class Sen54SensorSource : public SensorSource {
public:
    bool read(Reading& out) override;
};
