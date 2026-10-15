package edu.eci.dosw.login.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.dto.RoleResponse;
import edu.eci.dosw.login.dto.UserPatchRequest;
import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.entity.UserRol;
import edu.eci.dosw.login.exception.EmailAlreadyExistsException;
import edu.eci.dosw.login.exception.InvalidCredentialsException;
import edu.eci.dosw.login.exception.UserNotFoundException;
import edu.eci.dosw.login.repository.UserRepository;

@Service
public class AccessServiceImpl implements AccessService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // TODO Fase 2: inyectar AuthTokenProvider para emitir el JWT en login().

    public AccessServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Consulta todos los usuarios registrados en la base de datos.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RegisterResponse> findAll() {
        return userRepository.findAll().stream()
                .map(RegisterResponse::fromEntity)
                .toList();
    }

    /**
     * Busca un usuario por su identificador.
     */
    @Override
    @Transactional(readOnly = true)
    public RegisterResponse findById(Long id) {
        return RegisterResponse.fromEntity(getOrThrow(id));
    }

    /**
     * Busca un usuario por su correo electronico.
     */
    @Override
    @Transactional(readOnly = true)
    public RegisterResponse findByEmail(String email) {
        UserEntity entity = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException(email));
        return RegisterResponse.fromEntity(entity);
    }

    /**
     * Registra un nuevo usuario. Valida unicidad del correo y cifra la contrasena.
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        UserEntity entity = new UserEntity(email, passwordEncoder.encode(request.password()), request.rol());
        return RegisterResponse.fromEntity(userRepository.save(entity));
    }

    /**
     * Actualiza por completo la informacion de un usuario existente.
     */
    @Override
    @Transactional
    public RegisterResponse update(RegisterUpdateRequest request, Long id) {
        UserEntity entity = getOrThrow(id);
        String email = normalizeEmail(request.email());
        if (!email.equals(entity.getEmail()) && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(request.password()));
        entity.setRol(request.rol());
        return RegisterResponse.fromEntity(userRepository.save(entity));
    }

    /**
     * Actualiza parcialmente la informacion de un usuario existente.
     */
    @Override
    @Transactional
    public RegisterResponse patch(UserPatchRequest request, Long id) {
        UserEntity entity = getOrThrow(id);

        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizeEmail(request.email());
            if (!email.equals(entity.getEmail()) && userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }
            entity.setEmail(email);
        }
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.rol() != null) {
            entity.setRol(request.rol());
        }
        return RegisterResponse.fromEntity(userRepository.save(entity));
    }

    /**
     * Asigna un rol a un usuario existente.
     */
    @Override
    @Transactional
    public RegisterResponse assignRol(Long id, UserRol rol) {
        UserEntity entity = getOrThrow(id);
        entity.setRol(rol);
        return RegisterResponse.fromEntity(userRepository.save(entity));
    }

    /**
     * Valida las credenciales de un usuario. El mensaje de error es generico para
     * evitar user-enumeration.
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity entity = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), entity.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // TODO Fase 2: generar el token con AuthTokenProvider y rellenar
        // tokenType/accessToken/expiresAt de la respuesta.
        return LoginResponse.authenticated(RegisterResponse.fromEntity(entity));
    }

    /**
     * Elimina un usuario existente.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        UserEntity entity = getOrThrow(id);
        userRepository.delete(entity);
    }

    /**
     * Catalogo de roles disponibles (constraint ckk_rol).
     */
    @Override
    public List<RoleResponse> findRoles() {
        return Arrays.stream(UserRol.values())
                .map(rol -> new RoleResponse(rol.name(), rol.getDisplayName()))
                .toList();
    }

    private UserEntity getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}