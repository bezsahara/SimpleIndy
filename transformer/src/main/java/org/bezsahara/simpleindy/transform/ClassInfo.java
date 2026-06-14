package org.bezsahara.simpleindy.transform;

import java.util.List;

record ClassInfo(
        String internalName,
        int access,
        String superName,
        List<String> interfaces,
        List<MethodInfo> methods,
        String location
) {
    boolean isInterface() {
        return (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
    }

    MethodInfo findMethod(String name, String descriptor) {
        for (var method : methods) {
            if (method.name().equals(name) && method.descriptor().equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    record MethodInfo(String name, String descriptor, int access) {
        boolean isStatic() {
            return (access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0;
        }

        boolean isVarargs() {
            return (access & org.objectweb.asm.Opcodes.ACC_VARARGS) != 0;
        }
    }
}
