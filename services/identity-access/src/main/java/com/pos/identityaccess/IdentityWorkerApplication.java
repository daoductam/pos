package com.pos.identityaccess;

import org.springframework.boot.SpringApplication;

public final class IdentityWorkerApplication {

    private IdentityWorkerApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IdentityAccessApplication.class);
        application.setAdditionalProfiles("worker");
        application.run(args);
    }
}
