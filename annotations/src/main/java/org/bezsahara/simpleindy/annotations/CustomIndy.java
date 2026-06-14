package org.bezsahara.simpleindy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full-control {@code invokedynamic} intrinsic annotation.
 *
 * <p>The transformer replaces {@code invokestatic} calls to the annotated
 * method with an {@code invokedynamic} instruction using the bootstrap method
 * handle declared here. The annotated method must be static; its body is only a
 * source-level placeholder and is not expected to run after transformation.</p>
 *
 * <p>The bootstrap handle is always emitted as {@code H_INVOKESTATIC}.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface CustomIndy {
    /**
     * Bootstrap owner internal JVM name.
     *
     * <p>Example: {@code "pkg/MyBootstrap"}.</p>
     *
     * @return the bootstrap owner internal name
     */
    String owner();

    /**
     * Static bootstrap method name.
     *
     * @return the bootstrap method name
     */
    String bootstrap();

    /**
     * Static bootstrap method JVM descriptor.
     *
     * <p>The descriptor must start with the standard bootstrap parameters:
     * {@code (MethodHandles.Lookup, String, MethodType)} and must return
     * {@code java.lang.invoke.CallSite} or a subclass. SimpleIndy currently
     * emits no extra static bootstrap arguments for {@code @CustomIndy}, so the
     * descriptor should have exactly those three parameters unless the
     * bootstrap method is a varargs collector with one trailing array
     * parameter.</p>
     *
     * @return the full bootstrap method descriptor
     */
    String bootstrapDescriptor();

    /**
     * Whether the bootstrap owner is an interface.
     *
     * <p>ASM needs this value when emitting the bootstrap method handle. When
     * this element is not explicitly present in source, the transformer tries
     * to resolve {@link #owner()} and derive the value even when strict
     * verification is disabled. If the value is explicitly set, derivation is
     * skipped unless verification is enabled.</p>
     *
     * @return {@code true} when {@link #owner()} names an interface
     */
    boolean ownerIsInterface() default false;

    /**
     * Optional {@code invokedynamic} name override.
     *
     * <p>If empty, the annotated method name is used.</p>
     *
     * @return the indy call-site name, or empty to derive it
     */
    String name() default "";
}
