package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Creates the first administrator at startup (RN-MS01-12)
 *
 * It is created only if no administrator exists yet and if the environment variables BOOTSTRAP_ADMIN_EMAIL and
 * BOOTSTRAP_ADMIN_PASSWORD are defined (or the properties bootstrap.admin.email and bootstrap.admin.password); the user
 * is active and BOOTSTRAP_ADMIN_FIRST_NAME and BOOTSTRAP_ADMIN_LAST_NAME give it a name
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    static final String DEFAULT_FIRST_NAME = "Administrador";
    static final String DEFAULT_LAST_NAME = "Inicial";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

        /**
     * Creates the initializer with the repository and the data of the first administrator
     *
     * @param userRepository repository to access the users
     * @param roleRepository repository to access the roles
     * @param passwordEncoder encoder with which the password is stored
     * @param email email of the first administrator; empty disables the startup creation
     * @param password password of the first administrator; empty disables the startup creation
     * @param firstName first name of the administrator; if it is empty a default value is used
     * @param lastName first lastname of the administrator; if it is empty a default value is used
     */
    public BootstrapAdminInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bootstrap.admin.email:${BOOTSTRAP_ADMIN_EMAIL:}}") String email,
            @Value("${bootstrap.admin.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password,
            @Value("${bootstrap.admin.first-name:${BOOTSTRAP_ADMIN_FIRST_NAME:}}") String firstName,
            @Value("${bootstrap.admin.last-name:${BOOTSTRAP_ADMIN_LAST_NAME:}}") String lastName) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

        /**
     * Runs the creation of the first administrator once the application has started
     *
     * @param args startup arguments of the application
     */
    @Override
    public void run(ApplicationArguments args) {
        createFirstAdminIfNeeded();
    }

        /**
     * Creates the first administrator if there is configured data, if no administrator exists yet and if the email does
     * not already belong to another user
     *
     * @return true if the administrator was created; false if it was not needed or was not possible
     */
    public boolean createFirstAdminIfNeeded() {
        if (isBlank(email) || isBlank(password)) {
            log.info("First administrator not configured (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD): "
                    + "none is created.");
            return false;
        }

        if (userRepository.countWithRole(RoleNames.ADMIN) > 0) {
            log.info("An administrator already exists: the first administrator is not created.");
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("The email of the first administrator already belongs to a user without the ADMIN role: "
                    + "it is neither created nor promoted automatically.");
            return false;
        }

        RoleEntity adminRole = roleRepository.findByName(RoleNames.ADMIN).orElseThrow(
                () -> new IllegalStateException("The system role " + RoleNames.ADMIN + " does not exist"));

        UserEntity admin = new UserEntity();
        admin.setFirstName(valueOrDefault(firstName, DEFAULT_FIRST_NAME));
        admin.setFirstLastName(valueOrDefault(lastName, DEFAULT_LAST_NAME));
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRoles(Set.of(adminRole));
        admin.setState(UserState.ACTIVO);

        userRepository.save(admin);
        log.info("First administrator created with the ADMIN role.");
        return true;
    }

        /**
     * Tells whether a configuration value is missing or contains only spaces
     *
     * @param value configuration String
     * @return true if the value is null or blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

        /**
     * Returns the trimmed value, or a default one if it is empty
     *
     * @param value value read from the configuration
     * @param defaultValue value used when none is configured
     * @return the final value
     */
    private static String valueOrDefault(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return value.trim();
    }
}
