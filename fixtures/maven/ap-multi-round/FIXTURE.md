# ap-multi-round

## What this tests

An annotation processor that generates code in round 1 which itself carries an annotation that triggers further processing in round 2.

## Why it is interesting

Multi-round processing stresses the AP lifecycle. Many build tools assume APs are single-pass. Round 2 sources appear after round 1 completes, so the build must re-run the compiler with the freshly generated sources on the source path.

## Expected behavior

- Round 1: `@Step` on `Fetch` → generates `FetchWrapper` annotated with `@StepWrapper`
- Round 2: `@StepWrapper` on `FetchWrapper` → generates `FetchWrapperCatalog`
- Both generated classes are available at test time

## Judgment call

Single processor handles both annotations to keep the fixture self-contained. A real-world case would typically have separate processors in separate jars.
