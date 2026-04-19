/*
  ESP32 + SG-90 на GPIO 13: LEDC, без Servo.

  Serial: 115200. Мигание встроенного LED (GPIO 2, стиль ESP32 DevKit) синхронно с ожиданиями у серво.
  Если у вас LED на другом GPIO — смените STATUS_LED ниже.
*/
#if !defined(ARDUINO_ARCH_ESP32)
#error Выберите плату ESP32 (Arduino IDE: Tools - Board).
#endif

static const uint8_t SERVO_PIN = 13;
static const double SERVO_HZ = 50;
static const uint8_t SERVO_BITS = 14;

// На многих ESP32 DevKit встроенный синий LED на GPIO 2 (без макроса LED_BUILTIN в вашем ядре).
static const int STATUS_LED = 2;

static uint32_t usToDuty(uint16_t us) {
  const uint32_t period_us = 1000000UL / (uint32_t)SERVO_HZ;
  const uint32_t maxDuty = (1UL << SERVO_BITS) - 1;
  return (uint32_t)((uint64_t)us * maxDuty / period_us);
}

#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)

static void servoAttach() { ledcAttach(SERVO_PIN, SERVO_HZ, SERVO_BITS); }
static void servoDetach() { ledcDetach(SERVO_PIN); }
static void servoWriteUs(uint16_t us) { ledcWrite(SERVO_PIN, usToDuty(us)); }

#else

static const int PWM_CHANNEL = 0;

static void servoAttach() {
  ledcSetup(PWM_CHANNEL, (uint32_t)SERVO_HZ, SERVO_BITS);
  ledcAttachPin(SERVO_PIN, PWM_CHANNEL);
}
static void servoDetach() { ledcDetachPin(SERVO_PIN); }
static void servoWriteUs(uint16_t us) { ledcWrite(PWM_CHANNEL, usToDuty(us)); }

#endif

static void ledBlinkWhileWaiting(unsigned long totalMs, unsigned long halfPeriodMs) {
  const unsigned long t0 = millis();
  bool level = false;
  unsigned long next = t0;
  while (millis() - t0 < totalMs) {
    const unsigned long now = millis();
    if (now >= next) {
      level = !level;
      digitalWrite(STATUS_LED, level ? HIGH : LOW);
      next = now + halfPeriodMs;
    }
    delay(1);
  }
}

void setup() {
  pinMode(STATUS_LED, OUTPUT);
  digitalWrite(STATUS_LED, LOW);

  Serial.begin(115200);
  delay(300);
  Serial.println();
  Serial.println("SG90 test: LEDC on GPIO13, Serial 115200");
  Serial.printf("SERVO_PIN=%u STATUS_LED=%d (onboard LED)\n", SERVO_PIN, STATUS_LED);
  Serial.printf("50 Hz, %u bits, duty(1000us)=%lu duty(1500us)=%lu duty(2000us)=%lu\n",
                SERVO_BITS, (unsigned long)usToDuty(1000), (unsigned long)usToDuty(1500),
                (unsigned long)usToDuty(2000));

  servoAttach();
  Serial.println("setup: servoAttach OK");
}

static void servoGotoUs(uint16_t us, unsigned long holdMs) {
  Serial.printf("  %u us -> duty=%lu, hold %lu ms\n", us, (unsigned long)usToDuty(us),
                holdMs);
  servoWriteUs(us);
  ledBlinkWhileWaiting(holdMs, 120);
}

void loop() {
  static uint32_t cycle = 0;
  cycle++;

  Serial.printf("\n--- cycle %lu ---\n", (unsigned long)cycle);

  // Раньше было 1480/1520 us — для SG-90 это часто меньше люфта/мёртвой зоны, визуально «тишина».
  // Ниже — заметный, но не предельный ход (подстройте под свой привод при необходимости).
  servoGotoUs(1200, 700);
  servoGotoUs(1800, 700);
  servoGotoUs(1500, 500);

  Serial.println("servoDetach (PWM off)");
  servoDetach();
  digitalWrite(STATUS_LED, LOW);

  Serial.println("pause 3 s, slow blink 500 ms");
  ledBlinkWhileWaiting(3000, 500);

  Serial.println("servoAttach (next cycle)");
  servoAttach();
}
