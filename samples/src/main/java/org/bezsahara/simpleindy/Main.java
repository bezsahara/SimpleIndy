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

    static int value = 10;

    static void testInJava() {
        if (constantJavaTest() == constantJavaTest()) {
            System.out.println("It works in java");
        }
    }

    static class ConstantBootstrap implements SimpleBootstrap {
        @Override
        public CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, Object[] args) {
            return new ConstantCallSite(MethodHandles.constant(Integer.TYPE, value));
        }
    }

    @SimpleIndy(ConstantBootstrap.class)
    public static int constantJavaTest() {
        throw new AssertionError("Implemented via transform");
    }
}
