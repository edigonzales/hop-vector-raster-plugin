#!/usr/bin/env sh
set -eu

if ! command -v Xvfb >/dev/null 2>&1; then
  echo "Xvfb is required for the Hop dialog smoke tests" >&2
  exit 1
fi

display_file="$(mktemp)"
log_file="$(mktemp "${TMPDIR:-/tmp}/hop-vector-raster-xvfb.XXXXXX.log")"
xvfb_pid=""

cleanup() {
  if [ -n "$xvfb_pid" ]; then
    kill "$xvfb_pid" 2>/dev/null || true
    wait "$xvfb_pid" 2>/dev/null || true
  fi
  rm -f "$display_file" "$log_file"
}
trap cleanup EXIT INT TERM

Xvfb -displayfd 1 -screen 0 1280x1024x24 -ac >"$display_file" 2>"$log_file" &
xvfb_pid=$!

display=""
attempt=0
while [ "$attempt" -lt 50 ]; do
  if [ -s "$display_file" ]; then
    display="$(sed -n '1p' "$display_file" | tr -d '[:space:]')"
    break
  fi
  if ! kill -0 "$xvfb_pid" 2>/dev/null; then
    cat "$log_file" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 0.1
done

if [ -z "$display" ]; then
  cat "$log_file" >&2
  echo "Xvfb did not publish a display number" >&2
  exit 1
fi

DISPLAY=":$display" python3 scripts/check-vector-dialogs.py
DISPLAY=":$display" python3 scripts/check-raster-dialogs.py
