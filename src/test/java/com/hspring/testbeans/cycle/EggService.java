package com.hspring.testbeans.cycle;

import com.project.MultiThreadedWebServer.annotations.Service;

@Service
public class EggService {
    public EggService(ChickenService chickenService) {
    }
}
