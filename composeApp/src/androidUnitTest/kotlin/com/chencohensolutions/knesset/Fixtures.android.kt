package com.chencohensolutions.knesset

actual fun fixture(name: String): String {
    val stream = Thread.currentThread().contextClassLoader!!.getResourceAsStream("fixtures/$name")
        ?: error("Missing fixture $name")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
