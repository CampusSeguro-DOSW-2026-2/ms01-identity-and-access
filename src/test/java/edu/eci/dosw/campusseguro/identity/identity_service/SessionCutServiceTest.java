package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionCutServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * RF-MS01-11: snapshot of the session cuts of users and roles within the validity window
 */
@ExtendWith(MockitoExtension.class)
class SessionCutServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00.750Z");
    private static final UUID USER_ONE = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final UUID USER_TWO = UUID.fromString("7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private SessionCutServiceImpl service;

        /**
     * Creates the service with a fixed clock
     */
    @BeforeEach
    void setUp() {
        service = new SessionCutServiceImpl(userRepository, roleRepository, FixedDatabaseTime.at(NOW));
    }

        /**
     * User cut as delivered by the repository
     *
     * @param id id of the user
     * @param validFrom instant of the cut
     * @return the projection
     */
    private UserRepository.UserSessionCut userCut(UUID id, Instant validFrom) {
        UserRepository.UserSessionCut cut = mock(UserRepository.UserSessionCut.class);
        when(cut.getId()).thenReturn(id);
        when(cut.getValidFrom()).thenReturn(validFrom);
        return cut;
    }

        /**
     * Role with a cut
     *
     * @param name name of the role
     * @param validFrom instant of the cut
     * @return the role
     */
    private RoleEntity roleCut(String name, Instant validFrom) {
        RoleEntity role = new RoleEntity();
        role.setName(name);
        role.setValidFrom(validFrom);
        return role;
    }

        /**
     * The window is the token validity plus the clock tolerance and the margin: 3700 seconds
     */
    @Test
    void window_isThirtySevenHundredSeconds() {
        assertEquals(3700, GlobalValues.SESSION_CUT_WINDOW_SECONDS);
    }

        /**
     * The snapshot carries the MS01 time without a fraction, the window, and the cuts in ISO 8601 UTC with the shape of
     * the contract
     */
    @Test
    void currentCuts_returnsTheSnapshotInTheContractShape() {
        UserRepository.UserSessionCut first = userCut(USER_ONE, Instant.parse("2026-11-11T11:30:00Z"));
        UserRepository.UserSessionCut second = userCut(USER_TWO, Instant.parse("2026-11-11T11:59:59Z"));
        when(userRepository.findWithSessionCutSince(any())).thenReturn(List.of(first, second));
        when(roleRepository.findWithSessionCutSince(any())).thenReturn(List.of(
                roleCut("PROFESOR", Instant.parse("2026-11-11T11:45:10Z"))));

        SessionCutsResponse response = service.currentCuts();

        assertEquals("2026-11-11T12:00:00Z", response.generatedAt());
        assertEquals(3700, response.windowSeconds());
        assertEquals(2, response.usuarios().size());
        assertEquals(USER_ONE.toString(), response.usuarios().get(0).id());
        assertEquals("2026-11-11T11:30:00Z", response.usuarios().get(0).validFrom());
        assertEquals(USER_TWO.toString(), response.usuarios().get(1).id());
        assertEquals("2026-11-11T11:59:59Z", response.usuarios().get(1).validFrom());
        assertEquals(1, response.roles().size());
        assertEquals("PROFESOR", response.roles().get(0).rol());
        assertEquals("2026-11-11T11:45:10Z", response.roles().get(0).validFrom());
    }

        /**
     * The cuts are requested from 3700 seconds before now, measured with the MS01 clock and in whole seconds
     */
    @Test
    void currentCuts_asksForTheCutsOfTheLastThirtySevenHundredSeconds() {
        when(userRepository.findWithSessionCutSince(any())).thenReturn(List.of());
        when(roleRepository.findWithSessionCutSince(any())).thenReturn(List.of());

        service.currentCuts();

        Instant expected = Instant.parse("2026-11-11T10:58:20Z");
        ArgumentCaptor<Instant> users = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> roles = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).findWithSessionCutSince(users.capture());
        verify(roleRepository).findWithSessionCutSince(roles.capture());
        assertEquals(expected, users.getValue());
        assertEquals(expected, roles.getValue());
    }

        /**
     * Without current cuts both lists come out empty, not null
     */
    @Test
    void currentCuts_withoutCuts_returnsEmptyLists() {
        when(userRepository.findWithSessionCutSince(any())).thenReturn(List.of());
        when(roleRepository.findWithSessionCutSince(any())).thenReturn(List.of());

        SessionCutsResponse response = service.currentCuts();

        assertTrue(response.usuarios().isEmpty());
        assertTrue(response.roles().isEmpty());
        assertEquals("2026-11-11T12:00:00Z", response.generatedAt());
    }

        /**
     * The query runs in a read-only transaction
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void currentCuts_runsInAReadOnlyTransaction() throws NoSuchMethodException {
        Transactional transactional = SessionCutServiceImpl.class.getMethod("currentCuts")
                .getAnnotation(Transactional.class);

        assertTrue(transactional.readOnly());
    }
}
