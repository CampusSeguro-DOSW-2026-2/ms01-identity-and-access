package edu.eci.dosw.login.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.exception.UserNotFoundException;
import edu.eci.dosw.login.repository.CampusRepository;

@Service 
public class AccessServiceImpl implements AccessService{
    
    private CampusRepository campusRepository;

    public AccessServiceImpl(CampusRepository campusRepository){
        this.campusRepository = campusRepository;
    }

    /**
     * Metodo encargado de consultar todos los usuarios que estan registrados dentro de la
     * base de datos
     */
    @Override
    public List<RegisterResponse> findAll(){
        return campusRepository.findAll().stream()
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
        UserEntity user = campusRepository.findById(id)
                        .orElseThrow(() -> new UserNotFoundException(id));
        return RegisterResponse.fromEntity(user);
    }

    /**
     * Metodo que se encarga de registrar un usuario dentro de la base de datos
     * 
     */
    @Override
    public RegisterResponse register(RegisterRequest request){
        return null;
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
        UserEntity user = campusRepository.findById(id)
                        .orElseThrow(() -> new UserNotFoundException(id));
        campusRepository.delete(user);
    }
}
