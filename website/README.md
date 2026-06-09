# MedAboutYou — website

A self-contained static landing page for the app, with a **Download latest APK**
button.

## Files

```
website/
  index.html          landing page (hero, features, screenshots, download, docs)
  style.css           brand-themed styles (teal #1A6F5B)
  images/             app screenshots (copied from docs/images)
  downloads/
    MedAboutYou-latest.apk   the package the Download button serves
    latest.json              version / size / date the page reads
  update-apk.sh       republish the latest build into downloads/
```

## Publish the latest build

```bash
./website/update-apk.sh           # copy the existing debug APK
./website/update-apk.sh --build   # assemble a fresh debug APK first, then copy
```

This refreshes `downloads/MedAboutYou-latest.apk` and writes `downloads/latest.json`
(`version`, `file`, `sizeMB`, `builtAt`). The page reads `latest.json` to show the
current version/size and point the Download button at the published file; if it
can't be fetched (e.g. opened via `file://`), it falls back to the static defaults
baked into `index.html`.

## Serve locally

```bash
python3 -m http.server -d website 8080   # then open http://localhost:8080
```

Serving over HTTP (rather than `file://`) lets the page read `latest.json` and
ensures the APK downloads with the right MIME type.

> The bundled package is the **debug** build. For public distribution, configure a
> release signing config and publish `assembleRelease` output instead.
