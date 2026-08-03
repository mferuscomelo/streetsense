#pragma once
#include <stdint.h>

// Estimates ambient sound level from the board's onboard PDM microphone.
//
// HONESTY NOTE — read this before trusting the number.
//
// What leaves this class is an *uncalibrated* sound-pressure estimate, not a
// certified dB(A) measurement. Two things are missing versus a real sound
// level meter:
//
//   1. No A-weighting filter. This is a flat (unweighted) RMS level, so it
//      over-reports low-frequency energy — traffic rumble and wind read
//      higher than a dB(A) meter would report them.
//   2. No per-unit calibration. The dBFS -> SPL offset below is taken from
//      the microphone's datasheet sensitivity, not measured against a
//      reference source, so absolute values carry a few dB of error.
//
// This is fine for what StreetSense actually does with it — comparing a
// place against its own history, and one route against another, where a
// consistent offset cancels out. It is NOT fine for any absolute claim
// ("this street is above X dB"), and the docs must not make one.
//
// The wire format field stays named noise_db for protocol compatibility;
// see docs/honest-caveats.md for the claim this project actually makes.
class PdmNoiseMeter {
public:
    // Starts the PDM peripheral. Returns false if it could not be started.
    bool begin();

    // Writes the level estimated over every sample captured since the last
    // call into `out_db`, and resets the accumulator. Returns false when no
    // samples have arrived yet (the mic is still starting up), leaving
    // `out_db` untouched.
    bool read(float& out_db);
};
