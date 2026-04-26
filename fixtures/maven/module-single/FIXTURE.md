# module-single

## What this tests

A single named JPMS module with a `module-info.java` that exports its package.

## Why it is interesting

Presence of `module-info.java` switches the compiler from classpath mode to module-path mode. spin must detect the file, compile on the module path, and run surefire with the module path rather than the classpath. A build tool that ignores `module-info.java` will produce an unnamed-module jar which may cause downstream module resolution failures.

## Expected behavior

Compiles as a named module `build.spin.fixtures.moduleSingle`. Tests pass. The jar manifest contains an `Automatic-Module-Name` entry (or the module-info is included in the jar root).
