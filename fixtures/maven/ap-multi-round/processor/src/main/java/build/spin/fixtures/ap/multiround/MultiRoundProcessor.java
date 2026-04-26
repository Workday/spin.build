package build.spin.fixtures.ap.multiround;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Set;

/**
 * Round 1: @Step on Fetch → generates FetchWrapper (annotated @StepWrapper)
 * Round 2: @StepWrapper on FetchWrapper → generates FetchWrapperCatalog
 */
@SupportedAnnotationTypes({
    "build.spin.fixtures.ap.multiround.Step",
    "build.spin.fixtures.ap.multiround.StepWrapper"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MultiRoundProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement stepAnnotation = processingEnv.getElementUtils()
                .getTypeElement("build.spin.fixtures.ap.multiround.Step");
        TypeElement wrapperAnnotation = processingEnv.getElementUtils()
                .getTypeElement("build.spin.fixtures.ap.multiround.StepWrapper");

        if (stepAnnotation != null) {
            for (var element : roundEnv.getElementsAnnotatedWith(stepAnnotation)) {
                generateWrapper(element.toString());
            }
        }
        if (wrapperAnnotation != null) {
            for (var element : roundEnv.getElementsAnnotatedWith(wrapperAnnotation)) {
                generateCatalog(element.toString());
            }
        }
        return true;
    }

    private void generateWrapper(String className) {
        String generated = className + "Wrapper";
        try {
            JavaFileObject f = processingEnv.getFiler().createSourceFile(generated);
            try (PrintWriter w = new PrintWriter(f.openWriter())) {
                int dot = generated.lastIndexOf('.');
                w.println("package " + generated.substring(0, dot) + ";");
                w.println("@StepWrapper");
                w.println("public class " + generated.substring(dot + 1) + " {}");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateCatalog(String className) {
        String generated = className + "Catalog";
        try {
            JavaFileObject f = processingEnv.getFiler().createSourceFile(generated);
            try (PrintWriter w = new PrintWriter(f.openWriter())) {
                int dot = generated.lastIndexOf('.');
                w.println("package " + generated.substring(0, dot) + ";");
                w.println("public class " + generated.substring(dot + 1) + " {");
                w.println("    public static String name() { return \"" + className + "\"; }");
                w.println("}");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
