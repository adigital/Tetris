/*
 * Tetris project — step 1: BLE peripheral, connect-only.
 * Same SERVICE_UUID / CHARACTERISTIC_UUID / DEVICE_NAME as ESP32_BLE_Server
 * for compatibility with the Android app.
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID "0000ffe0-0000-1000-8000-00805f9b34fb"
#define CHARACTERISTIC_UUID "0000ffe1-0000-1000-8000-00805f9b34fb"
#define DEVICE_NAME "ESP32-MyDevice"

// Same onboard LED as Arduino/Blink/Blink.ino (ESP32 DevKit-style)
#define LED_PIN 2
// Phone -> ESP32: first byte 0x01 = one blink cycle (500 ms on / 500 ms off)
#define CMD_BLINK 0x01

BLEServer *server = nullptr;
BLECharacteristic *txRxCharacteristic = nullptr;
bool deviceConnected = false;
volatile bool pendingBlink = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    deviceConnected = true;
    Serial.println("BLE: client connected");
  }

  void onDisconnect(BLEServer *pServer) override {
    deviceConnected = false;
    Serial.println("BLE: client disconnected");
    BLEDevice::startAdvertising();
    Serial.println("BLE: advertising restarted");
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = characteristic->getValue();
    if (value.length() == 0) {
      return;
    }
    uint8_t cmd = static_cast<uint8_t>(value[0]);
    if (cmd == CMD_BLINK) {
      pendingBlink = true;
      Serial.println("BLE: blink command (queued)");
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  BLEDevice::init(DEVICE_NAME);
  server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

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

  Serial.print("BLE: advertising started, name=");
  Serial.println(DEVICE_NAME);
}

void loop() {
  if (pendingBlink) {
    pendingBlink = false;
    Serial.println("LED: blink (500 ms ON / 500 ms OFF)");
    digitalWrite(LED_PIN, HIGH);
    delay(500);
    digitalWrite(LED_PIN, LOW);
    delay(500);
  }
  delay(10);
}
