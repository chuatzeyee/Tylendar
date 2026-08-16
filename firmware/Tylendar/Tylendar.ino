// Tylendar: a daily Chinese almanac on a 10.2 inch four color e-paper.
//
// The ESP32 wakes from deep sleep at each time in WAKE_TIMES, joins
// WiFi, downloads the image that GitHub Actions rendered minutes
// earlier, pushes it to the panel, puts the panel into deep sleep, and
// goes back to sleep until the next scheduled wake.
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

struct WifiNetwork {
  const char *ssid;
  const char *password;
};
static const WifiNetwork WIFI_LIST[] = WIFI_NETWORKS;
static const size_t WIFI_LIST_COUNT = sizeof(WIFI_LIST) / sizeof(WIFI_LIST[0]);

static bool connectWifi() {
  WiFi.mode(WIFI_STA);
  for (size_t i = 0; i < WIFI_LIST_COUNT; i++) {
    Serial.printf("WiFi: connecting to %s\n", WIFI_LIST[i].ssid);
    WiFi.begin(WIFI_LIST[i].ssid, WIFI_LIST[i].password);
    uint32_t start = millis();
    while (millis() - start <= WIFI_TIMEOUT_MS) {
      wl_status_t st = WiFi.status();
      if (st == WL_CONNECTED) {
        Serial.printf("WiFi: connected, ip %s\n", WiFi.localIP().toString().c_str());
        return true;
      }
      // Absent SSID fails in seconds, no need to burn the full timeout
      // before trying the next network on the list.
      if (st == WL_NO_SSID_AVAIL || st == WL_CONNECT_FAILED) break;
      delay(250);
    }
    Serial.println("WiFi: not this one");
    WiFi.disconnect(true);
    delay(100);
  }
  Serial.println("WiFi: no listed network reachable");
  return false;
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
// goes wrong, which leaves the previous image visible instead of garbage.
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

static const int WAKE_MINUTES_OF_DAY[] = WAKE_TIMES;
static const size_t WAKE_COUNT =
    sizeof(WAKE_MINUTES_OF_DAY) / sizeof(WAKE_MINUTES_OF_DAY[0]);

// Seconds until the next WAKE_TIMES entry, local time. The extra loop
// pass wraps to tomorrow's first wake once today's have all passed.
static uint64_t secondsToNextWake() {
  time_t now = time(nullptr);
  struct tm tm_now;
  localtime_r(&now, &tm_now);
  for (size_t i = 0; i <= WAKE_COUNT; i++) {
    struct tm tm_wake = tm_now;
    int m = WAKE_MINUTES_OF_DAY[i % WAKE_COUNT];
    tm_wake.tm_hour = m / 60;
    tm_wake.tm_min = m % 60;
    tm_wake.tm_sec = 0;
    time_t wake = mktime(&tm_wake) + (i == WAKE_COUNT ? 24 * 3600 : 0);
    if (wake > now) return (uint64_t)(wake - now);
  }
  return 24 * 3600ULL;
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
  sleepFor(secondsToNextWake(), "until next scheduled refresh");
}

void loop() {}
