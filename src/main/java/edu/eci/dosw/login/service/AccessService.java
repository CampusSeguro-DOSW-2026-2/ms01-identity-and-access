package edu.eci.dosw.login.service;

import java.util.List;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;

public interface AccessService {
    
    public List<RegisterResponse> findAll();

    public RegisterResponse findById(Long id);

    public RegisterResponse register(RegisterRequest request);

    public LoginResponse login(LoginRequest request);

    public void delete(Long id);
}
