#pragma once

// WiFi credentials
#define WIFI_SSID "YOUR_WIFI_SSID"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

// Where the rendered image lives. GitHub Actions in this repo updates it
// every night just after midnight Singapore time.
#define IMAGE_URL "https://raw.githubusercontent.com/chuatzeyee/Tylendar/main/output/tylendar.bin"

// Timezone offset from UTC in seconds. Singapore is UTC+8.
#define TZ_OFFSET_SECONDS (8 * 3600)

// Local time of the daily refresh. 00:20 leaves time for the GitHub
// Action (00:05) to finish rendering the new day.
#define WAKE_HOUR 0
#define WAKE_MINUTE 20

// How long to sleep before retrying after any failure, in minutes.
#define RETRY_MINUTES 60

// Pin mapping of the Good Display ESP32-L board to the DESPI-C02
// adapter. Taken from the official Good Display sample code for this
// kit: BUSY=A14, RES=A15, DC=A16, CS=A17. SPI uses the ESP32 default
// VSPI pins, SCK=18 and MOSI=23.
#define PIN_BUSY 13
#define PIN_RST 12
#define PIN_DC 14
#define PIN_CS 27
