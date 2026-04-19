void setup() {
  pinMode(2, OUTPUT); // onboard LED (ESP32 DevKit-style)
}

void loop() {
  digitalWrite(2, HIGH);
  delay(500);
  digitalWrite(2, LOW);
  delay(500);
}
