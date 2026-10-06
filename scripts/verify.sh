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

echo -e "${BOLD}${BLUE}▶ Running Rastrio verification...${RESET}"

if ./gradlew check \
    :androidApp:assembleDebug \
    :desktopApp:compileKotlin
then
    echo -e "${BOLD}${GREEN}✓ Rastrio verification passed.${RESET}"
else
    echo -e "${BOLD}${RED}✗ Rastrio verification failed.${RESET}" >&2
    exit 1
fi