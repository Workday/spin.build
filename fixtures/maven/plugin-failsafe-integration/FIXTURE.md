# plugin-failsafe-integration

## What this tests

Integration tests run via the maven-failsafe-plugin (`*IT.java` naming convention, bound to `integration-test` + `verify` goals).

## Why it is interesting

Failsafe runs in a different lifecycle phase from surefire and uses a separate naming convention (`*IT`). spin must distinguish unit tests from integration tests and handle the failsafe lifecycle binding. A build that only runs surefire will silently skip the `*IT` classes.

## Expected behavior

`CalculatorIT` is picked up by failsafe (not surefire) and passes during `mvn verify`. `CalculatorTest` is picked up by surefire as usual.
