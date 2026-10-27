package com.hspring.testbeans.ok;

import com.project.MultiThreadedWebServer.annotations.PostConstruct;
import com.project.MultiThreadedWebServer.annotations.PreDestroy;
import com.project.MultiThreadedWebServer.annotations.Service;

import java.util.ArrayList;
import java.util.List;

/** Constructor injection (single constructor, no @Autowired needed) + lifecycle callbacks. */
@Service
public class OrderService {
    public static final List<String> EVENTS = new ArrayList<>();

    private final Repository repository;
    private final GreetingService greetingService;

    public OrderService(Repository repository, GreetingService greetingService) {
        this.repository = repository;
        this.greetingService = greetingService;
    }

    @PostConstruct
    void init() {
        EVENTS.add("postConstruct:" + (repository != null));
    }

    @PreDestroy
    void shutdown() {
        EVENTS.add("preDestroy");
    }

    public Repository getRepository() {
        return repository;
    }

    public GreetingService getGreetingService() {
        return greetingService;
    }
}
