# AGENTS.md — SiteSweep

## What this is

An offline-first Android app for structural crack inspection. The user holds the phone up and
walks along a wall. The camera stream runs continuous on-device inference. When distress is
detected the phone vibrates, speaks the severity, and auto-captures a geotagged frame. Inspections
are grouped into sessions. Returning to a previously inspected location surfaces prior captures so
the engineer can see whether a crack is stable or widening.

Built for the iQOO Hackathon 2026 (Hyderabad, 26-27 Sept). It is judged on a live demo held by a
judge, on a loaner iQOO 15, in airplane mode.

## Non-negotiable constraints

These are scoring criteria, not preferences. Never violate them.

1. **No network calls on the inspection path.** Detection, severity, storage and revisit lookup
   must work with the device in airplane mode. If a feature needs network, it does not ship.
2. **Inference runs on-device via LiteRT (TFLite) with NNAPI/GPU delegate.** Never substitute a
   cloud vision API, not even as a fallback or a stub. Cloud inference is an automatic loss of a
   scoring category.
3. **No shutter button on the sweep screen.** Capture is automatic. If you find yourself adding a
   capture button, you have misunderstood the product.
4. **Feedback is haptic and audio first.** The user is on a ladder looking at a wall, not at the
   screen.
5. **Demo reliability beats feature count.** A feature that works 90% of the time is worse than
   not having it. If something is flaky, put it behind a debug flag, not in the main flow.

## Visual design — do not let this look AI-generated

Judges have seen a hundred hackathon apps built by Claude or Cursor and can spot the tells in
about two seconds. None of the following are allowed anywhere in the app, no exceptions:

- Harsh gradients as backgrounds or on buttons. Flat colour or a single subtle tonal shift, nothing
  loud.
- Lucide or any generic AI-default icon set used untouched. Pick one icon style and mean it, or use
  Material Symbols and restyle the ones that matter.
- Pure white (`#FFFFFF`) screen backgrounds. Use a warm or cool off-white, or the concrete/slate
  tones already in the deck palette (`FFFFFF` for cards is fine, the screen canvas is not).
- Rainbow or neon colour schemes, and generic purple-to-black gradients. Stay inside the deck
  palette: ink, slate, safety orange, and the three severity colours. Nothing else.
- Drop shadows on every card. Use them sparingly, on one elevated surface at most per screen, or
  skip them and rely on tonal separation instead.
- Three feature cards in a row, bento grids, or dot-grid backgrounds. This is a working tool with
  one primary screen, not a landing page. Don't manufacture a grid that isn't load-bearing.
- Emojis anywhere in the UI. Use icons or text.
- Liquid glass / frosted blur panels. Not on this app.
- Em dashes in any UI copy, labels, or error strings.
- Inter, Geist, or Space Grotesk as the default and only typeface choice made without thinking
  about it. Pick a typeface on purpose and justify it in one line if asked, or use the Android
  system font (Roboto) deliberately rather than swapping in a trendy default.
- A coloured stripe down the left edge of cards or the screen. Banned outright, this is the single
  most obvious tell.
- Fake testimonials, fake ratings, fake user counts anywhere, including placeholder/demo content.
  If you need example data, label it as example data.
- Checkmark bullet lists for feature lists. Use plain text or a different visual treatment.
- Sparkle icons, radial glow orbs, or animated arrows as decoration. If nothing is actually loading
  or pointing at something, don't animate it.
- Soft, uniform corner radius on every single element as a default. Vary it deliberately or commit
  to sharp corners, matching the deck's own roundRect-with-purpose usage, not a blanket 12dp on
  everything.
- Skeleton loaders standing in for content that loads in under 200ms. If a screen is instant, show
  it instant.

Reference the SiteSweep pitch deck palette (ink `1C1C1E`, slate `3A3F44`, safety orange `FF6A13`,
severity colours green/amber/red) for every screen. If a screen doesn't need a colour, leave it the
base ink/paper/slate, don't invent a new one to fill space.

