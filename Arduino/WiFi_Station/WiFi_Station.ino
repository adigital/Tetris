#include <WiFi.h>

// Replace with your network name and password.
const char *WIFI_SSID = "Sweethome";
const char *WIFI_PASSWORD = "Flatron11";

void setup() {
  Serial.begin(115200);
  delay(500);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Connecting");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }

  Serial.println();
  Serial.print("Connected, IP address: ");
  Serial.println(WiFi.localIP());
}

void loop() {
  // Nothing here — connection is kept by the stack.
}
