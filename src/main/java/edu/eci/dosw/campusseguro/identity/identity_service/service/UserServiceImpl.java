package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService{
    
    private UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository){
        this.userRepository = userRepository;
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
    public UserResponse findByEmail(String email){
        return null;
    }

    /**
     * 
     * 
     */
    @Override
    public UserResponse findByState(UserState state){
        return null;
    }

    /**
     * 
     */
    @Override
    public UserResponse findByRole(UserRole rol){
        return null;
    }

    /**
     * 
     */
    @Override
    public UserResponse create(UserCreateRequest request){
        UserEntity entity = new UserEntity();
        entity.setFirstName(request.getFirstName());
        entity.setSecondtName(request.getSecondName());
        entity.setFirstLastName(request.getFirstLastName());
        entity.setSecondLastName(request.getSecondLastName());
        entity.setEmail(request.getEmail());
        entity.setPassword(request.getPassword());
        entity.setRole(request.getRole());
        entity.setState(request.getState());

        UserEntity saved = userRepository.save(entity);
        return UserResponse.fromEntity(saved);
    }
}
