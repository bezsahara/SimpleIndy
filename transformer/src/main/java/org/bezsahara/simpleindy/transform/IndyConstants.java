package org.bezsahara.simpleindy.transform;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

final class IndyConstants {
    static final String SIMPLE_INDY_DESCRIPTOR = "Lorg/bezsahara/simpleindy/annotations/SimpleIndy;";
    static final String CUSTOM_INDY_DESCRIPTOR = "Lorg/bezsahara/simpleindy/annotations/CustomIndy;";
    static final String SIMPLE_BOOTSTRAP_INTERFACE = "org/bezsahara/simpleindy/annotations/SimpleBootstrap";
    static final String CALL_SITE = "java/lang/invoke/CallSite";
    static final String LOOKUP_DESCRIPTOR = "Ljava/lang/invoke/MethodHandles$Lookup;";
    static final String METHOD_TYPE_DESCRIPTOR = "Ljava/lang/invoke/MethodType;";
    static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

    private static final String SIMPLE_BOOTSTRAP_OWNER =
            "org/bezsahara/simpleindy/annotations/impl/BootstrapForSimpleIndy";
    private static final String SIMPLE_BOOTSTRAP_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;";

    static final Handle SIMPLE_BOOTSTRAP_HANDLE = new Handle(
            Opcodes.H_INVOKESTATIC,
            SIMPLE_BOOTSTRAP_OWNER,
            "bootstrap",
            SIMPLE_BOOTSTRAP_DESCRIPTOR,
            false
    );

    private IndyConstants() {
    }
}
