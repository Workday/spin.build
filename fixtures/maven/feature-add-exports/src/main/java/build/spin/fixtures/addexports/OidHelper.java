package build.spin.fixtures.addexports;

import sun.security.util.ObjectIdentifier;
import sun.security.util.KnownOIDs;

public class OidHelper {

    public static String sha256OidString() {
        return KnownOIDs.SHA256withRSA.value();
    }
}
