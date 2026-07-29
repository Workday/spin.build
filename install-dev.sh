#!/usr/bin/env sh
# Installs a spin binary and symlinks it onto PATH.
#
# Usage:
#   ./install-dev.sh             # install from existing spin/target/spin-*-bin.zip (as 'dev')
#   ./install-dev.sh --build     # rebuild first (skips self-hosting verification), then install as 'dev'
#   ./install-dev.sh 0.3.0       # download (or reuse cached) release v0.3.0 from GitHub and switch to it
#   ./install-dev.sh latest      # download (or reuse cached) latest GitHub release and switch to it
#   ./install-dev.sh --list      # list cached versions and show which one is active
#
# Every version ever installed is cached under $SPIN_HOME/versions/<tag>/, so switching
# back to one you've already used is a symlink flip - no re-download or re-extract.
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPIN_HOME="${SPIN_HOME:-$HOME/.spin}"
VERSIONS_DIR="$SPIN_HOME/versions"
CURRENT_LINK="$SPIN_HOME/current"
LINK_DIR="${SPIN_LINK_DIR:-$HOME/.local/bin}"
REPO="${SPIN_REPO:-Workday/spin.build}"

case "$(uname -s)" in Darwin) HOST_OS=mac ;; Linux) HOST_OS=linux ;; *) HOST_OS=other ;; esac
case "$(uname -m)" in arm64|aarch64) HOST_ARCH=aarch64 ;; x86_64|amd64) HOST_ARCH=x86_64 ;; *) HOST_ARCH=other ;; esac

# -----------------------------------------------------------
# Point $CURRENT_LINK and PATH at an already-cached version dir
# -----------------------------------------------------------
activate() {
    mkdir -p "$LINK_DIR"
    ln -sfn "$VERSIONS_DIR/$1" "$CURRENT_LINK"
    ln -sf "$CURRENT_LINK/bin/spin.sh" "$LINK_DIR/spin"
    chmod +x "$CURRENT_LINK/bin/spin.sh"

    echo "Active: $1"
    echo "Linked: $LINK_DIR/spin -> $CURRENT_LINK/bin/spin.sh"

    if ! echo ":$PATH:" | grep -q ":$LINK_DIR:"; then
        echo ""
        echo "Note: $LINK_DIR is not in your PATH. Add this to your shell profile:"
        echo "  export PATH=\"$LINK_DIR:\$PATH\""
    fi
}

# -----------------------------------------------------------
# One-time migration from the pre-versioning flat layout, where
# $SPIN_HOME was itself the extracted image (bin/, conf/, lib/, ...)
# -----------------------------------------------------------
if [ ! -e "$VERSIONS_DIR" ] && [ ! -L "$SPIN_HOME" ] && [ -e "$SPIN_HOME/bin/spin.sh" ]; then
    MIGRATE_TAG=$("$SPIN_HOME/bin/spin.sh" --version 2>/dev/null | tr -d '[:space:]')
    if [ -z "$MIGRATE_TAG" ]; then
        echo "Error: found an existing flat install at $SPIN_HOME but couldn't determine its version" >&2
        echo "       (spin --version produced no output). Not migrating - move it aside manually," >&2
        echo "       e.g.: mkdir -p $VERSIONS_DIR && mv $SPIN_HOME $VERSIONS_DIR/<tag>" >&2
        exit 1
    fi
    case "$MIGRATE_TAG" in v*) ;; *) MIGRATE_TAG="v$MIGRATE_TAG" ;; esac
    echo "Migrating existing $SPIN_HOME install (detected $MIGRATE_TAG) into the versioned cache..."
    MIGRATE_TMP=$(mktemp -d)
    rmdir "$MIGRATE_TMP"
    mv "$SPIN_HOME" "$MIGRATE_TMP"
    mkdir -p "$VERSIONS_DIR"
    mv "$MIGRATE_TMP" "$VERSIONS_DIR/$MIGRATE_TAG"
    activate "$MIGRATE_TAG"
fi

