package edu.eci.dosw.campusseguro.identity.identity_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the identity and access microservice (MS01)
 */
@SpringBootApplication
public class IdentityServiceApplication {

		/**
	 * Starts the Spring Boot application of the microservice
	 *
	 * @param args command line arguments received at startup
	 */
	public static void main(String[] args) {
		SpringApplication.run(IdentityServiceApplication.class, args);
	}

}
