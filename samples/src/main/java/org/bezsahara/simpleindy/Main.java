package org.bezsahara.simpleindy;


import org.bezsahara.simpleindy.annotations.SimpleBootstrap;
import org.bezsahara.simpleindy.annotations.SimpleIndy;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.bezsahara.simpleindy.KotlinMainKt.testInKotlin;

public class Main {
    static void main() {
        testInJava();
        testInKotlin();
    }

    static void testInJava() {
        if (constantTes3() == constantTes3()) {
            System.out.println("It works in java");
        }
    }

    static class ConstantBootstrap3 implements SimpleBootstrap {
        @Override
        public CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, Object[] args) {
            return new ConstantCallSite(MethodHandles.constant(Integer.TYPE, 10));
        }
    }

    @SimpleIndy(ConstantBootstrap3.class)
    public static int constantTes3() {
        throw new AssertionError("Implemented via transform");
    }
}