if [ "${1}" = "--list" ]; then
    ACTIVE=""
    [ -L "$CURRENT_LINK" ] && ACTIVE=$(basename "$(readlink "$CURRENT_LINK")")
    for d in "$VERSIONS_DIR"/*/; do
        [ -d "$d" ] || continue
        v=$(basename "$d")
        if [ "$v" = "$ACTIVE" ]; then echo "* $v"; else echo "  $v"; fi
    done
    exit 0
fi

# -----------------------------------------------------------
# Download (or reuse a cached) release from GitHub
# -----------------------------------------------------------
case "${1}" in
    ""|--build) ;;
    *)
        VERSION="${1#v}"
        TAG="v${VERSION}"

        if [ "$VERSION" = "latest" ]; then
            if ! command -v gh >/dev/null 2>&1; then
                echo "Error: the 'gh' CLI is required to resolve 'latest' (brew install gh / apt install gh)" >&2
                exit 1
            fi
            TAG=$(gh release view --repo "$REPO" --json tagName --jq .tagName) || {
                echo "Error: failed to resolve the latest release for $REPO" >&2
                exit 1
            }
        fi

        if [ -d "$VERSIONS_DIR/$TAG" ]; then
            echo "Using cached $TAG (already downloaded)"
            activate "$TAG"
            exit 0
        fi

        if ! command -v gh >/dev/null 2>&1; then
            echo "Error: the 'gh' CLI is required to download a release (brew install gh / apt install gh)" >&2
            exit 1
        fi

        # release assets use a different os/arch naming scheme than the local build's image dirs
        case "$HOST_OS" in mac) ASSET_OS=macos ;; *) ASSET_OS="$HOST_OS" ;; esac
        case "$HOST_ARCH" in x86_64) ASSET_ARCH=amd64 ;; aarch64) ASSET_ARCH=arm64 ;; *) ASSET_ARCH="$HOST_ARCH" ;; esac
        ASSET="spin-${ASSET_OS}-${ASSET_ARCH}.tar.gz"

        echo "Downloading $ASSET from $REPO@$TAG..."
        TMP=$(mktemp -d)
        trap 'rm -rf "$TMP"' EXIT

        if ! gh release download "$TAG" --repo "$REPO" --dir "$TMP" --pattern "$ASSET" --pattern "SHA256SUMS"; then
            echo "Error: failed to download $ASSET for release $TAG from $REPO" >&2
            exit 1
        fi

        if [ -f "$TMP/SHA256SUMS" ]; then
            if command -v sha256sum >/dev/null 2>&1; then
                CHECKSUM_CMD="sha256sum -c -"
            elif command -v shasum >/dev/null 2>&1; then
                CHECKSUM_CMD="shasum -a 256 -c -"
            else
                echo "Error: neither sha256sum nor shasum found; cannot verify checksum for $ASSET" >&2
                exit 1
            fi
            (cd "$TMP" && grep " $ASSET\$" SHA256SUMS | $CHECKSUM_CMD) || {
                echo "Error: checksum verification failed for $ASSET" >&2
                exit 1
            }
        fi

        tar xzf "$TMP/$ASSET" -C "$TMP"
        HOST_IMAGE=$(find "$TMP" -maxdepth 1 -mindepth 1 -type d | head -1)

        mkdir -p "$VERSIONS_DIR"
        rm -rf "$VERSIONS_DIR/$TAG"
        mv "$HOST_IMAGE" "$VERSIONS_DIR/$TAG"

        activate "$TAG"
        exit 0
        ;;
esac

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
# Extract and cache as the 'dev' version
# -----------------------------------------------------------
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"
EXTRACTED=$(find "$TMP" -maxdepth 1 -mindepth 1 -type d | head -1)

# the zip bundles one spin-<os>-<arch>/ image per target platform; only this host's own is
# installed locally - the others exist for distributing to those platforms, not for running here
HOST_IMAGE="$EXTRACTED/spin-${HOST_OS}-${HOST_ARCH}"

if [ ! -d "$HOST_IMAGE" ]; then
    echo "Error: no spin-${HOST_OS}-${HOST_ARCH} image found in $ZIP" >&2
    exit 1
fi

mkdir -p "$VERSIONS_DIR"
rm -rf "$VERSIONS_DIR/dev"
mv "$HOST_IMAGE" "$VERSIONS_DIR/dev"

activate "dev"
