package com.hspring.testbeans.ambiguous;

import com.project.MultiThreadedWebServer.annotations.Service;

@Service
public class AlertService {
    public AlertService(Notifier notifier) {
    }
}
