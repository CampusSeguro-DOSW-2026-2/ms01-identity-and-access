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
 * Query of the current session cuts (RF-MS01-11)
 *
 * Returns the cuts of users and roles of the last 3700 seconds (token validity, clock tolerance and margin), measured
 * with the database time, the older cuts can no longer affect any valid credential and are not included, it is not
 * audited
 */
@Service
public class SessionCutServiceImpl implements SessionCutService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DatabaseTimeService timeService;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users and their cuts
     * @param roleRepository repository to access the roles and their cuts
     * @param timeService single clock of the database with which the window is measured
     */
    public SessionCutServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            DatabaseTimeService timeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.timeService = timeService;
    }

        /**
     * Builds the snapshot of the session cuts of the users and roles within the validity window
     *
     * @return the snapshot with the instant at which it was built, the window and the cuts sorted
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
