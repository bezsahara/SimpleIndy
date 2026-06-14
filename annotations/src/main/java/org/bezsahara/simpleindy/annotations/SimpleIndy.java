package org.bezsahara.simpleindy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static JVM method as a typed {@code invokedynamic} intrinsic stub.
 *
 * <p>The transformer replaces {@code invokestatic} calls to the annotated
 * method with {@code invokedynamic} instructions. The annotated method body is
 * therefore only a source-level placeholder and is not expected to run after
 * transformation.</p>
 *
 * <p>This annotation uses SimpleIndy's built-in bootstrap adapter,
 * {@link org.bezsahara.simpleindy.annotations.impl.BootstrapForSimpleIndy}.
 * The class supplied through {@link #value()} is passed to that adapter as a
 * static bootstrap argument. The adapter then creates or obtains the
 * {@link SimpleBootstrap} implementation instance and delegates to
 * {@link SimpleBootstrap#bootstrap}.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface SimpleIndy {
    /**
     * User bootstrap implementation class.
     *
     * <p>The class must implement {@link SimpleBootstrap}. At runtime
     * {@link org.bezsahara.simpleindy.annotations.impl.BootstrapForSimpleIndy}
     * creates the implementation through a no-argument constructor, or through
     * the static factory method selected by {@link InitWithStatic}, then calls
     * {@link SimpleBootstrap#bootstrap} for the linked call site.</p>
     *
     * @return the user bootstrap implementation class
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
