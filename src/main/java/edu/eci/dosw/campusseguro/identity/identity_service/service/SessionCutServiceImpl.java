package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Consulta de los cortes de sesión vigentes (RF-MS01-11)
 *
 * Devuelve los cortes de usuarios y roles de los últimos 3700 segundos (vigencia del token, tolerancia de
 * reloj y margen), medidos con la hora de la base de datos, los cortes más antiguos ya no pueden afectar a ninguna
 * credencial vigente y no se incluyen, no se audita
 */
@Service
public class SessionCutServiceImpl implements SessionCutService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DatabaseTimeService timeService;

    /**
     * Crea el servicio con sus dependencias
     *
     * @param userRepository repositorio de acceso a los usuarios y sus cortes
     * @param roleRepository repositorio de acceso a los roles y sus cortes
     * @param timeService reloj único de la base de datos con el que se mide la ventana
     */
    public SessionCutServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            DatabaseTimeService timeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.timeService = timeService;
    }

    /**
     * Arma la instantánea de los cortes de sesión de los usuarios y roles dentro de la ventana de vigencia
     *
     * @return la instantánea con el instante en que se armó, la ventana y los cortes ordenados
     */
    @Override
    @Transactional(readOnly = true)
    public SessionCutsResponse currentCuts() {
        Instant now = timeService.now();
        Instant since = now.minusSeconds(GlobalValues.SESSION_CUT_WINDOW_SECONDS);

        List<SessionCutsResponse.UserCut> users = userRepository.findWithSessionCutSince(since).stream()
                .map(cut -> new SessionCutsResponse.UserCut(cut.getId().toString(),
                        DateTimeFormatter.ISO_INSTANT.format(cut.getValidFrom())))
                .toList();
        List<SessionCutsResponse.RoleCut> roles = roleRepository.findWithSessionCutSince(since).stream()
                .map(cut -> new SessionCutsResponse.RoleCut(cut.getName(),
                        DateTimeFormatter.ISO_INSTANT.format(cut.getValidFrom())))
                .toList();

        return new SessionCutsResponse(DateTimeFormatter.ISO_INSTANT.format(now),
                GlobalValues.SESSION_CUT_WINDOW_SECONDS, users, roles);
    }
}
