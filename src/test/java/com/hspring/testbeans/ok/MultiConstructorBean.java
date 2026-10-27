package com.hspring.testbeans.ok;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Component;

/** Several constructors: the @Autowired one must win. */
@Component
public class MultiConstructorBean {
    final Repository repository;

    public MultiConstructorBean() {
        this.repository = null;
    }

    @Autowired
    public MultiConstructorBean(Repository repository) {
        this.repository = repository;
    }
}
