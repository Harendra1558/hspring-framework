package com.hspring.testbeans.missingvalue;

import com.project.MultiThreadedWebServer.annotations.Component;
import com.project.MultiThreadedWebServer.annotations.Value;

@Component
public class NeedsProperty {
    @Value("test.not.defined")
    private String value;
}
