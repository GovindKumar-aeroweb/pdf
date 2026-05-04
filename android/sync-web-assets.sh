#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

rsync -a --delete \
  --exclude android \
  --exclude .git \
  --exclude node_modules \
  ../ app/src/main/assets/public/
