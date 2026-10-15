package edu.eci.dosw.login.security.userdetails;

import edu.eci.dosw.login.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Implementacion de {@link UserDetailsService} preparada para la FASE 2.
 *
 * No esta anotada con @Service: en la Fase 1 la autenticacion de Spring Security
 * no se utiliza (SecurityConfig usa permitAll y AccessService valida credenciales).
 *
 * TODO Fase 2: anotar con @Service y cablearla con el AuthenticationManager.
 */
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));
    }
}