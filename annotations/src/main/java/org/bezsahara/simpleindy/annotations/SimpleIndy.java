package org.bezsahara.simpleindy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java method as a typed {@code invokedynamic} intrinsic stub.
 *
 * <p>The transformer replaces calls to the annotated method with an
 * {@code invokedynamic} instruction.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface SimpleIndy {
    /**
     * Bootstrap owner class.
     *
     * @return the class that owns the bootstrap method
     */
    Class<? extends SimpleBootstrap> value();

    /**
     * Optional {@code invokedynamic} name override.
     *
     * <p>If empty, the annotated method name is used.</p>
     *
     * @return the indy call-site name, or empty to derive it
     */
    String name() default "";
}
