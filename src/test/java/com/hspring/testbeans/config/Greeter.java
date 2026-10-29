package com.hspring.testbeans.config;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Qualifier;
import com.project.MultiThreadedWebServer.annotations.Service;
import com.project.MultiThreadedWebServer.annotations.Value;

/** @Qualifier on a constructor parameter AND a field, plus typed @Value fields. */
@Service
public class Greeter {
    private final Greeting greeting;

    @Autowired
    @Qualifier("loudGreeting")
    private Greeting loudGreeting;

    @Value("test.repeat")
    private int repeat;

    @Value("test.enabled")
    private boolean enabled;

    public Greeter(@Qualifier("greeting") Greeting greeting) {
        this.greeting = greeting;
    }

    public Greeting getGreeting() {
        return greeting;
    }

    public Greeting getLoudGreeting() {
        return loudGreeting;
    }

    public int getRepeat() {
        return repeat;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
