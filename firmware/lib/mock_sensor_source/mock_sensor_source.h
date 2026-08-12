#pragma once
#include "sensor_source.h"

// Produces plausible urban air-quality and noise readings via a bounded
// random walk, with periodic excursions so the backend's baseline and event
// classifier have something to actually fire on.
//
// Every reading from this source is stamped is_mock = true, which becomes
// FLAG_MOCK_DATA on the wire, a MOCK badge in the app, and a `mock` field in
// every backend API response. That chain is not optional.
//
// The excursions deliberately rotate through distinct *shapes*, not just
// "everything goes up at once". The backend classifies an anomaly by which
// combination of channels moved — PM alone reads as a traffic plume or dust,
// PM with VOC as smoke or exhaust, VOC alone as solvent, noise alone as loud
// but clean air. A mock that only ever raised all three together would
// exercise one branch of that classifier and leave the rest unproven while
// still looking convincing in a demo.
class MockSensorSource : public SensorSource {
public:
    // The excursion shapes, in the order they are staged. Rotating rather
    // than picking at random means a demo of any reasonable length is
    // guaranteed to show all four.
    enum class Excursion : uint8_t {
        None,
        TrafficPlume,     // PM only
        SmokeOrExhaust,   // PM + VOC
        Solvent,          // VOC only
        LoudButClean,     // noise only
    };

    MockSensorSource();
    bool read(Reading& out) override;

private:
    Reading state_;
    uint32_t ticks_until_excursion_;
    uint8_t excursion_ticks_remaining_;
    Excursion current_excursion_;
    uint8_t next_excursion_index_;

    void step();
    void begin_excursion();
    void apply_excursion();
    void walk_calm(bool pm, bool voc, bool noise);
    float bounded_walk(float value, float min_v, float max_v, float max_delta);
};
