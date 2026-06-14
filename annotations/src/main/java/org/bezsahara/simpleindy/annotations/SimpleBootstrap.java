package org.bezsahara.simpleindy.annotations;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public interface SimpleBootstrap {
    // args are currently unused but in future versions they will be somehow implemented
     public CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            Object[] args
    );
}
