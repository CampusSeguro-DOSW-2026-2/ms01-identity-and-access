
package edu.eci.dosw.campusseguro.identity.identity_service.service;

public interface EmailService {

    void sendResetCode(String to, String code, int validMinutes);

}