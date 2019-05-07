/*
 * Copyright (c) 2019  Scott C. Sosna  ALL RIGHTS RESERVED
 *
 */

package com.buddhadata.sandbox.sdrrandom.random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Spring Boot application for the Oracle Code Card services.
 */
@SpringBootApplication
public class Application {

    /**
     * Main entry point for kicking off Spring Boot application
     * @param args command line args
     */
    public static void main (String[] args) {
        SpringApplication.run (Application.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner (ApplicationContext ctx) {

        //  Print out the beans provided by Spring Boot, useful for debugging only
        return args -> {

            System.out.println("Let's inspect the beans provided by Spring Boot:");
            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }
        };
    }
}
