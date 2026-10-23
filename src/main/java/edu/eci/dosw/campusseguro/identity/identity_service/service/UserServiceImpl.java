package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class UserServiceImpl implements UserService{
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Este metodo se encarga de realizar la busqueda general de todos los usuarios en la base de datos.
     * Este metodo retorna todos los UserResponse generados
     * 
     */
    @Override
    public List<UserResponse> findAll(){
        return userRepository.findAll().stream()
                .map(entity -> UserResponse.fromEntity(entity))
                .toList();
    }

    /**
     * Este metodo realiza una busqueda sobre la base de datos con un ID en especifico, de tal forma que
     * retorna el UserResponse correspondiente.
     *
     */
    @Override
    public UserResponse findById(UUID id){
        UserEntity entity = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        return UserResponse.fromEntity(entity);
    }

    /**
     * 
     * 
     */
    @Override
    @Transactional
    public List<UserResponse> findByEmail(String email){
        return userRepository.findByEmailContainingIgnoreCase(email).stream()
                .map(entity -> UserResponse.fromEntity(entity))
                .collect(Collectors.toList());
    }

    /**
     * 
     * 
     */
    @Override
    @Transactional
    public List<UserResponse> findByState(UserState state){
        return userRepository.findByState(state).stream()
                .map(entity -> UserResponse.fromEntity(entity))
                .collect(Collectors.toList());
    }

    /**
     * 
     */
    @Override
    public List<UserResponse> findByRole(UserRole role){
        return userRepository.findByRoles(role).stream()
                .map(entity -> UserResponse.fromEntity(entity))
                .collect(Collectors.toList());
    }

    /**
     * 
     */
    @Override
    @Transactional 
    public UserResponse create(UserCreateRequest request){

        String email = request.getEmail().trim().toLowerCase();
 
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        UserEntity entity = new UserEntity();
        entity.setFirstName(request.getFirstName());
        entity.setSecondName(blankToNull(request.getSecondName()));
        entity.setFirstLastName(request.getFirstLastName());
        entity.setSecondLastName(blankToNull(request.getSecondLastName()));
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(request.getPassword()));
        entity.setRoles(new ArrayList<>(List.of(UserRole.ESTUDIANTE)));
        entity.setState(UserState.ACTIVO);

        UserEntity saved = userRepository.save(entity);
        return UserResponse.fromEntity(saved);
    }

    /**
     * 
     */
    @Override
    public UserProfileResponse getMyProfile(UUID id){
        UserEntity entity = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        return UserProfileResponse.fromEntity(entity);
    }

    /**
     * 
     */
    @Override 
    @Transactional 
    public UserProfileResponse updateProfile(UUID id, UserUpdateProfileRequest request){
        UserEntity entity = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        entity.setFirstName(request.getFirstName());
        entity.setSecondName(blankToNull(request.getSecondName()));
        entity.setFirstLastName(request.getFirstLastName());
        entity.setSecondLastName(blankToNull(request.getSecondLastName()));

        UserEntity updated = userRepository.save(entity);
        return UserProfileResponse.fromEntity(updated);
    }

    /**
     * 
     * @param value
     * @return
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
