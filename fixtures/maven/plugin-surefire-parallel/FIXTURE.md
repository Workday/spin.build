# plugin-surefire-parallel

## What this tests

Surefire configured to run test methods in parallel (`<parallel>methods</parallel>`).

## Why it is interesting

Parallel execution changes how surefire forks and threads the JVM. spin must forward the parallel configuration without serializing it back to sequential. A regression here shows up as tests running slower than expected, which is hard to catch without a timing assertion.

## Expected behavior

Both tests pass. The `@TestMethodOrder` annotation is absent intentionally — parallel methods must not depend on execution order.
