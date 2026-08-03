#include "mock_sensor_source.h"
#include <stdlib.h>

namespace {
constexpr uint32_t EXCURSION_INTERVAL_TICKS = 45; // roughly every 45s at 1Hz
constexpr uint8_t  EXCURSION_DURATION_TICKS = 6;

// Calm ranges. The backend's rolling baseline settles inside these, so an
// excursion has to leave them decisively to register as an anomaly rather
// than as the top of the normal walk.
constexpr float CALM_PM2_5_MIN = 5.0f,   CALM_PM2_5_MAX = 35.0f;
constexpr float CALM_VOC_MIN = 80.0f,    CALM_VOC_MAX = 250.0f;
constexpr float CALM_NOISE_MIN = 45.0f,  CALM_NOISE_MAX = 75.0f;

// Excursion ranges, each clear of the corresponding calm ceiling.
constexpr float HOT_PM2_5_MIN = 45.0f,   HOT_PM2_5_MAX = 90.0f;
constexpr float HOT_VOC_MIN = 280.0f,    HOT_VOC_MAX = 400.0f;
constexpr float HOT_NOISE_MIN = 80.0f,   HOT_NOISE_MAX = 95.0f;

// The rotation. Order is arbitrary but fixed, so a demo of any reasonable
// length shows every shape and the sequence is reproducible when explaining
// what the classifier just did.
constexpr MockSensorSource::Excursion EXCURSION_CYCLE[] = {
    MockSensorSource::Excursion::TrafficPlume,
    MockSensorSource::Excursion::SmokeOrExhaust,
    MockSensorSource::Excursion::Solvent,
    MockSensorSource::Excursion::LoudButClean,
};
constexpr uint8_t EXCURSION_CYCLE_LENGTH =
    sizeof(EXCURSION_CYCLE) / sizeof(EXCURSION_CYCLE[0]);

float rand_unit() {
    // rand() is available on both the native test host and the Arduino/
    // Zephyr nRF52 toolchain (via newlib) — no hardware RNG needed for
    // plausible-looking mock data.
    return static_cast<float>(rand()) / static_cast<float>(RAND_MAX);
}
} // namespace

MockSensorSource::MockSensorSource()
    : ticks_until_excursion_(EXCURSION_INTERVAL_TICKS),
      excursion_ticks_remaining_(0),
      current_excursion_(Excursion::None),
      next_excursion_index_(0) {
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

void MockSensorSource::begin_excursion() {
    current_excursion_ = EXCURSION_CYCLE[next_excursion_index_];
    next_excursion_index_ = (next_excursion_index_ + 1) % EXCURSION_CYCLE_LENGTH;
    excursion_ticks_remaining_ = EXCURSION_DURATION_TICKS;
    ticks_until_excursion_ = EXCURSION_INTERVAL_TICKS;
}

// Walks the channels that are *not* part of the current excursion, keeping
// them inside their calm ranges. Without this, a channel left frozen at its
// last excursion value would read as elevated during the next excursion of a
// different shape and smear the signatures together.
void MockSensorSource::walk_calm(bool pm, bool voc, bool noise) {
    if (pm) {
        state_.pm2_5 = bounded_walk(state_.pm2_5, CALM_PM2_5_MIN, CALM_PM2_5_MAX, 2.0f);
    }
    if (voc) {
        state_.voc_index = bounded_walk(state_.voc_index, CALM_VOC_MIN, CALM_VOC_MAX, 10.0f);
    }
    if (noise) {
        state_.noise_db = bounded_walk(state_.noise_db, CALM_NOISE_MIN, CALM_NOISE_MAX, 3.0f);
    }
}

void MockSensorSource::apply_excursion() {
    switch (current_excursion_) {
        case Excursion::TrafficPlume:
            // Particulates without solvent vapour: brake dust, road dust, a
            // diesel passing. Noise often rises too in reality, but keeping
            // it calm here is what makes the shape distinguishable.
            state_.pm2_5 = bounded_walk(state_.pm2_5, HOT_PM2_5_MIN, HOT_PM2_5_MAX, 8.0f);
            walk_calm(false, true, true);
            break;

        case Excursion::SmokeOrExhaust:
            state_.pm2_5 = bounded_walk(state_.pm2_5, HOT_PM2_5_MIN, HOT_PM2_5_MAX, 8.0f);
            state_.voc_index = bounded_walk(state_.voc_index, HOT_VOC_MIN, HOT_VOC_MAX, 20.0f);
            walk_calm(false, false, true);
            break;

        case Excursion::Solvent:
            state_.voc_index = bounded_walk(state_.voc_index, HOT_VOC_MIN, HOT_VOC_MAX, 20.0f);
            walk_calm(true, false, true);
            break;

        case Excursion::LoudButClean:
            state_.noise_db = bounded_walk(state_.noise_db, HOT_NOISE_MIN, HOT_NOISE_MAX, 4.0f);
            walk_calm(true, true, false);
            break;

        case Excursion::None:
            break;
    }
}

void MockSensorSource::step() {
    if (excursion_ticks_remaining_ == 0) {
        if (ticks_until_excursion_ == 0) {
            begin_excursion();
        } else {
            ticks_until_excursion_--;
            current_excursion_ = Excursion::None;
        }
    }

    if (excursion_ticks_remaining_ > 0) {
        apply_excursion();
        excursion_ticks_remaining_--;
    } else {
        walk_calm(true, true, true);
    }

    // The coarser PM channels and the climate channels track along regardless
    // of excursion shape — they aren't part of any signature the backend
    // classifies on, so keeping them simple keeps the signatures clean.
    state_.pm1 = bounded_walk(state_.pm1, 2.0f, 15.0f, 1.0f);
    state_.pm4 = bounded_walk(state_.pm4, 6.0f, 40.0f, 2.0f);
    state_.pm10 = bounded_walk(state_.pm10, 8.0f, 45.0f, 2.0f);
    state_.temp_c = bounded_walk(state_.temp_c, 20.0f, 26.0f, 0.3f);
    state_.humidity = bounded_walk(state_.humidity, 40.0f, 60.0f, 1.5f);
}

bool MockSensorSource::read(Reading& out) {
    step();
    out = state_;
    return true;
}
