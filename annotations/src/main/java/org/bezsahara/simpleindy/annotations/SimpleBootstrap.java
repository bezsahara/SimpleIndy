package org.bezsahara.simpleindy.annotations;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * User bootstrap contract for {@link SimpleIndy}.
 *
 * <p>{@link SimpleIndy} does not emit this method directly as the JVM
 * bootstrap method. Instead, the transformer emits
 * {@link org.bezsahara.simpleindy.annotations.impl.BootstrapForSimpleIndy} as
 * the real bootstrap owner. That adapter receives the {@code SimpleBootstrap}
 * implementation class from {@link SimpleIndy#value()}, creates or obtains an
 * instance, and then delegates to {@link #bootstrap}.</p>
 *
 * <p>Implementations normally need an accessible no-argument constructor. If
 * construction should go through a static factory method, annotate the
 * implementation class with {@link InitWithStatic}.</p>
 *
 * <p>Typical source shape:</p>
 *
 * <pre>{@code
 * static class MyBootstrap implements SimpleBootstrap {
 *     public CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, Object[] args) {
 *         return ...;
 *     }
 * }
 *
 * @SimpleIndy(MyBootstrap.class)
 * public static int value() {
 *     throw new AssertionError("Replaced by SimpleIndy");
 * }
 * }</pre>
 */
public interface SimpleBootstrap {
    /**
     * Links one {@code invokedynamic} call site.
     *
     * @param lookup caller lookup object supplied by the JVM
     * @param name invokedynamic call-site name
     * @param type invokedynamic call-site method type
     * @param args extra static bootstrap arguments supplied by SimpleIndy;
     *             currently empty for {@link SimpleIndy}
     * @return call site used for this linked invocation site
     */
    CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            Object[] args
    );
}
