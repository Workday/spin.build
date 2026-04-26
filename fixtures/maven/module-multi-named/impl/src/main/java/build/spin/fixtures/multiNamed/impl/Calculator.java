package build.spin.fixtures.multiNamed.impl;

import build.spin.fixtures.multiNamed.api.Arithmetic;

public class Calculator implements Arithmetic {

    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public int multiply(int a, int b) {
        return a * b;
    }
}
