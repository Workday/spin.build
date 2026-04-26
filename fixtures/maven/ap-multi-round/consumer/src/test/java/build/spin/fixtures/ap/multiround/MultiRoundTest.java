package build.spin.fixtures.ap.multiround;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultiRoundTest {

    @Test
    void wrapperExists() throws Exception {
        Class.forName("build.spin.fixtures.ap.multiround.FetchWrapper");
    }

    @Test
    void catalogExists() throws Exception {
        Class.forName("build.spin.fixtures.ap.multiround.FetchWrapperCatalog");
    }
}
