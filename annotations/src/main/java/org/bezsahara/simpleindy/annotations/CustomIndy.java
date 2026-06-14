package org.bezsahara.simpleindy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full-control {@code invokedynamic} intrinsic annotation.
 *
 * <p>The transformer replaces calls to the annotated method with an
 * {@code invokedynamic} instruction using the exact static bootstrap method
 * declared here.</p>
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
     * @return the full bootstrap method descriptor
     */
    String bootstrapDescriptor();

    /**
     * Whether the bootstrap owner is an interface.
     *
     * <p>ASM needs this value when emitting the bootstrap method handle. If this
     * element is omitted, the transformer will try to resolve the owner class
     * and derive it even when strict verification is disabled.</p>
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
