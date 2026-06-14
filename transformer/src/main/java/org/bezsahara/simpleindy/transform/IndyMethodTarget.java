package org.bezsahara.simpleindy.transform;

import org.objectweb.asm.Handle;

import java.util.List;

record IndyMethodTarget(
        MethodKey key,
        String indyName,
        Handle bootstrapHandle,
        List<Object> bootstrapArguments,
        String sourceLocation,
        Kind kind
) {
    Object[] bootstrapArgumentArray() {
        return bootstrapArguments.toArray(Object[]::new);
    }

    enum Kind {
        SIMPLE,
        CUSTOM
    }
}
