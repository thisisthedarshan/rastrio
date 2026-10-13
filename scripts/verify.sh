#!/usr/bin/env bash
set -euo pipefail

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
    BOLD='\033[1m'
    BLUE='\033[34m'
    GREEN='\033[32m'
    RED='\033[31m'
    RESET='\033[0m'
else
    BOLD=''
    BLUE=''
    GREEN=''
    RED=''
    RESET=''
fi

echo -e "${BOLD}${BLUE}▶ Running RastrIO verification...${RESET}"

python3 scripts/generate_android_branding.py --check
python3 scripts/test_android_branding.py

if ./gradlew check \
    :androidApp:assembleDebug \
    :desktopApp:compileKotlin
then
    python3 scripts/test_android_branding.py --apk androidApp/build/outputs/apk/debug/androidApp-debug.apk
    echo -e "${BOLD}${GREEN}✓ RastrIO verification passed.${RESET}"
else
    echo -e "${BOLD}${RED}✗ RastrIO verification failed.${RESET}" >&2
    exit 1
fi