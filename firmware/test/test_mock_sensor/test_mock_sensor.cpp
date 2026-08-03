#include <unity.h>

#include <set>

#include "mock_sensor_source.h"

namespace {

// Upper bounds of the mock's calm random walk. Anything above these is the
// source deliberately staging an excursion rather than drifting.
constexpr float CALM_PM2_5_MAX = 35.0f;
constexpr float CALM_VOC_MAX = 250.0f;
constexpr float CALM_NOISE_MAX = 75.0f;

constexpr uint8_t PM_ELEVATED = 1 << 0;
constexpr uint8_t VOC_ELEVATED = 1 << 1;
constexpr uint8_t NOISE_ELEVATED = 1 << 2;

// The four excursion shapes the backend's event classifier has to tell apart.
// A mock that only ever raises all three channels at once can exercise one
// branch of that classifier and no others — which would make both the tests
// and the demo look like they work while proving nothing.
constexpr uint8_t TRAFFIC_PLUME = PM_ELEVATED;
constexpr uint8_t SMOKE_OR_EXHAUST = PM_ELEVATED | VOC_ELEVATED;
constexpr uint8_t SOLVENT = VOC_ELEVATED;
constexpr uint8_t LOUD_BUT_CLEAN = NOISE_ELEVATED;

uint8_t signature_of(const Reading& r) {
    uint8_t sig = 0;
    if (r.pm2_5 > CALM_PM2_5_MAX) sig |= PM_ELEVATED;
    if (r.voc_index > CALM_VOC_MAX) sig |= VOC_ELEVATED;
    if (r.noise_db > CALM_NOISE_MAX) sig |= NOISE_ELEVATED;
    return sig;
}

std::set<uint8_t> observed_signatures(int ticks) {
    MockSensorSource source;
    std::set<uint8_t> seen;
    Reading reading;
    for (int i = 0; i < ticks; i++) {
        if (source.read(reading)) {
            seen.insert(signature_of(reading));
        }
    }
    return seen;
}

} // namespace

void test_mock_produces_every_event_signature() {
    std::set<uint8_t> seen = observed_signatures(2000);

    TEST_ASSERT_TRUE_MESSAGE(seen.count(TRAFFIC_PLUME), "no PM-only excursion (traffic plume / dust)");
    TEST_ASSERT_TRUE_MESSAGE(seen.count(SMOKE_OR_EXHAUST), "no PM+VOC excursion (smoke or exhaust)");
    TEST_ASSERT_TRUE_MESSAGE(seen.count(SOLVENT), "no VOC-only excursion (solvent or fumes)");
    TEST_ASSERT_TRUE_MESSAGE(seen.count(LOUD_BUT_CLEAN), "no noise-only excursion (loud but clean)");
}

void test_mock_spends_most_of_its_time_calm() {
    // Excursions have to stay rare, or the rolling baseline never settles and
    // every reading looks normal relative to a permanently elevated history.
    MockSensorSource source;
    Reading reading;
    int calm = 0, total = 0;
    for (int i = 0; i < 2000; i++) {
        if (source.read(reading)) {
            total++;
            if (signature_of(reading) == 0) calm++;
        }
    }

    TEST_ASSERT_GREATER_THAN_MESSAGE(total * 3 / 4, calm, "mock is excursion-heavy; baseline will never settle");
}

void test_every_mock_reading_is_flagged_as_mock() {
    MockSensorSource source;
    Reading reading;
    for (int i = 0; i < 200; i++) {
        if (source.read(reading)) {
            TEST_ASSERT_TRUE_MESSAGE(reading.is_mock, "mock reading escaped without is_mock set");
        }
    }
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_mock_produces_every_event_signature);
    RUN_TEST(test_mock_spends_most_of_its_time_calm);
    RUN_TEST(test_every_mock_reading_is_flagged_as_mock);
    return UNITY_END();
}
