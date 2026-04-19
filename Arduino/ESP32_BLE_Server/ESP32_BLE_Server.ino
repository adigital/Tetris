/*
 * ESP32 as BLE peripheral for a custom Android app.
 * Match SERVICE_UUID and CHARACTERISTIC_UUID in your Android GATT code.
 *
 * - Phone WRITE -> onWrite() receives bytes (commands).
 * - ESP32 -> phone: setValue() + notify() on the same characteristic.
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Use your own 128-bit UUIDs (must match the Android app).
#define SERVICE_UUID "0000ffe0-0000-1000-8000-00805f9b34fb"
#define CHARACTERISTIC_UUID "0000ffe1-0000-1000-8000-00805f9b34fb"

#define DEVICE_NAME "ESP32-MyDevice"

BLEServer *server = nullptr;
BLECharacteristic *txRxCharacteristic = nullptr;
bool deviceConnected = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    deviceConnected = true;
  }

  void onDisconnect(BLEServer *pServer) override {
    deviceConnected = false;
    // Restart advertising so the phone can connect again.
    BLEDevice::startAdvertising();
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = characteristic->getValue();
    if (value.length() == 0) {
      return;
    }
    // Example: echo first byte back as notify (replace with your protocol).
    uint8_t b = static_cast<uint8_t>(value[0]);
    uint8_t reply[] = {static_cast<uint8_t>('R'), b};
    characteristic->setValue(reply, sizeof(reply));
    characteristic->notify();
  }
};

void setup() {
  Serial.begin(115200);
  delay(500);

  BLEDevice::init(DEVICE_NAME);
  server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  // READ: app can read last value. WRITE: app sends data. NOTIFY: ESP pushes to app.
  txRxCharacteristic = service->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_NOTIFY);
  txRxCharacteristic->addDescriptor(new BLE2902());
  txRxCharacteristic->setCallbacks(new RxCallbacks());

  uint8_t initial[] = "OK";
  txRxCharacteristic->setValue(initial, sizeof(initial) - 1);

  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);
  adv->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE advertising as " DEVICE_NAME);
}

void loop() {
  // Example: periodic notify every 5 s while connected (optional).
  static uint32_t last = 0;
  if (deviceConnected && txRxCharacteristic && millis() - last > 5000) {
    last = millis();
    uint8_t tick[] = {'t', static_cast<uint8_t>((millis() / 1000) & 0xFF)};
    txRxCharacteristic->setValue(tick, sizeof(tick));
    txRxCharacteristic->notify();
  }
  delay(10);
}
