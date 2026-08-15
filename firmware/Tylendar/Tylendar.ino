// Tylendar: a daily Chinese almanac on a 10.2 inch four color e-paper.
//
// Every night the ESP32 wakes from deep sleep, joins WiFi, downloads
// the image that GitHub Actions rendered for the new day, pushes it to
// the panel, puts the panel into deep sleep, and goes back to sleep
// until the next midnight.
//
// Board: Good Display ESP32-L with DESPI-C02 adapter.
// Panel: GDEM102F91, 960x640, black white yellow red.

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <time.h>
#include "config.h"
#include "epd_gdem102f91.h"

static const uint32_t WIFI_TIMEOUT_MS = 30000;
static const uint32_t NTP_TIMEOUT_MS = 15000;
static const uint32_t HTTP_TIMEOUT_MS = 30000;
static const size_t CHUNK_SIZE = 4096;
static uint8_t chunk[CHUNK_SIZE];

static bool connectWifi() {
  Serial.printf("WiFi: connecting to %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > WIFI_TIMEOUT_MS) {
      Serial.println("WiFi: timed out");
      return false;
    }
    delay(250);
  }
  Serial.printf("WiFi: connected, ip %s\n", WiFi.localIP().toString().c_str());
  return true;
}

static bool syncClock() {
  configTime(TZ_OFFSET_SECONDS, 0, "pool.ntp.org", "time.google.com");
  struct tm tm_now;
  uint32_t start = millis();
  while (!getLocalTime(&tm_now, 0)) {
    if (millis() - start > NTP_TIMEOUT_MS) {
      Serial.println("NTP: timed out");
      return false;
    }
    delay(250);
  }
  Serial.printf("NTP: %04d-%02d-%02d %02d:%02d\n", tm_now.tm_year + 1900,
                tm_now.tm_mon + 1, tm_now.tm_mday, tm_now.tm_hour, tm_now.tm_min);
  return true;
}

// Streams the image straight from the HTTP socket to the panel, so no
// frame buffer is needed. Returns false without refreshing if anything
// goes wrong, which leaves the previous day visible instead of garbage.
static bool fetchAndDisplay() {
  WiFiClientSecure client;
  client.setInsecure(); // public calendar data, integrity checked by size
  HTTPClient http;
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  if (!http.begin(client, IMAGE_URL)) {
    Serial.println("HTTP: begin failed");
    return false;
  }
  int code = http.GET();
  if (code != HTTP_CODE_OK) {
    Serial.printf("HTTP: status %d\n", code);
    http.end();
    return false;
  }
  int len = http.getSize();
  if (len != EPD_FRAME_BYTES) {
    Serial.printf("HTTP: unexpected size %d, want %ld\n", len, EPD_FRAME_BYTES);
    http.end();
    return false;
  }

  if (!epdInit()) {
    Serial.println("EPD: init timed out, check ribbon cable and RESE switch");
    http.end();
    return false;
  }
  epdStartFrame();

  WiFiClient *stream = http.getStreamPtr();
  long remaining = EPD_FRAME_BYTES;
  uint32_t lastData = millis();
  while (remaining > 0) {
    size_t avail = stream->available();
    if (avail == 0) {
      if (!http.connected() || millis() - lastData > HTTP_TIMEOUT_MS) {
        Serial.printf("HTTP: stream died with %ld bytes left\n", remaining);
        http.end();
        epdPowerOffAndSleep(); // abandon frame, do not refresh
        return false;
      }
      delay(10);
      continue;
    }
    size_t want = avail < CHUNK_SIZE ? avail : CHUNK_SIZE;
    if ((long)want > remaining) want = remaining;
    int got = stream->readBytes(chunk, want);
    if (got <= 0) continue;
    epdWriteData(chunk, got);
    remaining -= got;
    lastData = millis();
  }
  http.end();

  Serial.println("EPD: refreshing, this takes about 20 seconds");
  bool ok = epdRefresh();
  epdPowerOffAndSleep();
  if (!ok) Serial.println("EPD: refresh timed out");
  return ok;
}

// Seconds until the next WAKE_HOUR:WAKE_MINUTE local time.
static uint64_t secondsToNextWake() {
  time_t now = time(nullptr);
  struct tm tm_now;
  localtime_r(&now, &tm_now);
  struct tm tm_wake = tm_now;
  tm_wake.tm_hour = WAKE_HOUR;
  tm_wake.tm_min = WAKE_MINUTE;
  tm_wake.tm_sec = 0;
  time_t wake = mktime(&tm_wake);
  if (wake <= now) wake += 24 * 3600;
  return (uint64_t)(wake - now);
}

static void sleepFor(uint64_t seconds, const char *why) {
  Serial.printf("Sleep: %llu seconds (%s)\n", seconds, why);
  Serial.flush();
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
  esp_sleep_enable_timer_wakeup(seconds * 1000000ULL);
  esp_deep_sleep_start();
}

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\nTylendar waking up");
  epdPinsBegin();

  if (!connectWifi()) sleepFor(RETRY_MINUTES * 60ULL, "wifi failed");
  bool haveTime = syncClock();
  bool displayed = fetchAndDisplay();

  if (!displayed) sleepFor(RETRY_MINUTES * 60ULL, "fetch or display failed");
  if (!haveTime) sleepFor(24 * 3600ULL, "no clock, blind daily cycle");
  sleepFor(secondsToNextWake(), "until next midnight refresh");
}

void loop() {}
