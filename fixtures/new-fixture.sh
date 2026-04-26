#!/usr/bin/env bash
# Scaffolds a new fixture project.
#
# Usage:
#   ./fixtures/new-fixture.sh maven <name>
#   ./fixtures/new-fixture.sh maven plugin-surefire-junit5
#
# Creates fixtures/<tool>/<name>/ with:
#   pom.xml, .gitignore, FIXTURE.md, a Calculator source class, and a CalculatorTest.
#
# After running, edit pom.xml to add the feature-specific plugin/dep config,
# and update FIXTURE.md with the actual description.
set -euo pipefail

FIXTURES_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_BUILD="$(cd "$FIXTURES_DIR/../../base.build" && pwd)"

TOOL="${1:-}"
NAME="${2:-}"

if [ -z "$TOOL" ] || [ -z "$NAME" ]; then
    echo "Usage: $0 <tool> <name>" >&2
    echo "  e.g. $0 maven plugin-surefire-junit5" >&2
    exit 1
fi

TARGET="$FIXTURES_DIR/$TOOL/$NAME"

if [ -d "$TARGET" ]; then
    echo "Already exists: $TARGET" >&2
    exit 1
fi

# Derive a Java package segment from the fixture name.
# plugin-surefire-junit5 → surefireJunit5
# deps-bom               → depsBom
# module-single          → moduleSingle
pkg_segment() {
    local name="$1"
    local stripped
    stripped="$(echo "$name" | sed 's/^[^-]*-//')"
    echo "$stripped" | awk -F'-' '{
        result = $1
        for (i = 2; i <= NF; i++) {
            result = result toupper(substr($i,1,1)) substr($i,2)
        }
        print result
    }'
}

PKG_SEGMENT="$(pkg_segment "$NAME")"
PACKAGE="build.spin.fixtures.$PKG_SEGMENT"
PKG_PATH="$(echo "$PACKAGE" | tr '.' '/')"

mkdir -p "$TARGET/src/main/java/$PKG_PATH"
mkdir -p "$TARGET/src/test/java/$PKG_PATH"

# ── pom.xml ──────────────────────────────────────────────────────────────────
cat > "$TARGET/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>build.spin.fixtures</groupId>
    <artifactId>$NAME</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>6.0.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- TODO: add feature-specific plugin configuration here -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.5</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF

# ── .gitignore ────────────────────────────────────────────────────────────────
cat > "$TARGET/.gitignore" <<EOF
target/
EOF

# ── FIXTURE.md ────────────────────────────────────────────────────────────────
cat > "$TARGET/FIXTURE.md" <<EOF
# $NAME

## What this tests

TODO

## Why it is interesting

TODO

## Expected behavior

TODO
EOF

# ── Calculator.java ───────────────────────────────────────────────────────────
cat > "$TARGET/src/main/java/$PKG_PATH/Calculator.java" <<EOF
package $PACKAGE;

public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }
}
EOF

# ── CalculatorTest.java ───────────────────────────────────────────────────────
cat > "$TARGET/src/test/java/$PKG_PATH/CalculatorTest.java" <<EOF
package $PACKAGE;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    private final Calculator calc = new Calculator();

    @Test
    void add() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void multiply() {
        assertEquals(6, calc.multiply(2, 3));
    }
}
EOF

# ── mvnw ─────────────────────────────────────────────────────────────────────
mkdir -p "$TARGET/.mvn/wrapper"
cp "$BASE_BUILD/mvnw" "$TARGET/mvnw"
chmod +x "$TARGET/mvnw"
cp "$BASE_BUILD/.mvn/wrapper/maven-wrapper.properties" "$TARGET/.mvn/wrapper/maven-wrapper.properties"

echo
echo "Created: $TARGET"
echo
echo "Next steps:"
echo "  1. Edit pom.xml — add feature-specific plugin/dep config"
echo "  2. Edit FIXTURE.md — fill in the description"
echo "  3. Edit src/**/*.java — adjust code to exercise the feature"
echo "  4. Run: (from spin.build root) ./test-fixtures.sh $NAME"
