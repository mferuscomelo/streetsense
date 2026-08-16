# Demo video script

For the Hackster submission: a 90–120s walkthrough, plus the written summary
required alongside it. Shot list first, summary at the bottom — read the
summary as narration, or record it as a voiceover; either satisfies the
requirement.

Target runtime: **~105s**. Screen-record the phone (or mirror it) for the app
shots and the browser for the dashboard shot; no editing beyond trimming cuts
is needed.

## Shot list

| Time | Shot | What to show / say |
|---|---|---|
| 0:00–0:10 | Cold open on the landing page or a city street | *"Every run, every ride, every dog walk — you have no idea what you just breathed, or how loud it was."* |
| 0:10–0:25 | Phone: activity picker ("How are you travelling today?") | Tap **Jogging**. App scans and connects to `StreetSense-01` over BLE. |
| 0:25–0:45 | Phone: live session screen | Metrics ticking at 1 Hz — PM2.5, noise, VOC. Tap a metric to open the detail sheet (min/avg/max sparkline). Point out the **MOCK** or **SEN54** badge — *"you always know if a reading is real."* |
| 0:45–0:65 | Phone: tap Finish → session summary | Dose ("0.6 mg inhaled"), the worst 30-second stretch, and the route map colored by air quality. |
| 0:65–0:85 | Browser: dashboard | Switch to the live dashboard — the same reading arriving over Server-Sent Events. Point at a grid cell *your* node never walked that still has a verdict, because another contributor did — and the confidence legend that tells them apart from a corroborated block. |
| 0:85–1:00 | Landing page "Shared Map" section or a simple crowd-grid graphic | *"One phone today. At scale, this is street-level air and noise data no city has ever had — built by the people already out there, updating live, instead of a handful of fixed monitors."* |
| 1:00–1:05 | StreetSense wordmark / streetsense.tech | Close on the URL and category: Best Health Solution, Modern Java in the Wild. |

## Written summary (submit alongside the video)

StreetSense is a personal exposure tracker for runners, cyclists, and dog
walkers: a battery-powered BLE sensor node measures particulate matter, VOCs,
and noise, an Android app carries it through a tagged session, and a Java 26
Spring Boot backend turns raw readings into an inhaled **dose** — not "the air
was 34 µg/m³" but "you inhaled 0.6 mg of it, and the worst 30 seconds was this
junction." Every reading is also pooled, anonymized to a ~110m grid cell, into
a shared live map — so a street your phone never walked can still return an
answer, because someone else walked it. Java 26 runs the backend end to end:
the Foreign Function & Memory API decodes the wire packet, Structured
Concurrency fans one ingested reading into persist/baseline/anomaly-check as a
single unit of work, Stream Gatherers compute the rolling per-block baseline
and the dose itself, and a sealed event hierarchy classifies *what* changed
(traffic plume vs. wildfire smoke vs. solvent) rather than just *how much*.
The real-hardware path (Adafruit nRF52840 driver board + Sensirion SEN54 +
MAX17048 fuel gauge) has been run end to end over BLE; 42 backend tests
cover the decode, baseline, dose, and crowd logic.

## Notes for recording

- Keep the phone in dark mode for both the picker and session screens — it's the more polished of the two themes (see [`docs/images/`](images/)).
- The dashboard shot works fine with `scripts/run-backend.sh --seed` running, which populates the crowd layer from one physical node so the "a cell you never walked" beat is visible without needing a second person out with a second sensor.
- If there's time left after the core 105s, a 5–10s cutaway to the physical hardware node (board + sensor + battery, unboxed or worn) before the dashboard shot strengthens the BYOD/hardware-reality of the story.
