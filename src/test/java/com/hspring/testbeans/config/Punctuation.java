package com.hspring.testbeans.config;

import com.project.MultiThreadedWebServer.annotations.Component;

/** A component that a @Bean method takes as a parameter. */
@Component
public class Punctuation {
    public String mark() {
        return "!";
    }
}
