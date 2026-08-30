package com.haris.SpringEcom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.haris.SpringEcom.repo")
public class SpringEcomApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringEcomApplication.class, args);
	}

}
