#pragma once
#include "packet.h"

// Abstract source of environmental readings. Swapping mock data for the real
// SEN54 (air quality) + PDM mic (noise) is a one-file change: implement this
// interface and swap the instantiation in main.cpp. Nothing else in the
// firmware, app, or backend needs to know which implementation is in use —
// that's what the `is_mock` flag on Reading is for.
class SensorSource {
public:
    virtual ~SensorSource() = default;

    // Brings up whatever hardware this source needs. Returns false if the
    // source cannot be used. Defaulted to a no-op success so a source with
    // nothing to initialise (the mock) doesn't have to say so.
    virtual bool begin() { return true; }

    // Populates `out` with the latest reading. Returns false if no reading
    // is currently available (e.g. sensor still warming up).
    virtual bool read(Reading& out) = 0;
};
