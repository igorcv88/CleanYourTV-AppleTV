#!/usr/bin/env bash
# Prebuilt CleanYourTV + Scenic Glass installer. Run from the extracted release ZIP.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
: "${TV_HOST:?Set TV_HOST to the Philips/Android TV IP address first.}"
test -s ScenicGlassTV.apk || { echo 'Missing ScenicGlassTV.apk (extract the full ZIP).' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Install Python 3 first.' >&2; exit 1; }

if [ ! -x tools/venv/bin/python ] && [ ! -x tools/venv/Scripts/python.exe ]; then
  python3 -m venv tools/venv
fi
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
"$PY" -m pip -q install -r tools/requirements.txt

echo 'TV identity (read-only):'
"$PY" -I tools/tv.py --probe
fingerprint="$("$PY" -I tools/tv.py 'getprop ro.build.fingerprint' | tr -d '\r' | tail -n 1)"
test -n "$fingerprint" || { echo 'Cannot read firmware fingerprint; no changes made.' >&2; exit 1; }

profile="${DEBLOAT_LIST:-}"
if [ -z "$profile" ]; then
  matches=()
  for f in examples/*/debloat.txt; do
    test -f "$f" || continue
    reference="$(sed -n 's/^# Fingerprint: //p' "$f" | head -n 1 | tr -d '\r')"
    if [ -n "$reference" ] && [ "$reference" = "$fingerprint" ]; then
      matches+=("$f")
    fi
  done
  if [ "${#matches[@]}" -eq 1 ]; then
    profile="${matches[0]}"
    echo "Found one matching optimization profile: $profile"
  elif [ "${#matches[@]}" -gt 1 ]; then
    echo 'Multiple exact-firmware profiles exist; select one after reading its comments:'
    printf '  %s\n' "${matches[@]}"
  else
    echo 'No exact-firmware optimization profile found. Home installation can proceed without debloating.'
  fi
fi

if [ -n "$profile" ]; then
  test -f "$profile" || { echo "Profile not found: $profile" >&2; exit 1; }
  reference="$(sed -n 's/^# Fingerprint: //p' "$profile" | head -n 1 | tr -d '\r')"
  if [ -z "$reference" ] || [ "$reference" != "$fingerprint" ]; then
    echo 'Refusing to use a debloat profile from another or unidentified firmware.' >&2
    echo "TV: $fingerprint" >&2
    echo "Profile: ${reference:-unknown}" >&2
    exit 1
  fi
  echo "Pre-install recovery record for $profile:"
  "$PY" -I tools/recovery.py --save "$profile"
fi

# Also preserve the original Home state even when no debloat profile matches.
home="$("$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tr -d '\r' | tail -n 1)"
home_pkg="${home%%/*}"
if [[ "$home_pkg" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]]; then
  printf '%s\n' "$home_pkg" > recovery-baseline.txt
  "$PY" -I tools/recovery.py --save recovery-baseline.txt
else
  echo "Cannot pre-record Home '$home'; installer will retain the stock launcher on failure."
fi

echo 'Installing signed APK, optimizing its compilation, and switching Home safely...'
PREBUILT_APK="$ROOT/ScenicGlassTV.apk" "$ROOT/launcher/build-and-install.sh"

if [ -n "$profile" ]; then
  echo
  echo "A matching profile can disable preinstalled apps: $profile"
  echo 'Read its comments carefully; Chromecast, tuner, HDMI, Ambilight and other features must be preserved.'
  read -r -p 'Type OPTIMIZAR to apply this debloat profile, or press Enter to skip: ' answer
  if [ "$answer" = OPTIMIZAR ]; then
    "$ROOT/tools/disable.sh" "$profile"
    echo "Undo optimizations using: TV_HOST=$TV_HOST tools/restore.sh $profile"
  else
    echo 'Debloat skipped; Home was installed.'
  fi
fi
echo 'Keep this extracted folder and the recovery records under ~/.cleanyourtv for rollback.'
echo 'After validating the TV, turn ADB debugging OFF and reboot it.'
