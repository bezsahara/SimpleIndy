package org.bezsahara.simpleindy.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.Map;

final class ClassRewriter {
    private final Log log;

    ClassRewriter(Log log) {
        this.log = log;
    }

    RewriteResult rewrite(
            InputIndex input,
            Map<MethodKey, IndyMethodTarget> targets
    ) throws TransformException {
        var problems = new ArrayList<String>();
        int replacedCallSites = 0;
        int changedClasses = 0;

        for (var unit : input.classes()) {
            ClassNode node;
            try {
                node = readClass(unit.bytes());
            } catch (RuntimeException exception) {
                problems.add(unit.location() + ": failed to parse class for rewriting: " + exception.getMessage());
                continue;
            }

            var changed = false;
            for (var method : node.methods) {
                for (var instruction = method.instructions.getFirst(); instruction != null; ) {
                    var next = instruction.getNext();
                    if (instruction instanceof MethodInsnNode methodInsn) {
                        var key = new MethodKey(methodInsn.owner, methodInsn.name, methodInsn.desc);
                        var target = targets.get(key);
                        if (target != null) {
                            if (methodInsn.getOpcode() != Opcodes.INVOKESTATIC) {
                                problems.add(
                                        unit.location() + ": " + node.name + "." + method.name + method.desc
                                                + " calls indy target " + key.display()
                                                + " with opcode " + opcodeName(methodInsn.getOpcode())
                                                + "; only INVOKESTATIC call sites can be rewritten."
                                );
                            } else {
                                method.instructions.set(methodInsn, new InvokeDynamicInsnNode(
                                        target.indyName(),
                                        methodInsn.desc,
                                        target.bootstrapHandle(),
                                        target.bootstrapArgumentArray()
                                ));
                                replacedCallSites++;
                                changed = true;
                                log.debug(
                                        "Rewrote " + node.name + "." + method.name + method.desc
                                                + " call to " + key.display()
                                                + " as invokedynamic '" + target.indyName() + "'."
                                );
                            }
                        }
                    }
                    instruction = next;
                }
            }

            if (changed) {
                if (node.version < Opcodes.V1_7) {
                    log.warn(
                            unit.location() + ": upgraded class file version from " + node.version
                                    + " to " + Opcodes.V1_7
                                    + " because invokedynamic requires Java 7 class files."
                    );
                    node.version = Opcodes.V1_7;
                }

                try {
                    var writer = new ClassWriter(0);
                    node.accept(writer);
                    unit.replaceBytes(writer.toByteArray());
                    changedClasses++;
                } catch (RuntimeException exception) {
                    problems.add(unit.location() + ": failed to write transformed class: " + exception.getMessage());
                }
            }
        }

        if (!problems.isEmpty()) {
            throw TransformException.fromProblems("Could not rewrite call sites", problems);
        }

        log.info(
                "Rewrote " + replacedCallSites + " call site"
                        + (replacedCallSites == 1 ? "" : "s")
                        + " in " + changedClasses + " class"
                        + (changedClasses == 1 ? "" : "es") + "."
        );
        return new RewriteResult(changedClasses, replacedCallSites);
    }

    private static ClassNode readClass(byte[] bytes) {
        var reader = new ClassReader(bytes);
        var node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private static String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
            case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
            case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
            case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
            default -> "opcode " + opcode;
        };
    }

    record RewriteResult(int changedClasses, int replacedCallSites) {
    }
}
