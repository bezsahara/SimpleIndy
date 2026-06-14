package org.bezsahara.simpleindy.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TargetScanner {
    private final Log log;

    TargetScanner(Log log) {
        this.log = log;
    }

    ScanResult scan(List<ClassUnit> classUnits, ClassResolver resolver, boolean verify)
            throws TransformException {
        var targets = new LinkedHashMap<MethodKey, IndyMethodTarget>();
        var problems = new ArrayList<String>();

        for (var unit : classUnits) {
            ClassNode node;
            try {
                node = readClass(unit.bytes());
            } catch (RuntimeException exception) {
                problems.add(unit.location() + ": failed to parse class: " + exception.getMessage());
                continue;
            }

            for (var method : node.methods) {
                scanMethod(unit, node, method, resolver, verify, targets, problems);
            }
        }

        if (!problems.isEmpty()) {
            throw TransformException.fromProblems("Invalid SimpleIndy annotations", problems);
        }

        log.info(
                "Discovered " + targets.size() + " indy target"
                        + (targets.size() == 1 ? "" : "s") + "."
        );
        return new ScanResult(Map.copyOf(targets));
    }

    private void scanMethod(
            ClassUnit unit,
            ClassNode owner,
            MethodNode method,
            ClassResolver resolver,
            boolean verify,
            Map<MethodKey, IndyMethodTarget> targets,
            List<String> problems
    ) {
        var simple = findAnnotation(method, IndyConstants.SIMPLE_INDY_DESCRIPTOR);
        var custom = findAnnotation(method, IndyConstants.CUSTOM_INDY_DESCRIPTOR);

        if (simple == null && custom == null) {
            return;
        }

        var location = unit.location() + ": " + owner.name + "." + method.name + method.desc;

        if (simple != null && custom != null) {
            problems.add(location + ": method has both @SimpleIndy and @CustomIndy; choose exactly one.");
            return;
        }

        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            problems.add(location + ": indy annotations are only supported on static methods.");
            return;
        }

        var key = new MethodKey(owner.name, method.name, method.desc);
        IndyMethodTarget target = simple != null
                ? readSimpleTarget(simple, key, method.name, location, resolver, verify, problems)
                : readCustomTarget(custom, key, method.name, location, resolver, verify, problems);

        if (target == null) {
            return;
        }

        var previous = targets.putIfAbsent(key, target);
        if (previous != null) {
            problems.add(
                    location + ": duplicate indy target for " + key.display()
                            + "; first declaration was at " + previous.sourceLocation() + "."
            );
            return;
        }

        log.debug("Registered " + target.kind() + " target " + key.display() + " -> indy '" + target.indyName() + "'.");
    }

    private IndyMethodTarget readSimpleTarget(
            AnnotationNode annotation,
            MethodKey key,
            String methodName,
            String location,
            ClassResolver resolver,
            boolean verify,
            List<String> problems
    ) {
        var value = annotationValue(annotation, "value");
        if (!(value instanceof Type valueType) || valueType.getSort() != Type.OBJECT) {
            problems.add(location + ": @SimpleIndy.value must be a class literal implementing SimpleBootstrap.");
            return null;
        }

        var valueInternalName = valueType.getInternalName();
        var indyName = indyName(annotation, methodName, location, problems);
        if (indyName == null) {
            return null;
        }

        if (verify) {
            var valueInfo = resolver.resolve(valueInternalName);
            if (valueInfo.isEmpty()) {
                problems.add(
                        location + ": --verify could not resolve @SimpleIndy.value class "
                                + valueInternalName + ". Add it to the inputs or pass --classpath."
                );
            } else if (!resolver.isAssignableTo(valueInternalName, IndyConstants.SIMPLE_BOOTSTRAP_INTERFACE)) {
                problems.add(
                        location + ": @SimpleIndy.value class " + valueInternalName
                                + " does not implement " + IndyConstants.SIMPLE_BOOTSTRAP_INTERFACE + "."
                );
            }
        }

        return new IndyMethodTarget(
                key,
                indyName,
                IndyConstants.SIMPLE_BOOTSTRAP_HANDLE,
                List.of(Type.getObjectType(valueInternalName)),
                location,
                IndyMethodTarget.Kind.SIMPLE
        );
    }

    private IndyMethodTarget readCustomTarget(
            AnnotationNode annotation,
            MethodKey key,
            String methodName,
            String location,
            ClassResolver resolver,
            boolean verify,
            List<String> problems
    ) {
        var owner = requiredString(annotation, "owner", location, problems);
        var bootstrap = requiredString(annotation, "bootstrap", location, problems);
        var bootstrapDescriptor = requiredString(annotation, "bootstrapDescriptor", location, problems);
        var indyName = indyName(annotation, methodName, location, problems);

        if (owner == null || bootstrap == null || bootstrapDescriptor == null || indyName == null) {
            return null;
        }

        if (!isInternalClassName(owner)) {
            problems.add(
                    location + ": @CustomIndy.owner must be a JVM internal class name like pkg/MyBootstrap, got '"
                            + owner + "'."
            );
            return null;
        }

        if (!isMethodDescriptor(bootstrapDescriptor)) {
            problems.add(
                    location + ": @CustomIndy.bootstrapDescriptor is not a valid JVM method descriptor: "
                            + bootstrapDescriptor
            );
            return null;
        }

        var ownerIsInterface = ownerIsInterface(annotation, owner, location, resolver, verify, problems);
        if (ownerIsInterface == null) {
            return null;
        }

        if (verify) {
            validateCustomBootstrap(
                    owner,
                    bootstrap,
                    bootstrapDescriptor,
                    ownerIsInterface,
                    location,
                    resolver,
                    problems
            );
        }

        var handle = new Handle(Opcodes.H_INVOKESTATIC, owner, bootstrap, bootstrapDescriptor, ownerIsInterface);
        return new IndyMethodTarget(
                key,
                indyName,
                handle,
                List.of(),
                location,
                IndyMethodTarget.Kind.CUSTOM
        );
    }

    private Boolean ownerIsInterface(
            AnnotationNode annotation,
            String owner,
            String location,
            ClassResolver resolver,
            boolean verify,
            List<String> problems
    ) {
        var explicit = hasAnnotationValue(annotation, "ownerIsInterface");
        var value = annotationValue(annotation, "ownerIsInterface");

        if (explicit && !(value instanceof Boolean)) {
            problems.add(location + ": @CustomIndy.ownerIsInterface must be a boolean.");
            return null;
        }

        if (!explicit) {
            var ownerInfo = resolver.resolve(owner);
            if (ownerInfo.isEmpty()) {
                problems.add(
                        location + ": @CustomIndy.ownerIsInterface was omitted, and owner "
                                + owner + " could not be resolved from inputs, --classpath, or the transformer classpath."
                );
                return null;
            }
            var derived = ownerInfo.get().isInterface();
            log.debug(
                    "Derived ownerIsInterface=" + derived + " for " + owner
                            + " from " + ownerInfo.get().location() + "."
            );
            return derived;
        }

        var declared = (Boolean) value;
        if (verify) {
            var ownerInfo = resolver.resolve(owner);
            if (ownerInfo.isEmpty()) {
                problems.add(
                        location + ": --verify could not resolve @CustomIndy.owner "
                                + owner + ". Add it to the inputs or pass --classpath."
                );
            } else if (ownerInfo.get().isInterface() != declared) {
                problems.add(
                        location + ": @CustomIndy.ownerIsInterface is " + declared
                                + " but resolved owner " + owner + " is "
                                + (ownerInfo.get().isInterface() ? "an interface" : "a class")
                                + " at " + ownerInfo.get().location() + "."
                );
            }
        }

        return declared;
    }

    private void validateCustomBootstrap(
            String owner,
            String bootstrap,
            String bootstrapDescriptor,
            boolean ownerIsInterface,
            String location,
            ClassResolver resolver,
            List<String> problems
    ) {
        var ownerInfo = resolver.resolve(owner);
        if (ownerInfo.isEmpty()) {
            problems.add(
                    location + ": --verify could not resolve bootstrap owner "
                            + owner + ". Add it to the inputs or pass --classpath."
            );
            return;
        }

        if (ownerInfo.get().isInterface() != ownerIsInterface) {
            problems.add(
                    location + ": bootstrap handle interface flag is " + ownerIsInterface
                            + " but resolved owner " + owner + " is "
                            + (ownerInfo.get().isInterface() ? "an interface" : "a class") + "."
            );
        }

        var method = ownerInfo.get().findMethod(bootstrap, bootstrapDescriptor);
        if (method == null) {
            problems.add(
                    location + ": bootstrap method not found: " + owner + "."
                            + bootstrap + bootstrapDescriptor + " in " + ownerInfo.get().location() + "."
            );
            return;
        }

        if (!method.isStatic()) {
            problems.add(location + ": bootstrap method " + owner + "." + bootstrap + bootstrapDescriptor
                    + " exists but is not static.");
        }

        validateBootstrapShape(bootstrapDescriptor, method, location, resolver, problems);
    }

    private void validateBootstrapShape(
            String descriptor,
            ClassInfo.MethodInfo method,
            String location,
            ClassResolver resolver,
            List<String> problems
    ) {
        var args = Type.getArgumentTypes(descriptor);
        var returnType = Type.getReturnType(descriptor);

        if (args.length < 3
                || !IndyConstants.LOOKUP_DESCRIPTOR.equals(args[0].getDescriptor())
                || !IndyConstants.STRING_DESCRIPTOR.equals(args[1].getDescriptor())
                || !IndyConstants.METHOD_TYPE_DESCRIPTOR.equals(args[2].getDescriptor())) {
            problems.add(
                    location + ": bootstrap descriptor must start with "
                            + "(MethodHandles.Lookup, String, MethodType). Got " + descriptor + "."
            );
        }

        if (!returnsCallSite(returnType, resolver)) {
            problems.add(location + ": bootstrap descriptor must return java.lang.invoke.CallSite or a subclass.");
        }

        if (args.length > 3) {
            var allowedVarargsCollector = args.length == 4
                    && method.isVarargs()
                    && args[3].getSort() == Type.ARRAY;
            if (!allowedVarargsCollector) {
                problems.add(
                        location + ": CustomIndy currently emits no extra static bootstrap arguments, so "
                                + "bootstrapDescriptor must have exactly three parameters, or be a varargs method "
                                + "with one trailing array parameter."
                );
            }
        }
    }

    private boolean returnsCallSite(Type returnType, ClassResolver resolver) {
        if (returnType.getSort() != Type.OBJECT) {
            return false;
        }
        var internalName = returnType.getInternalName();
        return IndyConstants.CALL_SITE.equals(internalName)
                || resolver.isAssignableTo(internalName, IndyConstants.CALL_SITE);
    }

    private static ClassNode readClass(byte[] bytes) {
        var reader = new ClassReader(bytes);
        var node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static AnnotationNode findAnnotation(MethodNode method, String descriptor) {
        var invisible = findAnnotation(method.invisibleAnnotations, descriptor);
        var visible = findAnnotation(method.visibleAnnotations, descriptor);
        return invisible != null ? invisible : visible;
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return null;
        }

        for (var annotation : annotations) {
            if (descriptor.equals(annotation.desc)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return null;
        }

        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static boolean hasAnnotationValue(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return false;
        }

        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static String requiredString(
            AnnotationNode annotation,
            String name,
            String location,
            List<String> problems
    ) {
        var value = annotationValue(annotation, name);
        if (!(value instanceof String string) || string.isBlank()) {
            problems.add(location + ": @CustomIndy." + name + " must be a non-empty string.");
            return null;
        }
        return string;
    }

    private static String indyName(
            AnnotationNode annotation,
            String methodName,
            String location,
            List<String> problems
    ) {
        var value = annotationValue(annotation, "name");
        if (value == null) {
            return methodName;
        }
        if (!(value instanceof String string)) {
            problems.add(location + ": annotation name element must be a string.");
            return null;
        }
        if (string.isEmpty()) {
            return methodName;
        }
        if (string.isBlank()) {
            problems.add(location + ": indy name cannot be blank whitespace.");
            return null;
        }
        return string;
    }

    private static boolean isInternalClassName(String owner) {
        return !owner.isBlank()
                && !owner.startsWith("/")
                && !owner.endsWith("/")
                && !owner.contains(".")
                && !owner.contains(";")
                && !owner.contains("[");
    }

    private static boolean isMethodDescriptor(String descriptor) {
        if (!descriptor.startsWith("(")) {
            return false;
        }
        try {
            Type.getMethodType(descriptor);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    record ScanResult(Map<MethodKey, IndyMethodTarget> targets) {
    }
}
