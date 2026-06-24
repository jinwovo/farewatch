package com.portfolio.farewatch;

import org.springframework.boot.SpringApplication;

public class TestFarewatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(FarewatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
