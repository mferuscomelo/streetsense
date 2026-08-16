# Honest caveats

What this project does and doesn't claim, stated plainly rather than left
to be discovered.

- **The SEN54 is a consumer PM/VOC *proxy*, not a lab-grade reference
  instrument.** Its readings are useful for relative comparison (this
  block vs. its own history) and not suitable for regulatory or medical
  claims. The firmware's real sensor path (`ledglasses_sen54` environment,
  `firmware/src/sen54_sensor_source.cpp`) has been run end-to-end against
  physical hardware — real SEN54, PDM mic, and MAX17048 fuel gauge, over
  BLE to the app. `ledglasses` (the default environment) still runs the
  mock source for demos without the sensor attached; see
  `docs/ble-protocol.md` for how mock data is flagged end-to-end.
- **The noise figure is an uncalibrated estimate, not a certified dB(A)
  measurement.** `firmware/src/pdm_noise_meter.cpp` computes a flat
  (unweighted) RMS level from the onboard PDM microphone and applies a
  datasheet sensitivity offset — there is no A-weighting filter and no
  per-unit calibration against a reference source. It over-reports
  low-frequency energy (traffic rumble, wind) relative to a real sound level
  meter, and its absolute value carries a few dB of uncalibrated error. This
  is adequate for what StreetSense does with it — comparing a place against
  its own history, and one route or hour against another, where a
  consistent offset cancels out — and is not adequate for any absolute claim
  ("this street is above X dB"). No such claim is made anywhere in this
  project. See `firmware/src/pdm_noise_meter.h` for the full accounting.
- **Classification is rule-based, over sensor cross-products — not a
  trained model.** Two layers, both explicit thresholds:
  - The rolling per-`(cell, hour)` baseline (Stream Gatherers computing an
    EWMA-smoothed mean/stddev per pollutant) is the same mechanism slice 1
    shipped.
  - The event classifier (`backend/.../anomaly/AnomalyDetector.java`) now
    reads the *combination* of which channels rose — particulates alone,
    particulates with VOC, VOC alone, or noise alone — rather than reporting
    a bare severity. This is still a fixed decision table, not a model, and
    every verdict carries the z-scores it was decided from
    (`domain/Evidence.java`) specifically so the reasoning can be checked
    against the numbers on screen rather than taken on faith.
- **Only rises are flagged as anomalies.** Air measurably cleaner than a
  block's own history reads as `Normal`, not as an event — the detector
  keys off a positive z-score, not `|z|`.
- **Dose is a population-level estimate, not a personal one.**
  `backend/.../domain/Activity.java` weights concentration by a fixed
  ventilation multiplier per activity (walk/cycle/run), derived from
  published ratios of typical minute ventilation to resting ventilation.
  This is meant to make one session comparable to another — a run is
  reported as costing more than a walk through identical air, which is true
  in aggregate — not to estimate any individual's actual intake, which
  depends on fitness, pace, and physiology this project does not measure.
- **The precise GPS trace never leaves the phone.** The app snaps every
  reading to a `~110m` grid cell (`domain/GridCell.java`, mirrored in
  `app/.../location/GridCell.java`) before uploading; the backend's
  `DecodedReading` has no latitude/longitude field to receive one into. The
  session map in the app is drawn from the phone's own local trace
  (`app/.../session/TracePoint.java`), which is never uploaded and never
  leaves the device. A submission from an app build that predates this
  split (posting `lat`/`lon`) is rejected with an HTTP 400 rather than
  silently re-snapped server-side — see `ReadingControllerTest`.
  - The `~110m` cell size is a deliberate trade, not an arbitrary constant:
    finer cells give better route resolution but weaker anonymity; coarser
    cells give better anonymity but can't distinguish one street from the
    next. See the constant's own documentation in `GridCell.java`.
- **Contributor identity is a random per-install id, not a device or
  account identifier.** `app/.../session/ContributorId.java` generates a
  UUID stored in app-local preferences — not `ANDROID_ID`, not an
  advertising id, nothing derived from hardware or an account. It exists
  solely so the crowd layer can distinguish contributors and report
  `contributorCount` honestly; it correlates with nothing else, and
  clearing app data yields a new one with no other consequence.
- **Seeded contributors are always flagged, never presented as measured.**
  The crowd layer can be demonstrated with a single physical node by
  generating additional contributors (`ContributorSeeder`, off unless
  `streetsense.seed.enabled=true`). Every seeded contributor id carries the
  `seed:` prefix and every seeded reading sets the mock flag, so
  `CellSummary.seededContributorCount` and `mock` report this separately in
  every API response — the same discipline `FLAG_MOCK_DATA` applies to
  synthetic sensor readings, applied one level up to synthetic
  *contributors*.
- **A verdict is only as good as the evidence behind it, and every response
  says how much there is.** `CellStats.contributorCount` and
  `CellSummary.confidence()` distinguish "many readings from one person"
  from "corroborated by more than one contributor" — the crowd map and the
  live verdict both carry this, so a cell sampled once looks different from
  a cell twelve people agree on, rather than the same colour.
- **This is a distributed sensing network, not a mesh.** Each phone talks
  directly to the backend; nodes don't relay for each other. "Mesh" is not
  used to describe this system anywhere in the docs.
- **Mock data is always flagged.** `FLAG_MOCK_DATA` on the wire, a MOCK
  badge in the app UI, and a `mock` field in every backend API response —
  see `docs/ble-protocol.md`. Nothing in this system can present synthetic
  data as measured data without that flag being visible.
