package com.hspring.testbeans.config;

import com.project.MultiThreadedWebServer.annotations.Bean;
import com.project.MultiThreadedWebServer.annotations.Configuration;

/** Two @Bean methods of the SAME type, so injection points need @Qualifier. */
@Configuration
public class GreetingConfig {

    @Bean
    public Greeting greeting(Punctuation punctuation) {
        return new Greeting("hello" + punctuation.mark());
    }

    @Bean("loudGreeting")
    public Greeting shout() {
        return new Greeting("HELLO");
    }
}
