# Java 26 and modern-Java feature map

**Java 26 runs on the backend JVM only.** Android's build toolchain caps
`sourceCompatibility`/`targetCompatibility` at Java 17 (see
[developer.android.com/build/jdks](https://developer.android.com/build/jdks)) —
so `/app` is Java 17 on purpose, and every claim below is about `/backend`.
Proof: `backend/build.gradle.kts`'s `JavaLanguageVersion.of(26)` toolchain
declaration, plus the committed `build.log` at the repo root
(`java --version` output from the actual toolchain used to build).

Each feature below gets one line of *why this project actually wants it* —
the point of this document is to make the case that these nine features are
a coherent design, not a checklist assembled for rubric points. If any one
of these ever reads as strained, the honest move is to drop it, not defend it.

## Java-26-specific (preview, requires `--enable-preview`)

Three of the JEPs below are previews unique to JDK 26 — these are the parts
of this project that couldn't have been written against JDK 24 or 25.

### JEP 525 — Structured Concurrency (6th preview)
**Where:** `backend/.../ingest/IngestService.java`
**Why:** One ingested reading fans out into persist / update-baseline /
anomaly-check as a single unit of work with one lifetime. If any subtask
fails, the whole ingest fails together instead of leaving the system
half-updated (e.g. a reading stored but no baseline update, silently
skewing future anomaly checks). `StructuredTaskScope.open()` + `fork()` +
`join()` is a direct, honest fit for "these three things either all
succeed or the request fails."

### JEP 530 — Primitive Types in Patterns, `instanceof`, and `switch` (4th preview)
**Where:** `backend/.../web/LenientJson.java`
**Why:** Ingest JSON fields legitimately arrive as different boxed types
across client versions — a whole-number JSON value deserializes as
`Integer`, a fractional one as `Double`, and some client code stringifies
numbers to dodge float-precision quirks. `case int i -> i; case double d ->
d; case String s -> Double.parseDouble(s);` replaces a chain of
`instanceof` checks with one exhaustive expression — this is the textbook
scenario the JEP itself cites.

### JEP 526 — Lazy Constants (2nd preview)
**Status: dropped for this slice.** The intended use (one-time grid
configuration init) didn't clear the bar of "would this project use it
without the contest" once written out — a plain static final field already
does the job with no meaningful startup-order subtlety to justify
`StableValue`. Rather than contrive a use, this is left out. See
`docs/future-work.md` if a genuine lazy-init need shows up later (e.g. an
expensive HTTP/3 reference-station client, see JEP 517 below).

## Modern Java, final in earlier releases (22–25)

These aren't Java-26-specific claims on their own — they're finalized
features this project uses because they're the right tool, and they give
the three preview features above a real domain to operate on rather than a
standalone demo.

### FFM API — `MemoryLayout` + `VarHandle` (final in 22)
**Where:** `backend/.../wire/PacketLayout.java`
**Why:** A packed little-endian C struct crossing into Java is the textbook
FFM use case. The `StructLayout` mirrors `firmware/.../packet.h`
field-for-field, so the two stay legible against each other with no
hand-computed byte offsets. One non-obvious wrinkle documented in the code:
a struct-level `withByteAlignment(1)` is rejected by the JDK
(`IllegalArgumentException: Invalid alignment constraint`) because a group
layout's alignment can't go below its strictest member's natural alignment
— each `JAVA_SHORT` field needs its own `.withByteAlignment(1)` override to
actually reproduce `#pragma pack(1)`.

### Scoped Values (JEP 506, final in 25)
**Where:** `backend/.../ingest/IngestContext.java`, bound in `IngestService`
**Why:** JEP 525 specifies that structured-concurrency subtasks inherit
`ScopedValue` bindings from the thread that forked them. Binding an
`IngestContext` (node id, correlation id) once at the top of `ingest()`
means all three forked subtasks read it without threading it through every
method signature. This is what makes the structured-concurrency usage read
as a deliberate pairing rather than two unrelated features used side by
side.

### Stream Gatherers (JEP 485, final in 24)
**Where:** `backend/.../baseline/RollingBaseline.java`,
`backend/.../baseline/EwmaGatherer.java`
**Why:** The rolling per-cell baseline is the feature the entire "AI" claim
rests on, and a sliding window over recent readings is literally what
`Gatherers.windowSliding(n)` is for. A custom `Gatherer` folds those windows
into one running EWMA-smoothed value, so a single noisy reading nudges the
baseline instead of replacing it outright.

### Sealed interface + records + exhaustive switch
**Where:** `backend/.../domain/Verdict.java`,
`backend/.../web/VerdictView.java`
**Why:** `Verdict` is `Normal | Elevated | Spike`, closed by `sealed
interface ... permits`. Switching over it needs no `default` branch —
adding a fourth verdict later becomes a compile error at every site that
must handle it (`VerdictView.of` is the clearest example). This also gives
the JEP 530 primitive-pattern switch a real, non-trivial domain model to
sit beside.

### Virtual threads
**Where:** `backend/src/main/resources/application.yml`
(`spring.threads.virtual.enabled: true`)
**Why:** One line, and it's what makes fork-per-subtask ingest cheap —
without it, three forked subtasks per request would mean three
platform-thread-pool threads per request under load.

## Deferred, not adopted (see docs/future-work.md)

**JEP 517 (HTTP/3 for the HTTP Client API, final in 26)** is the cheapest
way to add a second final-in-26 feature if there's time left after the core
path is done — pulling an official reference air-quality station over
HTTP/3 also answers this project's own "SEN54 is a proxy, not lab-grade"
caveat. Declined for this slice; revisit only if there's slack.

**JEP 524 (PEM Encodings)** for signed node identity and **JEP 529 (Vector
API)** for SIMD-accelerated re-baselining were both considered and declined
— see `docs/future-work.md` for why each is a real idea worth having on
record, just not one this slice needed.
