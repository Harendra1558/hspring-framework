package com.hspring.testbeans.ok;

import com.project.MultiThreadedWebServer.annotations.Service;

@Service("greeter")
public class EnglishGreetingService implements GreetingService {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
