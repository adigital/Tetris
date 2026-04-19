#include <WiFi.h>
#include <time.h>

const char *WIFI_SSID = "Sweethome";
const char *WIFI_PASSWORD = "Flatron11";

const int LED_PIN = 2;

// Local time offset from UTC (Moscow MSK: 3 h). Change for your zone.
const long GMT_OFFSET_SEC = 3 * 3600;
const int DAYLIGHT_OFFSET_SEC = 0;

const char *NTP_SERVER1 = "pool.ntp.org";
const char *NTP_SERVER2 = "time.nist.gov";

const unsigned long NTP_WAIT_MS = 15000;

void blinkN(int n, unsigned onMs = 200, unsigned offMs = 200, unsigned pauseAfterMs = 400) {
  for (int i = 0; i < n; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(onMs);
    digitalWrite(LED_PIN, LOW);
    if (i < n - 1) {
      delay(offMs);
    }
  }
  delay(pauseAfterMs);
}

bool connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Wi-Fi connecting");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > 20000) {
      Serial.println();
      Serial.println("Wi-Fi: timeout");
      return false;
    }
    delay(300);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Wi-Fi OK, IP: ");
  Serial.println(WiFi.localIP());
  return true;
}

bool syncTimeAndPrint() {
  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER1, NTP_SERVER2);

  struct tm timeinfo;
  if (!getLocalTime(&timeinfo, NTP_WAIT_MS)) {
    Serial.println("NTP: failed to obtain time");
    return false;
  }

  char buf[64];
  if (strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S (%A)", &timeinfo) == 0) {
    Serial.println("NTP: time received but format failed");
    return false;
  }

  Serial.print("Time (local): ");
  Serial.println(buf);
  return true;
}

void setup() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.begin(115200);
  delay(500);

  blinkN(1);

  if (!connectWiFi()) {
    blinkN(3);
    return;
  }

  if (syncTimeAndPrint()) {
    blinkN(2);
  } else {
    blinkN(3);
  }
}

void loop() {
  // Idle after one-shot sequence in setup().
}
