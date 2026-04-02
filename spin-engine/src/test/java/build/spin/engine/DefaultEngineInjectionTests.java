package build.spin.engine;

import build.base.configuration.Configuration;
import build.spin.Engine;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystems;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dependency Injection tests for the {@link DefaultEngine}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
class DefaultEngineInjectionTests {

    /**
     * Ensure that {@link DocumentBuilderFactory} is injectable.
     */
    @Test
    void shouldDefineDocumentBuilderFactoryForInjection() {

        final Engine engine = new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        assertThat(engine.context().create(DocumentBuilderFactory.class)).isNotNull();
    }
}
