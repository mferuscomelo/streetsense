#include <bluefruit.h>
#include "packet.h"
#include "ble_uuids.h"
#include "mock_sensor_source.h"

namespace {
constexpr uint32_t NOTIFY_INTERVAL_MS = 1000;

BLEService streetSenseService(STREETSENSE_SERVICE_UUID);
BLECharacteristic streetSenseChar(STREETSENSE_CHAR_UUID);
BLEDis deviceInfo;

MockSensorSource sensorSource;
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
