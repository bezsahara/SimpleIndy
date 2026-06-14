package org.bezsahara.simpleindy

import org.bezsahara.simpleindy.annotations.SimpleBootstrap
import org.bezsahara.simpleindy.annotations.SimpleIndy
import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType


class ConstantBootstrapKotlin : SimpleBootstrap {
    override fun bootstrap(
        lookup: MethodHandles.Lookup,
        name: String,
        type: MethodType,
        args: Array<out Any?>,
    ): CallSite {
        return ConstantCallSite(MethodHandles.constant(Int::class.java, 11))
    }
}

fun testInKotlin() {
    if (constantKotlinTest() == constantKotlinTest()) {
        println("It works in kotlin")
    }
}

@SimpleIndy(ConstantBootstrapKotlin::class)
fun constantKotlinTest(): Int = error("Should not happen")