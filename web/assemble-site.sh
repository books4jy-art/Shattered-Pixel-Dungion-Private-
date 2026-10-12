#!/bin/bash
# Assembles a static site for the web build in web/build/site, ready to upload to any static host.
# Run './gradlew -PwebOnly :web:gdx_teavm_web_js_build' first.
set -e
cd "$(dirname "$0")"

DIST=build/dist/js/webapp
SITE=build/site

if [ ! -f "$DIST/app.js" ]; then
	echo "No web build found, run ./gradlew -PwebOnly :web:gdx_teavm_web_js_build first" >&2
	exit 1
fi

rm -rf "$SITE"
mkdir -p "$SITE"
cp -r "$DIST/app.js" "$DIST/assets" "$DIST/scripts" "$SITE/"

#escape the unicode replacement character, as some upload tools reject it in text files.
# it only appears inside JS string literals, where the escape sequence is equivalent
perl -CSD -pi -e 's/\x{FFFD}/\\uFFFD/g' "$SITE/app.js"

#reuse the version arguments from the generated page
MAIN_ARGS=$(grep -o "main(\[[^]]*\])" "$DIST/index.html" | sed -e 's/^main(//' -e 's/)$//')
sed "s|__MAIN_ARGS__|$MAIN_ARGS|" site/index.html > "$SITE/index.html"

echo "Site assembled in web/$SITE"
