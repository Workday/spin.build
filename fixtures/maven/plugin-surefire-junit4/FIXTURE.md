# plugin-surefire-junit4

## What this tests

JUnit 4 tests discovered and executed by surefire without Jupiter.

## Why it is interesting

JUnit 4 uses a different test discovery mechanism (`@RunWith`, vintage engine). Projects being migrated from JUnit 4 to 5 — or that can't yet migrate — still need this to work. spin must not assume Jupiter is the only engine.

## Expected behavior

Surefire discovers the `@Test`-annotated JUnit 4 test class and runs it via the vintage provider. No Jupiter dependency is present.
