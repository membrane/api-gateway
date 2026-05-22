#!/usr/bin/env sh
set -eu

FRUIT_BASE="${FRUIT_BASE:-http://localhost:3000}"
APIBIN_BASE="${APIBIN_BASE:-http://localhost:3001}"
ROUNDS="${1:-20}"

request() {
  method="$1"
  url="$2"
  body="${3:-}"

  printf "%s %s" "$method" "$url"

  if [ -n "$body" ]; then
    curl -sS -o /dev/null -w " -> %{http_code}\n" \
      -X "$method" "$url" \
      -H "Content-Type: text/plain" \
      --data "$body" || true
  else
    curl -sS -o /dev/null -w " -> %{http_code}\n" \
      -X "$method" "$url" || true
  fi
}

i=1
while [ "$i" -le "$ROUNDS" ]; do
  request GET  "$FRUIT_BASE/products/"
  request GET  "$FRUIT_BASE/products/4"
  request GET  "$FRUIT_BASE/categories/"

  request GET  "$APIBIN_BASE/analyze?round=$i&delay=50"
  request GET  "$APIBIN_BASE/faker?profile=order&count=2&locale=de-DE&seed=$i"
  request POST "$APIBIN_BASE/echo" "hello apibin round $i"

  i=$((i + 1))
done