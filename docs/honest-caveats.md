# Honest caveats

What this project does and doesn't claim, stated plainly rather than left
to be discovered.

- **The SEN54 is a consumer PM/VOC *proxy*, not a lab-grade reference
  instrument.** Its readings are useful for relative comparison (this
  location vs. its own history) and not suitable for regulatory or medical
  claims. Slice 1 doesn't use the SEN54 at all yet — see
  `docs/future-work.md`.
- **"AI" means anomaly detection against a rolling per-location baseline**
  (Stream Gatherers computing an EWMA-smoothed mean/stddev, z-score
  thresholds for Normal/Elevated/Spike). It is not a trained model, and it
  makes no claim to be one.
- **No raw audio is ever stored or transmitted — dB(A) only.** The firmware
  computes a noise level from the PDM microphone and only that scalar
  leaves the device. This is stated prominently for a second reason: the
  contest rules prohibit "harmful surveillance technologies," and a
  phone-plus-microphone sensing network invites that reading. The dB-only
  architecture answers it before it's asked.
- **This is a distributed sensing network, not a mesh.** Each phone talks
  directly to the backend; nodes don't relay for each other. "Mesh" is not
  used to describe this system anywhere in the docs.
- **Mock data is always flagged.** `FLAG_MOCK_DATA` on the wire, a MOCK
  badge in the app UI, and a `mock` field in every backend API response —
  see `docs/ble-protocol.md`. Nothing in this system can present synthetic
  data as measured data without that flag being visible.