## Stack

- Kotlin, min SDK 26, target SDK 35
- Jetpack Compose for UI
- CameraX (`ImageAnalysis` use case) for the frame stream
- LiteRT (TFLite) with NNAPI delegate, GPU delegate fallback, CPU last resort
- Room for local persistence
- `FusedLocationProviderClient` for geotagging, last-known-location cached so it never blocks
- Android `SpeechRecognizer` (offline mode) for voice notes, `TextToSpeech` for severity callouts
- No Firebase, no Retrofit, no analytics SDKs, no cloud dependencies of any kind

## Architecture

```
ui/            Compose screens: SweepScreen, SessionListScreen, SessionDetailScreen, RevisitScreen
detection/     CrackDetector (LiteRT wrapper), FrameThrottler, SeverityClassifier
capture/       AutoCapture, GeoTagger, FrameStore (writes JPEG to app-internal storage)
data/          Room entities, DAOs, repository
report/        Session export to JSON + markdown for the Office Kit handoff
feedback/      HapticController, VoiceAnnouncer
```

Single-activity. ViewModel per screen. Repository is the only thing that touches Room.

## Detection pipeline rules

- Throttle `ImageAnalysis` to ~5 fps. Do not run inference on every frame. Battery and thermals
  matter over a 30-hour event and the demo phone must not throttle mid-pitch.
- Use `STRATEGY_KEEP_ONLY_LATEST` backpressure. Dropped frames are correct behaviour.
- Model output is a binary class (hairline / structural) plus a confidence score. Do not claim
  millimetre width estimation anywhere in the UI. Severity bands only.
- Severity bands: `STABLE`, `MONITOR`, `STRUCTURAL`. Map confidence to bands in one place
  (`SeverityClassifier`) so thresholds are tunable during the event without touching the UI.
- Debounce auto-capture: after a capture, suppress further captures for 3 seconds and require the
  detection to clear before re-arming. Otherwise one crack produces forty photos.

## Data model

```
Session(id, startedAt, endedAt, label)
Capture(id, sessionId, imagePath, lat, lng, timestamp, severity, confidence, locationKey)
VoiceNote(id, sessionId, captureId?, transcript, timestamp)
```

`locationKey` is a geohash truncated to roughly 10m precision. Revisit lookup is a query on
`locationKey`, not a distance calculation over every row.

## Seed data

Ship a `DemoSeeder` that inserts three historical captures at a fixed `locationKey` with dates
three weeks apart and increasing severity. This must be real rows in Room, reachable through the
same code path as live captures. It is not a mock screen. During the demo, the judge walks to the
seeded crack poster and the revisit view populates from the database exactly as it would in the
field.

Gate the seeder behind a settings toggle so it can be disabled and re-enabled between demo runs.

## Office Kit report handoff

Session export writes a self-contained markdown file plus the captured JPEGs to shared storage.
The laptop side picks it up via Office Kit and an agent turns it into a structural distress
report. Keep the export format dumb and stable. It is a handoff boundary, not a place to be clever.

## What you must never do

- Add a network permission or an HTTP client to the manifest.
- Replace on-device inference with an API call, even temporarily, even to unblock UI work. Use a
  fake local detector that returns fixed results instead.
- Add a photo capture button to the sweep screen.
- Store captures anywhere the user has to grant broad storage permission for. App-internal only,
  except the export directory.
- Introduce a splash screen, onboarding flow, login, or settings menu beyond the demo toggle.
  Every one of those is time stolen from the demo path.

## Definition of done for the demo

Phone in airplane mode. Judge holds it. Opens app, taps Start Sweep, pans across a printed crack
poster. Phone vibrates, speaks the severity, a capture appears in the strip. Judge pans to the
seeded location, taps the capture, sees three prior readings with dates and widening severity.
Judge taps Export. File lands. That entire sequence must run five times in a row without a crash,
a permission dialog, or a visible stall.
