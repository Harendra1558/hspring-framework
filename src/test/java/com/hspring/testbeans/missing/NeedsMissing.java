package com.hspring.testbeans.missing;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Component;

@Component
public class NeedsMissing {
    @Autowired
    NotABean notABean;
}
