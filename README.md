# MedAboutYou — Android

An Android (Jetpack Compose / Material 3) port of the GTK4 desktop
**MedAboutYou**. It searches the **European Medicines Agency** and **Italian
AIFA** databases and shows full medicine records, and it is a **medication
manager**: recurring dose schedules, dose logging, reminder notifications,
adherence analytics and refill/stock forecasting.

| Field | Value |
|-------|-------|
| App ID | `com.uallsi.medaboutyou` |
| minSdk / target | 26 / 35 |
| Stack | Kotlin, Compose (Material 3), Room, WorkManager, OkHttp, kotlinx.serialization, Coroutines |

**Your data stays on your device.** Schedules, dose log, stock and the image
cache live in a local SQLite (Room) database; nothing about your medications is
uploaded. The only network use is the EMA dataset download, AIFA live search and
Wikipedia image lookup.

## Screens

- **Today** — adherence ring, time-grouped dose timeline (Morning/Afternoon/…),
  "take all", refill banner.
- **Search** — EMA (cached, offline) or AIFA (live) search → rich record with
  badges, classification, authorisation, stock, EPAR/RCP link.
- **Calendar** — month grid colour-coded by day state, daily agenda with
  take/skip, new-schedule form, active schedules.
- **Insights** — 7/30/90-day adherence, day streak, 30-day heatmap, per-medicine
  adherence, next-refill list.
- **Settings** — reminders, start-at-boot, about.

## Build

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # domain unit tests
```

See [CLAUDE.md](CLAUDE.md) for architecture and the porting rules that keep the
domain logic in parity with the immutable C++ reference.

## License

MIT © 2026 Umberto Allievi
