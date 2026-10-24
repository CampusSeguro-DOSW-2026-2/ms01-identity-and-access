package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;

/**
 * Crea el primer administrador al arrancar (RN-MS01-12)
 *
 * Solo se crea si no existe ningun usuario con rol ADMIN y si estan definidas las variables de entorno
 * BOOTSTRAP_ADMIN_EMAIL y BOOTSTRAP_ADMIN_PASSWORD (o las propiedades bootstrap.admin.email y
 * bootstrap.admin.password); nunca se guardan en el repositorio. El usuario queda ACTIVO con el rol
 * ADMIN, con BOOTSTRAP_ADMIN_FIRST_NAME y BOOTSTRAP_ADMIN_LAST_NAME se le da un nombre
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    static final String DEFAULT_FIRST_NAME = "Administrador";
    static final String DEFAULT_LAST_NAME = "Inicial";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    /**
     * Crea el inicializador con el repositorio y los datos del primer administrador.
     *
     * @param userRepository repositorio de acceso a los usuarios
     * @param passwordEncoder codificador con el que se guarda la contrasena
     * @param email correo del primer administrador; vacio desactiva el arranque
     * @param password contrasena del primer administrador; vacia desactiva el arranque
     * @param firstName primer nombre del administrador; si esta vacio se usa un valor por defecto
     * @param lastName primer apellido del administrador; si esta vacio se usa un valor por defecto
     */
    public BootstrapAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bootstrap.admin.email:${BOOTSTRAP_ADMIN_EMAIL:}}") String email,
            @Value("${bootstrap.admin.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password,
            @Value("${bootstrap.admin.first-name:${BOOTSTRAP_ADMIN_FIRST_NAME:}}") String firstName,
            @Value("${bootstrap.admin.last-name:${BOOTSTRAP_ADMIN_LAST_NAME:}}") String lastName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * Ejecuta la creacion del primer administrador una vez arrancada la aplicacion
     *
     * @param args
     */
    @Override
    public void run(ApplicationArguments args) {
        createFirstAdminIfNeeded();
    }

    /**
     * Crea el primer administrador si hay datos configurados, si todavia no existe ningun ADMIN y si el
     * correo no pertenece ya a otro usuario
     *
     * @return true si se creo el administrador; false si no hizo falta o no fue posible
     */
    public boolean createFirstAdminIfNeeded() {
        if (isBlank(email) || isBlank(password)) {
            log.info("Primer administrador no configurado (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD): "
                    + "no se crea ninguno.");
            return false;
        }

        if (userRepository.countWithRole(UserRole.ADMIN) > 0) {
            log.info("Ya existe un administrador: no se crea el primer administrador.");
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("El correo del primer administrador ya pertenece a un usuario sin rol ADMIN: no se crea "
                    + "ni se asciende automaticamente.");
            return false;
        }

        UserEntity admin = new UserEntity();
        admin.setFirstName(valueOrDefault(firstName, DEFAULT_FIRST_NAME));
        admin.setFirstLastName(valueOrDefault(lastName, DEFAULT_LAST_NAME));
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(password));
        List<UserRole> roles = new ArrayList<>();
        roles.add(UserRole.ADMIN);
        admin.setRoles(roles);
        admin.setState(UserState.ACTIVO);

        userRepository.save(admin);
        log.info("Primer administrador creado con el rol ADMIN.");
        return true;
    }

    /**
     * Indica si un valor de configuracion esta ausente o solo contiene espacios
     *
     * @param value String de la configuracion
     * @return true si el valor es null o esta en blanco
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Devuelve el valor recortado, o uno por defecto si esta vacio.
     *
     * @param value valor leido de la configuracion
     * @param defaultValue valor que se usa cuando no hay uno configurado
     * @return el valor final
     */
    private static String valueOrDefault(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return value.trim();
    }
}
