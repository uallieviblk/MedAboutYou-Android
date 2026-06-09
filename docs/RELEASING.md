# Releasing MedAboutYou

Releases are produced by the **GitHub Actions** workflow at
[`.github/workflows/release.yml`](../.github/workflows/release.yml).

## Cut a release

Tag a commit with a `v*` version tag and push the tag:

```bash
git tag v0.3.0
git push origin v0.3.0
```

The workflow then:
1. builds the release APK (`./gradlew :app:assembleRelease`),
2. names it `MedAboutYou-v0.3.0.apk`,
3. creates a **GitHub Release** for the tag with auto‑generated notes and the APK
   attached.

Watch progress under the repository's **Actions** tab; the published Release
appears under **Releases**. (A manual *Run workflow* / `workflow_dispatch` build
doesn't create a Release — it just uploads the APK as a workflow artifact.)

Keep `versionName`/`versionCode` in `app/build.gradle.kts` in step with the tag
before tagging.

## Signing

Out of the box the release APK is **debug‑signed** (installable for sideloading
and testing) — no setup required.

For a properly signed release, add four repository **secrets** (Settings →
Secrets and variables → Actions). The build picks them up automatically; without
them it falls back to the debug key.

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | the keystore file, base64‑encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create a keystore and encode it:

```bash
keytool -genkeypair -v -keystore release.keystore -alias medaboutyou \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore     # paste output into KEYSTORE_BASE64
```

The signing config in `app/build.gradle.kts` reads these via the `KEYSTORE_FILE`
(written by the workflow), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
environment variables. **Never commit the keystore.**

> Reproducible publishing of the same artifact requires a stable keystore. If you
> start unsigned/debug and later add a real keystore, the app signature changes —
> users must uninstall the debug build before installing the signed one.

## Website download

[`website/update-apk.sh`](../website/update-apk.sh) refreshes the local
`website/downloads/` copy used by the landing page. For public distribution,
point the site's download button at the latest GitHub Release asset instead.
