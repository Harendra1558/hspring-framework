package com.hspring.testbeans.cycle;

import com.project.MultiThreadedWebServer.annotations.Service;

@Service
public class ChickenService {
    public ChickenService(EggService eggService) {
    }
}
