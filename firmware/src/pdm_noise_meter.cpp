#include "pdm_noise_meter.h"

#include <Arduino.h>
#include <PDM.h>
#include <math.h>

namespace {

constexpr int  SAMPLE_RATE_HZ = 16000;
constexpr int  CHANNELS = 1;
constexpr int  MIC_GAIN = 20;          // library default is 20; stated explicitly so it's tunable
constexpr int  READ_BUFFER_SAMPLES = 256;

// dBFS -> SPL offset, derived from the microphone's datasheet sensitivity of
// -26 dBFS at 94 dB SPL: SPL = dBFS + (94 + 26) = dBFS + 120. This is a
// datasheet figure, not a measurement against a reference source — see the
// honesty note in the header.
constexpr float SPL_CALIBRATION_OFFSET_DB = 120.0f;

// Full-scale value for the 16-bit signed samples the PDM library produces.
constexpr float FULL_SCALE = 32768.0f;

// Floor for the RMS before taking a logarithm, so a perfectly silent buffer
// yields a very low level instead of -inf.
constexpr float MIN_RMS = 1.0f;

// Written by the PDM interrupt handler, read by read(). Both are touched
// with interrupts disabled on the read side; `volatile` alone would not make
// the pair consistent with each other.
volatile uint64_t g_sum_squares = 0;
volatile uint32_t g_sample_count = 0;

int16_t g_buffer[READ_BUFFER_SAMPLES];

void onPdmData() {
    int bytes = PDM.available();
    if (bytes <= 0) {
        return;
    }
    if (bytes > static_cast<int>(sizeof(g_buffer))) {
        bytes = sizeof(g_buffer);
    }

    PDM.read(g_buffer, bytes);

    const int samples = bytes / 2;
    uint64_t sum = 0;
    for (int i = 0; i < samples; i++) {
        const int32_t s = g_buffer[i];
        sum += static_cast<uint64_t>(s * s);
    }

    g_sum_squares += sum;
    g_sample_count += samples;
}

} // namespace

bool PdmNoiseMeter::begin() {
    PDM.onReceive(onPdmData);
    PDM.setGain(MIC_GAIN);

    // begin() returns 1 on success in this library, unlike the 0-on-success
    // convention the Sensirion driver uses — don't unify these by eye.
    return PDM.begin(CHANNELS, SAMPLE_RATE_HZ) == 1;
}

bool PdmNoiseMeter::read(float& out_db) {
    uint64_t sum;
    uint32_t count;

    // Snapshot and reset as one unit: the interrupt handler updates both
    // accumulators, and reading them separately could pair a sum with a
    // count from a different set of samples.
    noInterrupts();
    sum = g_sum_squares;
    count = g_sample_count;
    g_sum_squares = 0;
    g_sample_count = 0;
    interrupts();

    if (count == 0) {
        return false;
    }

    float rms = sqrtf(static_cast<float>(sum) / static_cast<float>(count));
    if (rms < MIN_RMS) {
        rms = MIN_RMS;
    }

    const float dbfs = 20.0f * log10f(rms / FULL_SCALE);
    out_db = dbfs + SPL_CALIBRATION_OFFSET_DB;
    return true;
}
