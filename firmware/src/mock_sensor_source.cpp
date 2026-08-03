#include "mock_sensor_source.h"
#include <stdlib.h>

namespace {
constexpr uint32_t EXCURSION_INTERVAL_TICKS = 45; // roughly every 45s at 1Hz
constexpr uint8_t  EXCURSION_DURATION_TICKS = 6;

float rand_unit() {
    // rand() is available on both the native test host and the Arduino/
    // Zephyr nRF52 toolchain (via newlib) — no hardware RNG needed for
    // plausible-looking mock data.
    return static_cast<float>(rand()) / static_cast<float>(RAND_MAX);
}
} // namespace

MockSensorSource::MockSensorSource()
    : ticks_until_excursion_(EXCURSION_INTERVAL_TICKS),
      excursion_ticks_remaining_(0) {
    state_.pm1 = 6.0f;
    state_.pm2_5 = 12.0f;
    state_.pm4 = 14.0f;
    state_.pm10 = 18.0f;
    state_.voc_index = 120.0f;
    state_.temp_c = 22.0f;
    state_.humidity = 48.0f;
    state_.noise_db = 55.0f;
    state_.is_mock = true;
}

float MockSensorSource::bounded_walk(float value, float min_v, float max_v, float max_delta) {
    float delta = (rand_unit() * 2.0f - 1.0f) * max_delta;
    float next = value + delta;
    if (next < min_v) next = min_v;
    if (next > max_v) next = max_v;
    return next;
}

void MockSensorSource::step() {
    bool in_excursion = excursion_ticks_remaining_ > 0;

    if (!in_excursion) {
        if (ticks_until_excursion_ == 0) {
            excursion_ticks_remaining_ = EXCURSION_DURATION_TICKS;
            ticks_until_excursion_ = EXCURSION_INTERVAL_TICKS;
            in_excursion = true;
        } else {
            ticks_until_excursion_--;
        }
    }

    if (in_excursion) {
        // A brief, plausible pollution/noise spike — gives the backend's
        // anomaly detector something to flag against the rolling baseline.
        state_.pm2_5 = bounded_walk(state_.pm2_5, 40.0f, 90.0f, 8.0f);
        state_.voc_index = bounded_walk(state_.voc_index, 250.0f, 400.0f, 20.0f);
        state_.noise_db = bounded_walk(state_.noise_db, 78.0f, 95.0f, 4.0f);
        excursion_ticks_remaining_--;
    } else {
        state_.pm1 = bounded_walk(state_.pm1, 2.0f, 15.0f, 1.0f);
        state_.pm2_5 = bounded_walk(state_.pm2_5, 5.0f, 35.0f, 2.0f);
        state_.pm4 = bounded_walk(state_.pm4, 6.0f, 40.0f, 2.0f);
        state_.pm10 = bounded_walk(state_.pm10, 8.0f, 45.0f, 2.0f);
        state_.voc_index = bounded_walk(state_.voc_index, 80.0f, 250.0f, 10.0f);
        state_.noise_db = bounded_walk(state_.noise_db, 45.0f, 75.0f, 3.0f);
    }

    state_.temp_c = bounded_walk(state_.temp_c, 20.0f, 26.0f, 0.3f);
    state_.humidity = bounded_walk(state_.humidity, 40.0f, 60.0f, 1.5f);
}

bool MockSensorSource::read(Reading& out) {
    step();
    out = state_;
    return true;
}
