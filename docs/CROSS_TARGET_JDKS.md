# Staging Extra JDKs for Cross-Target `jlink`

spin's `jlink` task infers what to build entirely from what JDKs it can find: for every distinct
(operating system, architecture) pair among the JDKs discovered on the machine, it produces a
separate runtime image. No flags, no config — stage a JDK for a foreign platform and `spin clean
jlink` (or the self-hosting build) picks it up automatically alongside your host's own image.

This mirrors the three targets our GitHub Actions release matrix already builds:

| CI `os`             | CI `artifact`        | JDK platform    |
|----------------------|-----------------------|-----------------|
| `ubuntu-latest`       | `spin-linux-amd64`    | linux / x86_64  |
| `ubuntu-24.04-arm`    | `spin-linux-arm64`    | linux / aarch64 |
| `macos-latest`        | `spin-macos-arm64`    | mac / aarch64   |

(Windows is intentionally not included here, even though `spawn.build`'s JDK detector was recently
fixed to recognize `bin/java.exe` — it's just not one of our three release targets.)

To build all three locally in one shot, you need JDKs for whichever of the three platforms your
machine *isn't already*, staged somewhere spin's detector will find them.

## Where spin looks

JDK discovery (`spawn-local-jdk`'s `JDKHomeBasedPatternDetector`) reads glob patterns from
`java.home.properties`, scoped by **host OS category** — on Linux, only `unix@...` patterns are
scanned; on macOS, only `mac@...` patterns. This means a JDK's *own* target platform doesn't affect
*where* you can put it — only your host OS does. In particular, a JDK built for a completely
different OS/arch still has to live under one of the host-scoped glob patterns to be found at all.

The simplest cross-platform option on both Linux and macOS hosts, requiring no `sudo`:

```
~/.sdkman/candidates/java/<any-name>/
```

(matches `unix@sdkman` on Linux and `mac@sdkman` on macOS — you don't need the actual `sdkman` tool
installed, just the directory layout).

## Getting the JDKs

We've been using [Azul Zulu](https://www.azul.com/downloads/) builds of JDK 25 (matching this
project's `java.version`), fetched directly via Azul's CDN — no account or browser needed:

```bash
# --- linux / x86_64 ---
curl -L "https://cdn.azul.com/zulu/bin/zulu25.34.17-ca-jdk25.0.3-linux_x64.tar.gz" \
  -o /tmp/zulu-linux-amd64.tar.gz
mkdir -p ~/.sdkman/candidates/java/zulu25-linux-amd64
tar xzf /tmp/zulu-linux-amd64.tar.gz -C ~/.sdkman/candidates/java/zulu25-linux-amd64 --strip-components=1

# --- linux / aarch64 ---
curl -L "https://cdn.azul.com/zulu/bin/zulu25.34.17-ca-jdk25.0.3-linux_aarch64.tar.gz" \
  -o /tmp/zulu-linux-arm64.tar.gz
mkdir -p ~/.sdkman/candidates/java/zulu25-linux-arm64
tar xzf /tmp/zulu-linux-arm64.tar.gz -C ~/.sdkman/candidates/java/zulu25-linux-arm64 --strip-components=1

# --- macos / aarch64 (Apple Silicon) ---
curl -L "https://cdn.azul.com/zulu/bin/zulu25.34.17-ca-jdk25.0.3-macosx_aarch64.tar.gz" \
  -o /tmp/zulu-macos-arm64.tar.gz
mkdir -p ~/.sdkman/candidates/java/zulu25-macos-arm64
tar xzf /tmp/zulu-macos-arm64.tar.gz -C ~/.sdkman/candidates/java/zulu25-macos-arm64 --strip-components=3
```

Only stage the ones you don't already have natively — e.g. on an Apple Silicon Mac you'd fetch just
the two Linux ones; on an `x86_64` Linux box (like this one) you'd fetch `linux_aarch64` and
`macosx_aarch64`.

**Note the different `--strip-components`.** Zulu's Linux tarballs extract flat
(`bin/`, `lib/`, `release`, ... directly at the top), so stripping the one outer archive-name
directory is enough (`--strip-components=1`). Zulu's **macOS** tarballs nest everything under
`Contents/Home/` to mimic a macOS app bundle, so it takes `--strip-components=3` (archive dir +
`Contents` + `Home`) to land `bin/` at the top level, where the detector expects it. Getting this
wrong is the most common mistake — if detection silently skips a staged JDK, check that
`<staged-dir>/bin/java` exists directly (not two levels down).

## Verifying it worked

Run `spin` with diagnostics on and look for one `Discovered Java Development Kit ...` line per JDK:

```bash
spin --verbose clean jlink   # or your own already-installed spin binary
```

Or just run the real build and inspect the output — every target, including the host's own, gets
its own `<packageName>-<os>-<arch>/` sibling directory (no flat, un-suffixed directory at all):

```
.build/spin-linux-x86_64/    # the host's own image, if building on a linux / intel box
.build/spin-linux-aarch64/   # only appears if you staged a linux/aarch64 JDK
.build/spin-mac-aarch64/     # only appears if you staged a mac/aarch64 JDK
```

Confirm a foreign image is genuinely cross-linked, not just copied, with `file`:

```bash
file .build/spin-mac-aarch64/bin/java
# -> Mach-O 64-bit arm64 executable
file .build/spin-linux-aarch64/bin/java
# -> ELF 64-bit LSB pie executable, ARM aarch64
```
