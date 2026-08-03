#include <bluefruit.h>
#include "packet.h"
#include "ble_uuids.h"

// Which sensor path gets compiled in is the only difference between the two
// ledglasses environments in platformio.ini. Everything downstream — the
// packet, the app, the backend — is identical either way; the mock flags
// itself on the wire via FLAG_MOCK_DATA.
#if defined(STREETSENSE_MOCK) && STREETSENSE_MOCK
#include "mock_sensor_source.h"
#else
#include "sen54_sensor_source.h"
#endif

namespace {
constexpr uint32_t NOTIFY_INTERVAL_MS = 1000;

BLEService streetSenseService(STREETSENSE_SERVICE_UUID);
BLECharacteristic streetSenseChar(STREETSENSE_CHAR_UUID);
BLEDis deviceInfo;

#if defined(STREETSENSE_MOCK) && STREETSENSE_MOCK
MockSensorSource sensorSource;
#else
Sen54SensorSource sensorSource;
#endif

uint16_t sequence = 0;
uint32_t lastNotifyMs = 0;

void startAdvertising() {
    // The primary advertising packet is capped at 31 bytes. Flags (3B) +
    // TX power (3B) + a 128-bit service UUID (18B) already use 24 of them,
    // leaving only 7 — enough for a 5-character name ("Stree"), silently
    // truncating "StreetSense-01". The full name goes in the scan response
    // packet instead, which has its own separate 31-byte budget.
    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addTxPower();
    Bluefruit.Advertising.addService(streetSenseService);
    Bluefruit.ScanResponse.addName();

    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(32, 244); // in units of 0.625ms
    Bluefruit.Advertising.setFastTimeout(30);
    Bluefruit.Advertising.start(0); // 0 = advertise indefinitely
}
} // namespace

void setup() {
    Serial.begin(115200);

    // Deliberately not fatal. If the SEN54 is unplugged or the mic won't
    // start, the node still advertises and stays connectable — it simply
    // never notifies, because read() keeps returning false. A node that
    // vanishes from the air is much harder to diagnose in the field than one
    // that is present but silent.
    if (!sensorSource.begin()) {
        Serial.println("sensor source failed to start — no packets will be sent");
    }

    Bluefruit.begin();
    Bluefruit.setTxPower(4);
    Bluefruit.setName("StreetSense-01");

    deviceInfo.setManufacturer("Adafruit Industries");
    deviceInfo.setModel("LED Glasses Driver nRF52840");
    deviceInfo.begin();

    // NOTE: BLEService::begin() must be called before adding its
    // characteristics — characteristics attach to the most recently
    // begun service. Get this order wrong and the characteristic silently
    // attaches to the wrong (or no) service.
    streetSenseService.begin();

    streetSenseChar.setProperties(CHR_PROPS_NOTIFY);
    streetSenseChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
    streetSenseChar.setFixedLen(sizeof(StreetSensePacket));
    streetSenseChar.begin();

    startAdvertising();
}

void loop() {
    uint32_t now = millis();
    if (now - lastNotifyMs < NOTIFY_INTERVAL_MS) {
        return;
    }
    lastNotifyMs = now;

    Reading reading;
    if (!sensorSource.read(reading)) {
        return;
    }

    StreetSensePacket packet;
    encode_packet(reading, sequence++, packet);

    uint8_t buf[sizeof(StreetSensePacket)];
    write_packet_bytes(packet, buf);

    if (Bluefruit.connected() && streetSenseChar.notifyEnabled()) {
        streetSenseChar.notify(buf, sizeof(buf));
    }
}
