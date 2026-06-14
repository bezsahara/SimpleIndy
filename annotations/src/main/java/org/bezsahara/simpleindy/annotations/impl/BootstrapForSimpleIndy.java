package org.bezsahara.simpleindy.annotations.impl;

import org.bezsahara.simpleindy.annotations.InitWithStatic;
import org.bezsahara.simpleindy.annotations.SimpleBootstrap;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

public final class BootstrapForSimpleIndy {
    public static CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            Class<? extends SimpleBootstrap> userClass,
            Object... spreader
    ) throws Throwable {
        var instance = createInstanceSB(userClass);
        return instance.bootstrap(lookup, name, type, spreader);
    }


    static SimpleBootstrap createInstanceSB(Class<? extends SimpleBootstrap> userClass) throws Throwable {
        Objects.requireNonNull(userClass);
        var possible = userClass.getDeclaredAnnotation(InitWithStatic.class);
        if (possible != null) {
            try {
                var method = userClass.getDeclaredMethod(possible.value());
                method.setAccessible(true);
                return (SimpleBootstrap) method.invoke(null);
            } catch (Throwable ignored) {
            }
        }
        // let's try constructor
        var ctor = userClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (SimpleBootstrap) ctor.newInstance();
    }
}
