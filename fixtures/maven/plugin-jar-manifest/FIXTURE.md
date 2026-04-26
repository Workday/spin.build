# plugin-jar-manifest

## What this tests

Custom `MANIFEST.MF` entries via maven-jar-plugin, including `Main-Class` and bespoke key-value pairs.

## Why it is interesting

spin needs to read `<archive>` configuration to determine the main class for jlink/jpackage and to produce correct manifests for executable jars. The test reads the manifest from the classpath to confirm entries are present.

## Expected behavior

The jar manifest contains `Main-Class`, `Implementation-Version`, `Build-Tool: spin`, and `Build-Jdk`.
