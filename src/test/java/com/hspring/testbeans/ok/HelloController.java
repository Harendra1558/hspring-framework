package com.hspring.testbeans.ok;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.RestController;

/** Field injection, including a field inherited from the superclass. */
@RestController
public class HelloController extends BaseController {
    @Autowired
    OrderService orderService;

    @Autowired(required = false)
    Runnable optionalDependency;
}
