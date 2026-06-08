# Automated analysis & testing

Every automated analysis applicable to this app (Kotlin / Compose / Room / Gradle,
no backend) and how to run it. JVM = no device needed; DEVICE = emulator/phone.

Run the whole JVM gate at once:

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew test lintDebug detekt koverHtmlReportDebug dependencyUpdates assembleDebug
```

## Results (last run)

| # | Analysis | Tool | Command | Type | Result |
|---|----------|------|---------|------|--------|
| 1 | Unit tests | JUnit | `:app:testDebugUnitTest` | JVM | **12 passed, 0 failed** |
| 2 | Instrumented tests | AndroidJUnit4 + Compose UI test + Room in-memory | `:app:connectedDebugAndroidTest` | DEVICE | **5 passed, 0 failed** |
| 3 | Android Lint (+ Slack Compose checks) | AGP lint + `compose-lint-checks` | `:app:lintDebug` | JVM | **0 errors, 53 warnings** |
| 4 | Kotlin static analysis | detekt 1.23.7 (+ formatting) | `:app:detekt` | JVM | runs; ~398 style findings (non-gating) |
| 5 | Code coverage | Kover 0.9.1 | `:app:koverHtmlReportDebug` | JVM | domain well-covered; ~7% line overall (UI excluded from JVM) |
| 6 | Compose compiler metrics | Compose compiler reports | `:app:assembleDebug` | JVM | 34/34 composables restartable **and** skippable |
| 7 | Dependency freshness | gradle-versions 0.51 | `:app:dependencyUpdates` | JVM | on latest **stable**; newer are alphas/RCs |
| 8 | APK size | `apkanalyzer` | `apkanalyzer apk file-size …` | JVM | 19.0 MB on disk, 18.5 MB download |
| 9 | Build (debug APK) | AGP/R8 | `:app:assembleDebug` | JVM | succeeds |

Reports land in `app/build/reports/` (`tests/`, `lint-results-debug.html`,
`detekt/detekt.html`, `kover/htmlDebug/index.html`),
`app/build/compose-reports/`, `app/build/dependencyUpdates/report.txt`, and
`app/build/outputs/androidTest-results/connected/`.

## Tests

**Unit (JVM, `src/test`)** — pure domain, no Android:
- `InsightsTest` (8) — occurrence generation (daily / hours / weeks / count-end),
  `doseIsDue`, adherence, streak, refill run-out forecast.
- `MedicineParsingTest` (4) — EMA JSON → `Medicine` mapping, Yes/No flags,
  ext-id fallback, vet ATC fallback, null/non-string safety.

**Instrumented (DEVICE, `src/androidTest`)** — real Room DB + Compose:
- `ScheduleRepositoryInstrumentedTest` (4) — create→snapshot, dose logging,
  single-occurrence cancel (override), soft-cancel a schedule.
- `TodayScreenUiTest` (1) — Today screen composes and shows its header.

## Other analyses available (not wired in)

- **Spotless/ktlint** (`com.diffplug.spotless`) — autoformat the detekt style nits
  (`spotlessApply`).
- **Paparazzi / Roborazzi / Compose Preview screenshot testing** — JVM screenshot
  diffing of composables.
- **Robolectric** — Android unit tests on the JVM (e.g. DAO tests without an
  emulator).
- **dependency-analysis (`buildHealth`)** — unused/misused dependencies.
- **OWASP dependency-check** — CVE scan of dependencies.
- **Macrobenchmark + Baseline Profiles** — startup/scroll timing (needs device).

## Running instrumented tests headlessly

```bash
$ANDROID_HOME/emulator/emulator -avd Pixel_API_36 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect &
$ANDROID_HOME/platform-tools/adb wait-for-device
# wait for sys.boot_completed == 1, then:
./gradlew :app:connectedDebugAndroidTest
$ANDROID_HOME/platform-tools/adb -s emulator-5554 emu kill
```
