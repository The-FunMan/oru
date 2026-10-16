#!/usr/bin/env bash
# Checks the static project page in site/ before the Pages workflow deploys it.
# Fails on external resources, external scripts, banned words, em dashes and oversized files.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
site="$root/site"
failed=0

fail() {
  echo "FAIL: $1"
  failed=1
}

if [ ! -d "$site" ]; then
  echo "FAIL: $site does not exist"
  exit 1
fi

# Text files the browser loads as resources or reads as page content.
mapfile -d '' resource_files < <(find "$site" -type f \( -name '*.html' -o -name '*.css' -o -name '*.svg' -o -name '*.js' \) -print0)
mapfile -d '' text_files < <(find "$site" -type f \( -name '*.html' -o -name '*.css' -o -name '*.svg' -o -name '*.js' -o -name '*.txt' -o -name '*.md' \) -print0)

# 1. Absolute http(s) URLs and protocol-relative resource URLs. The only allowed
#    absolute URLs are href values on <a> elements and the SVG namespace identifier.
for f in "${resource_files[@]}"; do
  hits="$(sed -E \
      -e 's# xmlns="http://www\.w3\.org/2000/svg"##g' \
      -e 's#(<a [^>]*)href="https?://[^"]*"#\1#g' \
      "$f" \
    | grep -nE 'https?://|(src|href|srcset)[[:space:]]*=[[:space:]]*["'\'']?//|url\([[:space:]]*["'\'']?//' || true)"
  if [ -n "$hits" ]; then
    fail "external resource URL in ${f#"$root"/}: $hits"
  fi
done

# 2. Scripts with a src attribute.
for f in "${resource_files[@]}"; do
  if grep -nE '<script[^>]*[[:space:]]src[[:space:]]*=' "$f" >/dev/null 2>&1; then
    fail "script with src in ${f#"$root"/}"
  fi
done

# 3. Banned words.
for f in "${text_files[@]}"; do
  if grep -niE 'youtube|reddit' "$f" >/dev/null 2>&1; then
    fail "banned word in ${f#"$root"/}"
  fi
done

# 4. Em dashes (U+2014, UTF-8 bytes E2 80 94).
for f in "${text_files[@]}"; do
  if LC_ALL=C grep -q $'\xe2\x80\x94' "$f"; then
    fail "em dash in ${f#"$root"/}"
  fi
done

# 5. Files over 500 KB, except the wordmark font.
while IFS= read -r -d '' f; do
  fail "file over 500 KB: ${f#"$root"/}"
done < <(find "$site" -type f ! -name '*.ttf' -size +500000c -print0)

if [ "$failed" -ne 0 ]; then
  exit 1
fi
echo "OK: site/ passed all checks (${#text_files[@]} text files scanned)"
