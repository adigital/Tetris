/*
 * Tetris project — step 2: BLE + LED blink (0x01) + one SG90 tap (0x02).
 * Same SERVICE_UUID / CHARACTERISTIC_UUID / DEVICE_NAME as Step1 / Android app.
 * Servo: GPIO 13, LEDC 50 Hz (see Arduino/Servo_SG90_D13/Servo_SG90_D13.ino).
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
#define CMD_SERVO_TAP 0x02

// SG90 pulse widths (microseconds); tune for your mechanics.
static const uint16_t SERVO_NEUTRAL_US = 1500;
static const uint16_t SERVO_PRESS_US = 1200;
static const uint32_t SERVO_HOLD_MS = 180;
static const uint32_t SERVO_SETTLE_MS = 120;

static const uint8_t SERVO_PIN = 13;
static const double SERVO_HZ = 50;
static const uint8_t SERVO_BITS = 14;

BLEServer *server = nullptr;
BLECharacteristic *txRxCharacteristic = nullptr;
bool deviceConnected = false;
volatile bool pendingBlink = false;
volatile bool pendingServoTap = false;
volatile bool busyExecuting = false;

static uint32_t usToDuty(uint16_t us) {
  const uint32_t period_us = 1000000UL / (uint32_t)SERVO_HZ;
  const uint32_t maxDuty = (1UL << SERVO_BITS) - 1;
  return (uint32_t)((uint64_t)us * maxDuty / period_us);
}

#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)

static void servoAttach() { ledcAttach(SERVO_PIN, SERVO_HZ, SERVO_BITS); }
static void servoWriteUs(uint16_t us) { ledcWrite(SERVO_PIN, usToDuty(us)); }

#else

static const int PWM_CHANNEL = 0;

static void servoAttach() {
  ledcSetup(PWM_CHANNEL, (uint32_t)SERVO_HZ, SERVO_BITS);
  ledcAttachPin(SERVO_PIN, PWM_CHANNEL);
}
static void servoWriteUs(uint16_t us) { ledcWrite(PWM_CHANNEL, usToDuty(us)); }

#endif

static void runServoTap() {
  Serial.println("SERVO: tap (neutral -> press -> neutral)");
  servoWriteUs(SERVO_NEUTRAL_US);
  delay(SERVO_SETTLE_MS);
  servoWriteUs(SERVO_PRESS_US);
  delay(SERVO_HOLD_MS);
  servoWriteUs(SERVO_NEUTRAL_US);
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
    } else if (cmd == CMD_SERVO_TAP) {
      pendingServoTap = true;
      Serial.println("BLE: servo tap command (queued)");
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  servoAttach();
  servoWriteUs(SERVO_NEUTRAL_US);
  Serial.printf("SERVO: pin=%u neutral=%u us press=%u us\n", SERVO_PIN,
                (unsigned)SERVO_NEUTRAL_US, (unsigned)SERVO_PRESS_US);

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
  } else if (pendingServoTap) {
    busyExecuting = true;
    pendingServoTap = false;
    runServoTap();
    busyExecuting = false;
  }

  delay(10);
}
