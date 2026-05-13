package com.ramira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RamiraFoodsMsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RamiraFoodsMsApplication.class, args);
	}

}
