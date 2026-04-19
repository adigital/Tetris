/*
 * Tetris project — step 3: BLE + LED blink (0x01) + four SG90 taps (0x02..0x05).
 * Same SERVICE_UUID / CHARACTERISTIC_UUID / DEVICE_NAME as Step1/2 / Android app.
 * Servos: GPIO 13=left, 12=right, 14=rotate, 27=down (match D13/D12/D14/D27 wiring).
 * PWM 50 Hz, 14-bit duty (same tuning constants as Step2; calibrate per mechanics).
 */

#if !defined(ARDUINO_ARCH_ESP32)
#error Select an ESP32 board (Arduino IDE: Tools - Board).
#endif

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID "0000ffe0-0000-1000-8000-00805f9b34fb"
#define CHARACTERISTIC_UUID "0000ffe1-0000-1000-8000-00805f9b34fb"
#define DEVICE_NAME "ESP32-MyDevice"

#define LED_PIN 2
#define CMD_BLINK 0x01
#define CMD_TAP_LEFT 0x02
#define CMD_TAP_RIGHT 0x03
#define CMD_TAP_ROTATE 0x04
#define CMD_TAP_DOWN 0x05

static const uint16_t SERVO_NEUTRAL_US = 1500;
static const uint16_t SERVO_PRESS_US = 1200;
static const uint32_t SERVO_HOLD_MS = 180;
static const uint32_t SERVO_SETTLE_MS = 120;

static const double SERVO_HZ = 50;
static const uint8_t SERVO_BITS = 14;
static const uint8_t SERVO_COUNT = 4;
/** Order: left, right, rotate, down — must match BLE command bytes 0x02..0x05. */
static const uint8_t SERVO_PINS[SERVO_COUNT] = {13, 12, 14, 27};

BLEServer *server = nullptr;
BLECharacteristic *txRxCharacteristic = nullptr;
bool deviceConnected = false;
volatile bool pendingBlink = false;
/** 0 = none; else CMD_TAP_LEFT..CMD_TAP_DOWN */
volatile uint8_t pendingTapCmd = 0;
volatile bool busyExecuting = false;

static uint32_t usToDuty(uint16_t us) {
  const uint32_t period_us = 1000000UL / (uint32_t)SERVO_HZ;
  const uint32_t maxDuty = (1UL << SERVO_BITS) - 1;
  return (uint32_t)((uint64_t)us * maxDuty / period_us);
}

#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)

static void servoAttachAll() {
  for (uint8_t i = 0; i < SERVO_COUNT; ++i) {
    ledcAttach(SERVO_PINS[i], SERVO_HZ, SERVO_BITS);
  }
}

static void servoWriteUsIdx(uint8_t idx, uint16_t us) {
  if (idx >= SERVO_COUNT) {
    return;
  }
  ledcWrite(SERVO_PINS[idx], usToDuty(us));
}

#else

static void servoAttachAll() {
  for (uint8_t i = 0; i < SERVO_COUNT; ++i) {
    ledcSetup(i, (uint32_t)SERVO_HZ, SERVO_BITS);
    ledcAttachPin(SERVO_PINS[i], i);
  }
}

static void servoWriteUsIdx(uint8_t idx, uint16_t us) {
  if (idx >= SERVO_COUNT) {
    return;
  }
  ledcWrite(idx, usToDuty(us));
}

#endif

static uint8_t tapCmdToIndex(uint8_t cmd) {
  if (cmd >= CMD_TAP_LEFT && cmd <= CMD_TAP_DOWN) {
    return static_cast<uint8_t>(cmd - CMD_TAP_LEFT);
  }
  return 255;
}

static void runServoTap(uint8_t cmd) {
  const uint8_t idx = tapCmdToIndex(cmd);
  if (idx >= SERVO_COUNT) {
    return;
  }
  Serial.printf("SERVO: tap cmd=0x%02X pin=%u\n", cmd, SERVO_PINS[idx]);
  servoWriteUsIdx(idx, SERVO_NEUTRAL_US);
  delay(SERVO_SETTLE_MS);
  servoWriteUsIdx(idx, SERVO_PRESS_US);
  delay(SERVO_HOLD_MS);
  servoWriteUsIdx(idx, SERVO_NEUTRAL_US);
  delay(SERVO_SETTLE_MS);
}

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
    if (busyExecuting) {
      Serial.println("BLE: command ignored (action in progress)");
      return;
    }
    uint8_t cmd = static_cast<uint8_t>(value[0]);
    if (cmd == CMD_BLINK) {
      pendingBlink = true;
      Serial.println("BLE: blink command (queued)");
    } else if (cmd >= CMD_TAP_LEFT && cmd <= CMD_TAP_DOWN) {
      pendingTapCmd = cmd;
      Serial.printf("BLE: tap command 0x%02X (queued)\n", cmd);
    } else {
      Serial.printf("BLE: unknown command 0x%02X\n", cmd);
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  servoAttachAll();
  for (uint8_t i = 0; i < SERVO_COUNT; ++i) {
    servoWriteUsIdx(i, SERVO_NEUTRAL_US);
  }
  Serial.println("SERVO: four outputs (13=L 12=R 14=Rot 27=Dn), neutral/press us:");
  Serial.printf("  neutral=%u press=%u\n", (unsigned)SERVO_NEUTRAL_US,
                (unsigned)SERVO_PRESS_US);

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
    busyExecuting = true;
    pendingBlink = false;
    Serial.println("LED: blink (500 ms ON / 500 ms OFF)");
    digitalWrite(LED_PIN, HIGH);
    delay(500);
    digitalWrite(LED_PIN, LOW);
    delay(500);
    busyExecuting = false;
  } else if (pendingTapCmd != 0) {
    busyExecuting = true;
    const uint8_t cmd = pendingTapCmd;
    pendingTapCmd = 0;
    runServoTap(cmd);
    busyExecuting = false;
  }

  delay(10);
}
