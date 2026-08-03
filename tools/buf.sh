#!/usr/bin/env bash
#
# Runs buf through Docker so no local install is needed.
#
#   tools/buf.sh lint                 - style and consistency
#   tools/buf.sh breaking             - compare against origin/main
#
# The breaking check is the one that matters: a Kafka topic holds messages written
# by builds that no longer exist, so a field renumbered today corrupts data already
# on disk.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUF_IMAGE="bufbuild/buf:latest"

run_buf() {
  docker run --rm \
    --volume "${REPO_ROOT}:/workspace" \
    --workdir /workspace \
    "${BUF_IMAGE}" "$@"
}

case "${1:-lint}" in
  lint)
    run_buf lint
    ;;
  breaking)
    run_buf breaking --against '.git#branch=main'
    ;;
  *)
    run_buf "$@"
    ;;
esac
