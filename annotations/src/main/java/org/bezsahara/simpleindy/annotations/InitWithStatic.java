package org.bezsahara.simpleindy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a static factory method for creating a {@link SimpleBootstrap}
 * implementation used by {@link SimpleIndy}.
 *
 * <p>When this annotation is present on the class passed to
 * {@link SimpleIndy#value()},
 * {@link org.bezsahara.simpleindy.annotations.impl.BootstrapForSimpleIndy}
 * first tries to call the named no-argument static method and cast its result
 * to {@link SimpleBootstrap}. If that factory lookup or invocation fails, the
 * adapter falls back to the class's no-argument constructor.</p>
 *
 * <pre>{@code
 * @InitWithStatic("create")
 * final class MyBootstrap implements SimpleBootstrap {
 *     static SimpleBootstrap create() {
 *         return new MyBootstrap(...);
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InitWithStatic {

    /**
     * Name of the no-argument static factory method.
     *
     * @return factory method name
     */
    String value();

}
