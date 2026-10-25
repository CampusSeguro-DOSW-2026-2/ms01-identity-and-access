package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.IndividualChangeStateResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminOrAuditorRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidSearchParamException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SelfStatusChangeForbiddenException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

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
     * Este metodo se encarga de realizar la consulta de los perfiles de los usuarios al momento de estar registrados en
     * la app web.
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(UUID requesterId, String userRole, UUID id){
        requireAdminOrAuditor(userRole);
        
        UserEntity entity = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
        return UserProfileResponse.fromEntity(entity);
    }

    /**
     * Este metodo es el encargado de realizar la busqueda de usuarios, esta busqueda solo la puede hacer el administrador
     * y puede tener 3 posibles filtros, buscar por correo, estado o rol
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UUID requesterId, String userRole, String correo, String estado, String rol, int page, int size){
        requireAdminOrAuditor(userRole);

        if (page < 0) throw new InvalidSearchParamException("page debe ser mayor o igual a 0");
        if (size < 1 || size > 100) throw new InvalidSearchParamException("size debe estar entre 1 y 100");

        String textCorreo = (correo == null || correo.isBlank()) ? null : correo.trim().toLowerCase();
        UserState stateEnum = parseFilter(estado, UserState.class, "estado");
        UserRole roleEnum = parseFilter(rol, UserRole.class, "rol");

        Pageable pageable = PageRequest.of(page, size, Sort.by("email").ascending());
        Page<UserEntity> pageResult = userRepository.searchUsers(textCorreo, stateEnum, roleEnum, pageable);

        return PageResponse.from(pageResult.map(entity -> UserResponse.fromEntity(entity)));
    }

    /**
     * Este metodo se encarga de realizar los cambios masivos, esta funcion solamente puede ser usada por el administrador
     * y puede realizar un cambio en varios usuarios al mismo tiempo
     */
    @Override
    @Transactional 
    public ChangeStatusResponse changeUserStatus(UUID requesterId, String userRole, ChangeStatusRequest request){
        if(userRole == null || (!userRole.contains("ADMIN"))){ throw new AdminOrAuditorRequiredException(); }
        if(request.getListIds().contains(requesterId)){ throw new SelfStatusChangeForbiddenException(); }

        List<UserEntity> usersToUpdate = new ArrayList<>();
        for (UUID id : request.getListIds()){
            UserEntity user = userRepository.findById(id)
                            .orElseThrow(() -> new UserNotFoundException(id));
            usersToUpdate.add(user);
        }

        List<IndividualChangeStateResponse> resultados = new ArrayList<>();
        UserState nuevoEstado = request.getState();

        for (UserEntity user : usersToUpdate) {
            UserState estadoAnterior = user.getState();
            user.setState(nuevoEstado);
            userRepository.save(user);

            resultados.add(IndividualChangeStateResponse.fromEntity(user, estadoAnterior));
        }

        return ChangeStatusResponse.fromEntity(resultados);
    }

    /**
     * Metodo que se encarga de crear un nuevo usuario en la base de datos, es la base para el registro de nuevos usuarios
     * en la aplicacion web.
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
        entity.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        entity.setState(UserState.ACTIVO);

        UserEntity saved = userRepository.save(entity);
        return UserResponse.fromEntity(saved);
    }

    /**
     * Este metodo se encarga de obtener toda la informacion del usuario en el apartado de perfil. Solamente su 
     * informacion.
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

    /**
     * 
     * @param userRole
     */
    private void requireAdminOrAuditor(String userRole){
        if(userRole == null || (!userRole.contains("ADMIN") && !userRole.contains("AUDITOR"))){
            throw new AdminOrAuditorRequiredException();
        }
    }

    /**
     * 
     * @param <E>
     * @param value
     * @param type
     * @param name
     * @return
     */
    private static <E extends Enum<E>> E parseFilter(String value, Class<E> type, String name) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("TODOS")) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSearchParamException("Valor inválido para el filtro " + name);
        }
    }
}
