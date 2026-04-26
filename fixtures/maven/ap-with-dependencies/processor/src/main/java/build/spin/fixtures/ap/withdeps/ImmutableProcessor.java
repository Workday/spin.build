package build.spin.fixtures.ap.withdeps;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("build.spin.fixtures.ap.withdeps.Immutable")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ImmutableProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var element : roundEnv.getElementsAnnotatedWith(Immutable.class)) {
            String className = element.toString();
            String generatedName = className + "View";
            try {
                JavaFileObject f = processingEnv.getFiler().createSourceFile(generatedName);
                try (PrintWriter w = new PrintWriter(f.openWriter())) {
                    int dot = generatedName.lastIndexOf('.');
                    String pkg = generatedName.substring(0, dot);
                    String simple = generatedName.substring(dot + 1);
                    w.println("package " + pkg + ";");
                    w.println("import com.google.common.collect.ImmutableList;");
                    w.println("public class " + simple + " {");
                    w.println("    public static ImmutableList<String> fields() {");
                    w.println("        return ImmutableList.of(\"" + className + "\");");
                    w.println("    }");
                    w.println("}");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
