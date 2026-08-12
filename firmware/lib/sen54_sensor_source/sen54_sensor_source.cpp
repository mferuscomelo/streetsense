#include "sen54_sensor_source.h"

#include <Wire.h>
#include <math.h>

namespace {

// The SEN5x driver returns 0 on success and a non-zero error code otherwise.
// The PDM library uses the opposite convention (1 on success) — see
// pdm_noise_meter.cpp. Don't unify these by eye.
constexpr uint16_t SEN5X_OK = 0;

// The SEN54 needs a moment after a reset before it will accept a measurement
// command; the datasheet's power-up time is well under this.
constexpr uint32_t RESET_SETTLE_MS = 100;

bool is_usable(float v) {
    return !isnan(v);
}

} // namespace

bool Sen54SensorSource::begin() {
    Wire.begin();
    sen5x_.begin(Wire);

    if (sen5x_.deviceReset() != SEN5X_OK) {
        return false;
    }
    delay(RESET_SETTLE_MS);

    // startMeasurement() (as opposed to startMeasurementWithoutPm) spins up
    // the fan and the laser scattering unit — PM is the point of this sensor,
    // so the extra power draw is the trade this project is making.
    if (sen5x_.startMeasurement() != SEN5X_OK) {
        return false;
    }

    // A failed mic start is not fatal: PM and VOC are still worth collecting,
    // and read() falls back to reporting no noise rather than no reading.
    noise_.begin();

    return true;
}

bool Sen54SensorSource::read(Reading& out) {
    // Fold in whatever the microphone has captured since the last call,
    // whether or not the SEN54 has a fresh sample this tick.
    float noise_db;
    if (noise_.read(noise_db)) {
        last_noise_db_ = noise_db;
        have_noise_ = true;
    }

    bool data_ready = false;
    if (sen5x_.readDataReady(data_ready) != SEN5X_OK || !data_ready) {
        return false;
    }

    float pm1, pm2_5, pm4, pm10, humidity, temp_c, voc_index, nox_index;
    if (sen5x_.readMeasuredValues(pm1, pm2_5, pm4, pm10, humidity, temp_c,
                                  voc_index, nox_index) != SEN5X_OK) {
        return false;
    }

    // The SEN54 reports NAN for any value it doesn't have yet. During the
    // first seconds after startMeasurement() the PM channels warm up while
    // temperature and humidity are already valid, so a partial reading is
    // normal rather than a fault — hold the packet back until PM is real.
    if (!is_usable(pm1) || !is_usable(pm2_5) || !is_usable(pm4) || !is_usable(pm10)) {
        return false;
    }

    out.pm1 = pm1;
    out.pm2_5 = pm2_5;
    out.pm4 = pm4;
    out.pm10 = pm10;

    // VOC index needs its own warm-up (the algorithm builds a reference over
    // roughly the first minute) and is reported separately from PM, so a
    // missing VOC index shouldn't cost us an otherwise good PM reading.
    out.voc_index = is_usable(voc_index) ? voc_index : 0.0f;
    out.temp_c = is_usable(temp_c) ? temp_c : 0.0f;
    out.humidity = is_usable(humidity) ? humidity : 0.0f;

    out.noise_db = have_noise_ ? last_noise_db_ : 0.0f;

    // nox_index is deliberately unused: the SEN54 has no NOx channel (that's
    // the SEN55) and returns NAN here. The wire format has no NOx field.
    (void) nox_index;

    out.is_mock = false;
    return true;
}
