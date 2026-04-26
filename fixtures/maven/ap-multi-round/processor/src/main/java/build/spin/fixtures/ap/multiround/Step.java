package build.spin.fixtures.ap.multiround;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class as a pipeline step; the AP generates a wrapper annotated with @StepWrapper. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Step {}
