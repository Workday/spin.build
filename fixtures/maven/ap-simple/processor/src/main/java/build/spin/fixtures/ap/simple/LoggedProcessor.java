package build.spin.fixtures.ap.simple;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Set;

@SupportedAnnotationTypes("build.spin.fixtures.ap.simple.Logged")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class LoggedProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var element : roundEnv.getElementsAnnotatedWith(Logged.class)) {
            String className = element.toString();
            String generatedName = className + "Logger";
            try {
                JavaFileObject f = processingEnv.getFiler().createSourceFile(generatedName);
                try (PrintWriter w = new PrintWriter(f.openWriter())) {
                    int dot = generatedName.lastIndexOf('.');
                    String pkg = generatedName.substring(0, dot);
                    String simple = generatedName.substring(dot + 1);
                    w.println("package " + pkg + ";");
                    w.println("public class " + simple + " {");
                    w.println("    public static void log(String msg) { System.out.println(\"[LOG] \" + msg); }");
                    w.println("}");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
