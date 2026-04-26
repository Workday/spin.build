# plugin-jpackage

## What this tests

Native application image via jpackage (`app-image` type), configured through the jpackage-maven-plugin.

## Why it is interesting

jpackage wraps a jlink image into a platform-native application bundle. spin must identify the main class and forward it. This fixture uses `app-image` (no installer) so it works cross-platform without OS-specific packaging tools.

## Expected behavior

`mvn package` produces a native app image under `target/dist/calculator/`. Tests pass via surefire before packaging.

## Known quirks

jpackage requires the JDK's `jpackage` tool, present since JDK 16. If the build JDK is older this will fail at the packaging step — but that's an environment issue, not a spin issue.
