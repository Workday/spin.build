package build.spin.fixtures.addopens;

import java.lang.reflect.Field;

public class FieldAccess {

    public static int stringHashCode(String s) throws Exception {
        Field f = String.class.getDeclaredField("hash");
        f.setAccessible(true);
        // Force hash computation
        s.hashCode();
        return (int) f.get(s);
    }
}
