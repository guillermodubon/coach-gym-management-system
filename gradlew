#!/bin/sh
set -eu

TASK_REPOSITORY_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$TASK_REPOSITORY_DIR/backend"
exec sh ./gradlew "$@"
