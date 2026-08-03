# Future work

Deliberately out of scope for slice 1. Listed here rather than left
unstated, so the scope cuts read as decisions, not gaps.

## Hardware / firmware
- **Swap in the real SEN54** — implement `Sen54SensorSource` (currently a
  stub) against the I2C driver; nothing else changes since it produces the
  same `Reading` the mock source does.
- **Motion tagging via the LIS3DH** — the onboard accelerometer (not a full
  IMU — see the root README) could flag readings taken while walking vs.
  stationary, which matters for noise readings especially.
- **Offline buffering** — the app currently drops a reading if the upload
  fails; no retry queue. Fine for a demo on one Wi-Fi network, not fine for
  a phone that walks out of range mid-route.
- **LED-matrix ambient feedback** — the product this board was designed for
  is an LED glasses driver; using the LEDs to show a live air-quality
  color code was considered and cut to keep slice 1's scope to sensing +
  upload only.

## Backend
- **Postgres/PostGIS** — `ReadingRepository` is already an interface for
  exactly this; the in-memory implementation is a bounded ring buffer that
  will not survive a restart or scale past a single demo.
- **Real grid sophistication** — `GridCell` is a crude fixed-size lat/lon
  bucket. PostGIS-backed spatial binning (tuned cell size, actual distance
  queries) replaces it without an API change.
- **True mesh networking** — this is a distributed sensing network (many
  independent phone-to-backend uploads), not a mesh (nodes don't relay for
  each other). Worth exploring if node density ever makes direct backend
  connectivity unreliable.
- **HTTP/3 reference-station lookup (JEP 517, final in 26)** — pulling an
  official reference air-quality station's readings over the new HTTP/3
  client API would both add a second final-in-26 feature and answer this
  project's own "SEN54 is a proxy, not lab-grade" caveat. The cheapest
  addition if there's time left — see `docs/java26-jeps.md`.
- **AOT object caching (JEP 516, final in 26)** for backend startup-time
  demo numbers — nice for a "look how fast this starts" beat in the demo
  video, not load-bearing for anything functional.
- **PEM node identity (JEP 524)** — signed uploads so a reading is
  attributable to a specific physical node, not just whatever `nodeId`
  string a client claims. Real security value in a multi-node deployment;
  not needed for one demo node.
- **Vector API re-baselining (JEP 529)** — SIMD-accelerated baseline
  recomputation across many cells at once. Only matters at a scale slice 1
  never reaches (a handful of cells, one node).
