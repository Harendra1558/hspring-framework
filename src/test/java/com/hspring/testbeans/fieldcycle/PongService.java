package com.hspring.testbeans.fieldcycle;

import com.project.MultiThreadedWebServer.annotations.Autowired;
import com.project.MultiThreadedWebServer.annotations.Service;

@Service
public class PongService {
    @Autowired
    public PingService ping;
}
