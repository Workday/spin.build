#!/usr/bin/env sh
# Installs the locally-built spin binary to ~/.spin and symlinks it onto PATH.
#
# Usage:
#   ./install-dev.sh             # install from existing spin/target/spin-*-bin.zip
#   ./install-dev.sh --build     # rebuild first (skips self-hosting verification), then install
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPIN_HOME="${SPIN_HOME:-$HOME/.spin}"
LINK_DIR="${SPIN_LINK_DIR:-$HOME/.local/bin}"

# -----------------------------------------------------------
# Optional rebuild
# -----------------------------------------------------------
if [ "${1}" = "--build" ]; then
    echo "Building spin..."
    cd "$SCRIPT_DIR"
    ./mvnw package -DskipTests
fi

# -----------------------------------------------------------
# Find the zip
# -----------------------------------------------------------
ZIP=$(find "$SCRIPT_DIR/spin/target" -maxdepth 1 -name "spin-*-bin.zip" 2>/dev/null | head -1)

if [ -z "$ZIP" ]; then
    echo "Error: no spin binary found at spin/target/spin-*-bin.zip" >&2
    echo "Run:   ./install-dev.sh --build" >&2
    exit 1
fi

# -----------------------------------------------------------
# Extract to SPIN_HOME
# -----------------------------------------------------------
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"
EXTRACTED=$(find "$TMP" -maxdepth 1 -mindepth 1 -type d | head -1)

rm -rf "$SPIN_HOME"
mv "$EXTRACTED" "$SPIN_HOME"

# -----------------------------------------------------------
# Symlink spin.sh as 'spin' on PATH
# -----------------------------------------------------------
mkdir -p "$LINK_DIR"
ln -sf "$SPIN_HOME/bin/spin.sh" "$LINK_DIR/spin"
chmod +x "$SPIN_HOME/bin/spin.sh"

echo "Installed: $SPIN_HOME"
echo "Linked:    $LINK_DIR/spin -> $SPIN_HOME/bin/spin.sh"

if ! echo ":$PATH:" | grep -q ":$LINK_DIR:"; then
    echo ""
    echo "Note: $LINK_DIR is not in your PATH. Add this to your shell profile:"
    echo "  export PATH=\"$LINK_DIR:\$PATH\""
fi
