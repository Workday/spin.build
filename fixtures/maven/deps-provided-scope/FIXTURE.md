# deps-provided-scope

## What this tests

`<scope>provided</scope>` — the Servlet API is available at compile and test time but must not appear in the packaged jar.

## Why it is interesting

Provided-scope deps are assumed to be supplied by the runtime container (a servlet container, an app server, etc.). They must be on the compile classpath but excluded from the jar. spin must distinguish provided scope from compile scope when assembling artifacts and when computing downstream module classpaths.

## Expected behavior

`HttpServlet` compiles and the test passes (provided dep is on the test classpath). The packaged jar does not contain `jakarta/servlet/` classes.
