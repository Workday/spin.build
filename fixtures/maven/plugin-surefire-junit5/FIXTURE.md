# plugin-surefire-junit5

## What this tests

Baseline surefire + JUnit Jupiter configuration with no extra flags.

## Why it is interesting

This is the reference case. Every other surefire fixture adds something on top of this. If this one breaks, the others are meaningless. spin must discover and run `@Test` methods via the Jupiter engine without any manual engine configuration.

## Expected behavior

Both `add` and `multiply` tests pass. No engine configuration beyond declaring `junit-jupiter` on the test classpath.
