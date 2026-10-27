package com.hspring.testbeans.ok;

import com.project.MultiThreadedWebServer.annotations.Autowired;

/** Not a component itself; its @Autowired field is inherited by subclasses. */
public abstract class BaseController {
    @Autowired
    protected GreetingService greetingService;
}
