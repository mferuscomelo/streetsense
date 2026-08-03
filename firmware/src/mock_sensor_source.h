#pragma once
#include "sensor_source.h"

// Produces plausible urban air-quality and noise readings via a bounded
// random walk, with an occasional excursion so the anomaly-detection path
// on the backend has something to actually fire on during the demo.
// Every reading from this source is stamped is_mock = true.
class MockSensorSource : public SensorSource {
public:
    MockSensorSource();
    bool read(Reading& out) override;

private:
    Reading state_;
    uint32_t ticks_until_excursion_;
    uint8_t excursion_ticks_remaining_;

    void step();
    float bounded_walk(float value, float min_v, float max_v, float max_delta);
};
