package com.openclaw.plugins.demo;

/**
 * Tiny POJO registered by {@link HelloPlugin#onLoad} under bean name
 * {@code plugin.hello.greeter}. Exists solely to demonstrate that plugins can
 * contribute beans into the host Spring context.
 */
public class HelloGreeter {

    public String greet(final String name) {
        return "hello, " + name + " — from the openclaw hello plugin";
    }
}
