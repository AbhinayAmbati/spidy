#!/usr/bin/env bash
# Scans a CycloneDX SBOM for dependencies with known vulnerabilities (OSV.dev database) and
# exits non-zero if it finds any. No account or secret is needed; the scan sends only package
# names and versions to api.osv.dev.
#
#   bash tools/osv_scan.sh app/build/reports/cyclonedx-direct/bom.json
#
# The scanner binary is downloaded from the official GitHub release and checked against a
# SHA-256 pinned here, so a tampered download fails instead of running. To upgrade, change
# VERSION and SHA256 together (checksums: .../releases/download/<ver>/osv-scanner_SHA256SUMS).
#
# A finding that is a false positive or cannot be fixed yet is acknowledged in osv-scanner.toml
# at the repository root (run from there; picked up automatically) ([[IgnoredVulns]] with an id, a reason and an ignoreUntil date).
set -euo pipefail

VERSION="v2.7.0"
SHA256="160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b" # osv-scanner_linux_amd64

SBOM="${1:?usage: osv_scan.sh <bom.json>}"
test -s "$SBOM" || { echo "::error::SBOM $SBOM is missing or empty"; exit 1; }

BIN_DIR="${RUNNER_TEMP:-$(mktemp -d)}"
BIN="$BIN_DIR/osv-scanner"
curl --fail --silent --show-error --location --retry 3 \
  --output "$BIN" \
  "https://github.com/google/osv-scanner/releases/download/${VERSION}/osv-scanner_linux_amd64"
echo "${SHA256}  ${BIN}" | sha256sum --check --strict -
chmod +x "$BIN"

# `scan source -L <file>` scans exactly this file. Exit code 1 means vulnerabilities were found.
# The file must be named bom.json (or *.cdx.json) so it is recognised as CycloneDX.
ARGS=(scan source -L "$SBOM")
if [ -f osv-scanner.toml ]; then ARGS+=(--config osv-scanner.toml); fi
"$BIN" "${ARGS[@]}"
