# Getting Started with the Web (Browser) Build

The `:web` module compiles the game to JavaScript with [gdx-teavm](https://github.com/xpenatan/gdx-teavm), so it can be played in a browser.

## Building

Requires JDK 17 or newer. The Android and iOS modules aren't needed, so pass `-PwebOnly` to skip them (no Android SDK required):

```
./gradlew -PwebOnly :web:gdx_teavm_web_js_build
```

The output is written to `web/build/dist/js/webapp`. To try it locally, serve that folder with any static web server, e.g. `python3 -m http.server -d web/build/dist/js/webapp 8080`, and open http://localhost:8080.

Add `-PwebDebug` for readable (non-minified) JavaScript when debugging in the browser.

## Deploying

`web/assemble-site.sh` copies the build into `web/build/site` with a minimal full-window page (`web/site/index.html`). Upload the contents of that folder to any static host.

## Saves

Saves and settings are stored in the browser (IndexedDB and localStorage), so they belong to that browser and site address. Clearing site data deletes them. The game saves whenever the hero finishes acting, so refreshing or closing the page keeps your progress.

When the page is hosted on claude.ai with the `db` and `user` capabilities, `web/site/cloud-saves.js` also mirrors every save file and the settings to the viewer's private area of the artifact's database. If the page opens with empty browser storage, they are copied back before the game starts. When the page runs anywhere else, this does nothing.

A backup of each game is also taken whenever you arrive on a floor or continue a game. If the main save can't be read, the backup is restored automatically and the game says so in the log.

## Web-specific differences

- Actors are processed on the render thread rather than a separate thread (`Actor.processSingleThreaded`), as the browser runtime has no thread support here.
- Saves are written uncompressed, as TeaVM's deflater rejects a harmless zlib status.
- Controllers, vibration and fullscreen toggling aren't supported.
- Crashes are shown on the page with the error details, instead of only in the browser console.
