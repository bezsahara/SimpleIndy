package org.bezsahara.simpleindy.core;

import org.bezsahara.simpleindy.Properties;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

public class IndyVisitorFinder extends ClassVisitor {
    public IndyVisitorFinder(int api, ClassVisitor next) {
        super(api, next);
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(api, mv) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(Properties.SIMPLE_INDY_PATH)) {

                }

                AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);

                return new AnnotationVisitor(api, annotationVisitor) {
                    @Override
                    public void visit(String name, Object value) {
                        System.out.println(name + " = " + value);
                    }

                    @Override
                    public void visitEnum(String name, String desc, String value) {
                        System.out.println(name + " = " + value);
                    }

                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(api) {
                            @Override
                            public void visit(String ignored, Object value) {
                                System.out.println(name + "[] value = " + value);
                            }
                        };
                    }
                };
            }

            @Override
            public void visitInvokeDynamicInsn(
                    String name,
                    String descriptor,
                    Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments
            ) {
                // found invokedynamic
                super.visitInvokeDynamicInsn(
                        name,
                        descriptor,
                        bootstrapMethodHandle,
                        bootstrapMethodArguments
                );
            }
        };
    }
}