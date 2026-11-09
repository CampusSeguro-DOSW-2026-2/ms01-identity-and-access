package edu.eci.dosw.campusseguro.identity.identity_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de identidad y acceso (MS01)
 */
@SpringBootApplication
public class IdentityServiceApplication {

	/**
	 * Arranca la aplicación Spring Boot del microservicio
	 *
	 * @param args argumentos de línea de comandos recibidos al arrancar
	 */
	public static void main(String[] args) {
		SpringApplication.run(IdentityServiceApplication.class, args);
	}

}
