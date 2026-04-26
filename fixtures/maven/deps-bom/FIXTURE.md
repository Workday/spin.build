# deps-bom

## What this tests

BOM-managed dependency versions via `<dependencyManagement>` with `<type>pom</type><scope>import</scope>`.

## Why it is interesting

BOMs are the standard way large projects (Spring, Jackson, Quarkus) centralise version management. A dependency declared without a version in `<dependencies>` must resolve its version from the imported BOM. spin must process `dependencyManagement` and BOM imports before resolving dependency versions — skipping this step produces a resolution failure.

## Expected behavior

`jackson-databind` and `jackson-core` are declared without `<version>` tags and both resolve correctly to the version specified in the Jackson BOM.
