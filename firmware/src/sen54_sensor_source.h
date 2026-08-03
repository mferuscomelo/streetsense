#pragma once
#include <SensirionI2CSen5x.h>

#include "pdm_noise_meter.h"
#include "sensor_source.h"

// The real sensor path: a Sensirion SEN54 over I2C (STEMMA QT) for PM1/2.5/
// 4/10, VOC index, temperature and humidity, plus the board's onboard PDM
// microphone for sound level.
//
// Produces exactly the same Reading the mock source does, with is_mock left
// false — which is the whole point of the SensorSource interface. Nothing in
// the app or backend needs to know which source is running.
//
// Read the honesty note in pdm_noise_meter.h before quoting the noise figure
// anywhere: it is an uncalibrated, unweighted estimate.
class Sen54SensorSource : public SensorSource {
public:
    bool begin() override;
    bool read(Reading& out) override;

private:
    SensirionI2CSen5x sen5x_;
    PdmNoiseMeter noise_;

    // The SEN54 produces a fresh sample about once a second while the PDM mic
    // delivers samples continuously; this holds the last good noise value so a
    // reading is never blocked on the two happening to line up.
    float last_noise_db_ = 0.0f;
    bool  have_noise_ = false;
};
