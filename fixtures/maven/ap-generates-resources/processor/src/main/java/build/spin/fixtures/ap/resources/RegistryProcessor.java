package build.spin.fixtures.ap.resources;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;

@SupportedAnnotationTypes("build.spin.fixtures.ap.resources.Registered")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RegistryProcessor extends AbstractProcessor {

    private final Set<String> collected = new TreeSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var element : roundEnv.getElementsAnnotatedWith(Registered.class)) {
            collected.add(element.toString());
        }
        if (roundEnv.processingOver() && !collected.isEmpty()) {
            try {
                FileObject f = processingEnv.getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/registry.txt");
                try (PrintWriter w = new PrintWriter(f.openWriter())) {
                    collected.forEach(w::println);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
