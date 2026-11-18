#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export PYTHONIOENCODING=utf-8
exec "${PYTHON:-python}" scripts/auth-test.py
