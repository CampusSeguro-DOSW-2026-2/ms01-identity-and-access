package edu.eci.dosw.login.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.exception.UserNotFoundException;
import edu.eci.dosw.login.repository.UserRepository;

@Service 
public class AccessServiceImpl implements AccessService{
    
    private UserRepository userRepository;

    public AccessServiceImpl(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    /**
     * Metodo encargado de consultar todos los usuarios que estan registrados dentro de la
     * base de datos
     */
    @Override
    public List<RegisterResponse> findAll(){
        return userRepository.findAll().stream()
                .map(entity -> RegisterResponse.fromEntity(entity))
                .toList();
    }

    /**
     * Metodo que se encarga de encontrar un usuario tomando su ID como referencia dentro de
     * la base de datos
     *
     */
    @Override
    public RegisterResponse findById(Long id){
        UserEntity user = userRepository.findById(id)
                        .orElseThrow(() -> new UserNotFoundException(id));
        return RegisterResponse.fromEntity(user);
    }

    /**
     * Metodo que se encarga de registrar un usuario dentro de la base de datos
     * 
     */
    @Override
    public RegisterResponse register(RegisterRequest request){
        UserEntity entity = new UserEntity();
        entity.setEmail(request.getEmail());
        entity.setPassword(request.getPassword());
        entity.setRol(request.getRol());
        
        UserEntity saved = userRepository.save(entity);
        return RegisterResponse.fromEntity(saved);
    }

    /**
     * Metodo que se encarga de actualizar la informacion de un usuario dentro de la base de datos
     * dada
     *
     */
    @Override 
    public RegisterResponse update(RegisterUpdateRequest request, Long id){
        UserEntity entity = userRepository.findById(id)
                            .orElseThrow(() -> new UserNotFoundException(id));
        entity.setEmail(request.getEmail());
        entity.setPassword(request.getPassword());
        entity.setRol(request.getRol());

        UserEntity updated = userRepository.save(entity);
        return RegisterResponse.fromEntity(updated);
    }

    /**
     * Metodo que se encarga de realizar el login de manera adecuada dentro de la base de datos
     * 
     */
    @Override
    public LoginResponse login(LoginRequest request){
        return null;
    }

    /**
     * Este metodo se encarga de eliminar a un usuario de la base de datos teniendo en cuenta
     * su ID para eliminarlo.
     * 
     */
    @Override
    public void delete(Long id){
        UserEntity user = userRepository.findById(id)
                        .orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(user);
    }
}
