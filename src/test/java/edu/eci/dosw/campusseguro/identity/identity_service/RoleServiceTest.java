package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * RF-MS01-05 / stories KAIR-36 and KAIR-41: query of roles with permissions, base flow of the role change, alternate
 * flows A1 to A4, atomicity, CAMBIO_ROL audit and rules RN-MS01-11 to RN-MS01-14
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID USER_A = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final UUID USER_B = UUID.fromString("7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleServiceImpl roleService;

        /**
     * Creates the service with a fixed clock so the date of the events and of the session cut is predictable, by
     * default all the requested roles exist
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        roleService = new RoleServiceImpl(userRepository, roleRepository, new AuditRecorder(auditPublisher, clock),
                FixedDatabaseTime.at(NOW));
        lenient().when(roleRepository.findAllByNameIn(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            List<RoleEntity> existing = new ArrayList<>();
            if (names != null) {
                for (String name : names) {
                    existing.add(TestEntities.role(name, "ms01:profile:read"));
                }
            }
            return existing;
        });
    }

        /**
     * Identity of an administrator
     *
     * @return identity with the ADMIN role
     */
    private RequesterIdentity admin() {
        return TestIdentities.of(ADMIN_ID, "ADMIN");
    }

        /**
     * Identity of a user with the indicated roles
     *
     * @param roles roles of the requester
     * @return identity with those roles
     */
    private RequesterIdentity requesterWith(String... roles) {
        return TestIdentities.of(ADMIN_ID, roles);
    }

        /**
     * Stored role with the indicated permissions
     *
     * @param name name of the role
     * @param permissions permission codes of the role
     * @return the entity of the role
     */
    private RoleEntity role(String name, String... permissions) {
        return TestEntities.role(name, permissions);
    }

        /**
     * User with modifiable roles, like the ones delivered by Hibernate
     *
     * @param id identifier of the user
     * @param roles current roles
     * @return the entity
     */
    private UserEntity user(UUID id, String... roles) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail(id + "@mail.escuelaing.edu.co");
        entity.setRoles(TestEntities.roles(roles));
        return entity;
    }

        /**
     * Builds the body of the request
     *
     * @param ids user identifiers
     * @param add names of the roles to add
     * @param remove names of the roles to remove
     * @return the request
     */
    private ChangeRolesRequest request(List<String> ids, List<String> add, List<String> remove) {
        ChangeRolesRequest request = new ChangeRolesRequest();
        request.setIds(ids);
        request.setAgregar(add);
        request.setRetirar(remove);
        return request;
    }

        /**
     * Captures the published audit events
     *
     * @param expected number of events that should have been published
     * @return the events, in the order in which they were published
     */
    private List<AuditEvent> publishedEvents(int expected) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------ GET /roles

        /**
     * Whoever has the permission ms01:role:read_all receives all the stored roles, sorted by name, each one with the
     * codes of its permissions
     */
    @Test
    void getRoles_withReadAll_returnsEveryStoredRole() {
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                role("ADMIN", "ms01:user:roles", "ms01:role:read_all"),
                role("MONITOR_EVACUACION", "ms04:checkin:write")));

        List<RolePermissionsResponse> roles = roleService.getRoles(admin());

        assertEquals(2, roles.size());
        assertEquals("ADMIN", roles.get(0).rol());
        assertEquals(List.of("ms01:role:read_all", "ms01:user:roles"), roles.get(0).permisos());
        assertEquals("MONITOR_EVACUACION", roles.get(1).rol());
        assertEquals(List.of("ms04:checkin:write"), roles.get(1).permisos());
        verify(roleRepository, never()).findAllByNameIn(any());
        verifyNoInteractions(userRepository, auditPublisher);
    }

        /**
     * The permission decides, not the name of the role: a custom role with ms01:role:read_all also receives the
     * complete catalog
     */
    @Test
    void getRoles_customRoleWithReadAll_returnsEveryStoredRole() {
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(role("ADMIN"), role("SOPORTE")));
        RequesterIdentity support = TestIdentities.withPermissions(ADMIN_ID, Set.of("SOPORTE"),
                "ms01:role:read_all", "ms01:role:read_own");

        List<RolePermissionsResponse> roles = roleService.getRoles(support);

        assertEquals(2, roles.size());
    }

        /**
     * Whoever does not have ms01:role:read_all receives only their own roles, sorted by name
     */
    @Test
    void getRoles_withoutReadAll_returnsOnlyItsOwnRoles() {
        when(roleRepository.findAllByNameIn(Set.of("ESTUDIANTE", "PROFESOR"))).thenReturn(List.of(
                role("PROFESOR", "ms05:incident:report"), role("ESTUDIANTE", "ms01:profile:read")));

        List<RolePermissionsResponse> roles = roleService.getRoles(requesterWith("PROFESOR", "ESTUDIANTE"));

        assertEquals(2, roles.size());
        assertEquals("ESTUDIANTE", roles.get(0).rol());
        assertEquals("PROFESOR", roles.get(1).rol());
        verify(roleRepository, never()).findAllByOrderByNameAsc();
    }

        /**
     * The permission codes are delivered as they are stored in the database
     */
    @Test
    void getRoles_returnsTheStoredCodesOfEachRole() {
        when(roleRepository.findAllByNameIn(any())).thenReturn(List.of(
                role("BRIGADISTA", "ms05:panel:read_own", "ms99:viejo:permiso")));

        List<RolePermissionsResponse> roles = roleService.getRoles(requesterWith("BRIGADISTA"));

        assertEquals(List.of("ms05:panel:read_own", "ms99:viejo:permiso"), roles.get(0).permisos());
    }

        /**
     * A role of the requester that no longer exists in the table does not appear in the response
     */
    @Test
    void getRoles_roleMissingFromTheTable_isOmitted() {
        when(roleRepository.findAllByNameIn(any())).thenReturn(List.of());

        assertTrue(roleService.getRoles(requesterWith("AUDITOR")).isEmpty());
    }

    // ------------------------------------------------------------------ PATCH /users/roles: base flow

        /**
     * Base flow: adding a role answers with the resulting roles, persists the change and audits an EXITO event with
     * who, to whom and the previous and new roles
     */
    @Test
    void changeRoles_addRole_updatesPersistsAndAudits() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        assertEquals(USER_A.toString(), response.resultados().get(0).id());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), response.resultados().get(0).roles());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), target.getRoleNames());
        verify(userRepository).saveAllAndFlush(List.of(target));

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditAction.CAMBIO_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertEquals("usuario:" + USER_A, event.recursoAfectado());
        assertEquals("agregados=PROFESOR;retirados=", event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertEquals(NOW, event.fecha());
        assertEquals("ADMIN", event.usuarioRoles());
    }

        /**
     * The modified user receives the session cut with the time of the clock truncated to seconds (RF-MS01-11)
     */
    @Test
    void changeRoles_addRole_setsTheSessionCutOnTheModifiedUser() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        roleService.changeRoles(admin(), request(List.of(USER_A.toString()), List.of("PROFESOR"), null),
                CORRELATION_ID);

        assertEquals(NOW, target.getValidFrom());
        publishedEvents(1);
    }

        /**
     * The cut is the database time, it is queried only once and is applied to all the modified users
     */
    @Test
    void changeRoles_sessionCutIsTheDatabaseTimeQueriedOnce() {
        DatabaseTimeService databaseTime = mock(DatabaseTimeService.class);
        Instant databaseNow = Instant.parse("2026-10-23T16:30:05Z");
        when(databaseTime.now()).thenReturn(databaseNow);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        roleService = new RoleServiceImpl(userRepository, roleRepository, new AuditRecorder(auditPublisher, clock),
                databaseTime);
        UserEntity first = user(USER_A, "ESTUDIANTE");
        UserEntity second = user(USER_B, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(first, second));

        roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(databaseNow, first.getValidFrom());
        assertEquals(databaseNow, second.getValidFrom());
        verify(databaseTime, times(1)).now();
        publishedEvents(2);
    }

        /**
     * A user whose roles do not change does not receive a session cut
     */
    @Test
    void changeRoles_unchangedUser_hasNoSessionCut() {
        UserEntity changed = user(USER_A, "ESTUDIANTE");
        UserEntity unchanged = user(USER_B, "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(changed, unchanged));

        roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(NOW, changed.getValidFrom());
        assertNull(unchanged.getValidFrom());
        publishedEvents(2);
    }

        /**
     * A custom role that exists in the table can be assigned
     */
    @Test
    void changeRoles_customRoleThatExists_isAssigned() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("MONITOR_EVACUACION"), null), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE", "MONITOR_EVACUACION"), response.resultados().get(0).roles());
        verify(roleRepository).findAllByNameIn(Set.of("MONITOR_EVACUACION"));
        publishedEvents(1);
    }

        /**
     * Removing a role leaves the user with the others
     */
    @Test
    void changeRoles_removeRole_keepsTheOthers() {
        UserEntity target = user(USER_A, "ESTUDIANTE", "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), null, List.of("PROFESOR")), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE"), response.resultados().get(0).roles());
        assertEquals(List.of("ESTUDIANTE"), target.getRoleNames());
        assertEquals("agregados=;retirados=PROFESOR", publishedEvents(1).get(0).detalle());
    }

        /**
     * Adding and removing in the same request allows replacing one role with another
     */
    @Test
    void changeRoles_addAndRemove_replacesTheRole() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("BRIGADISTA"), List.of("ESTUDIANTE")), CORRELATION_ID);

        assertEquals(List.of("BRIGADISTA"), response.resultados().get(0).roles());
    }

        /**
     * The resulting roles are delivered in alphabetical order, regardless of the order in which they were added
     */
    @Test
    void changeRoles_resultingRolesAreSortedAlphabetically() {
        UserEntity target = user(USER_A, "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("ORGANIZADOR", "ADMINISTRATIVO"), null),
                CORRELATION_ID);

        assertEquals(List.of("ADMINISTRATIVO", "ORGANIZADOR", "PROFESOR"),
                response.resultados().get(0).roles());
    }

        /**
     * Several users in a single operation: the response follows the order of the request and one event per user is
     * audited
     */
    @Test
    void changeRoles_severalUsers_respondsInRequestOrderAndAuditsEach() {
        UserEntity first = user(USER_A, "ESTUDIANTE");
        UserEntity second = user(USER_B, "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(first, second));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_B.toString(), USER_A.toString()), List.of("AUDITOR"), null), CORRELATION_ID);

        assertEquals(USER_B.toString(), response.resultados().get(0).id());
        assertEquals(List.of("AUDITOR", "PROFESOR"), response.resultados().get(0).roles());
        assertEquals(USER_A.toString(), response.resultados().get(1).id());
        assertEquals(List.of("AUDITOR", "ESTUDIANTE"), response.resultados().get(1).roles());

        List<AuditEvent> events = publishedEvents(2);
        assertEquals("usuario:" + USER_B, events.get(0).recursoAfectado());
        assertEquals("usuario:" + USER_A, events.get(1).recursoAfectado());
        verify(userRepository).saveAllAndFlush(any());
    }

        /**
     * The repository is queried only once with the requested ids, without repeated ones
     */
    @Test
    void changeRoles_looksUpTheDistinctIdsInOneQuery() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository).findAllByIdInOrderById(captor.capture());
        assertEquals(List.of(USER_A), new ArrayList<>(captor.getValue()));
        publishedEvents(1);
    }

        /**
     * Adding a role that the user already has, or removing one that the user does not have, changes nothing: the row of
     * the user is not rewritten, but the request is audited
     */
    @Test
    void changeRoles_noEffectiveChange_doesNotSaveButAudits() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("ESTUDIANTE"), List.of("PROFESOR")), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE"), response.resultados().get(0).roles());
        verify(userRepository, never()).saveAllAndFlush(any());
        assertEquals("agregados=;retirados=", publishedEvents(1).get(0).detalle());
    }

        /**
     * Only the users whose roles actually change are persisted
     */
    @Test
    void changeRoles_savesOnlyTheUsersThatChange() {
        UserEntity changes = user(USER_A, "ESTUDIANTE");
        UserEntity unchanged = user(USER_B, "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(changes, unchanged));

        roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        verify(userRepository).saveAllAndFlush(List.of(changes));
        publishedEvents(2);
    }

        /**
     * A user without any role is treated as a user without current roles
     */
    @Test
    void changeRoles_userWithoutRoles_isTreatedAsEmpty() {
        UserEntity target = user(USER_A);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(List.of("PROFESOR"), response.resultados().get(0).roles());
        assertEquals("agregados=PROFESOR;retirados=", publishedEvents(1).get(0).detalle());
    }

    // ------------------------------------------------------------------ A1, A2

        /**
     * A1 / MR-05 / RN-MS01-12: the administrator cannot modify their own roles. It answers 403, does not touch the
     * database and audits a FALLO
     */
    @Test
    void changeRoles_adminModifiesItself_isForbiddenAndAudited() {
        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(ADMIN_ID.toString()), List.of("AUDITOR"), null), CORRELATION_ID));

        assertEquals("No puede modificar sus propios roles.", error.getMessage());
        verifyNoInteractions(userRepository);
        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditAction.CAMBIO_ROL, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertEquals("usuario:" + ADMIN_ID, event.recursoAfectado());
        assertEquals("AUTOMODIFICACION_DE_ROLES", event.detalle());
    }

        /**
     * A1: if the administrator themselves is among the target users, none is changed (atomic)
     */
    @Test
    void changeRoles_selfAmongSeveralTargets_changesNobody() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), ADMIN_ID.toString()), List.of("AUDITOR"), null),
                CORRELATION_ID));

        verifyNoInteractions(userRepository);
        publishedEvents(1);
    }

        /**
     * A2 / RN-MS01-11: whoever does not have the ms01:user:roles permission receives 403, the database is not touched
     * and a FALLO is audited without an affected user
     */
    @Test
    void changeRoles_nonAdmin_isForbiddenAndAudited() {
        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> roleService.changeRoles(requesterWith("ESTUDIANTE"),
                        request(List.of(USER_A.toString()), List.of("ADMIN"), null), CORRELATION_ID));

        assertEquals("No cuenta con los permisos suficientes para realizar esta operación.", error.getMessage());
        verifyNoInteractions(userRepository);
        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertNull(event.recursoAfectado());
        assertEquals("SIN_PERMISO", event.detalle());
    }

        /**
     * MR-01: no role inherits from another, so combining COORDINADOR and AUDITOR does not give permission to change
     * roles
     */
    @Test
    void changeRoles_rolesWithoutTheRolesPermission_areForbiddenEvenCombined() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(
                requesterWith("COORDINADOR", "AUDITOR"),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository);
    }

        /**
     * The permission decides, not the name of the role: a custom role with ms01:user:roles can change roles
     */
    @Test
    void changeRoles_customRoleWithTheUserRolesPermission_isAllowed() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        RequesterIdentity support = TestIdentities.withPermissions(ADMIN_ID, Set.of("SOPORTE"),
                "ms01:user:roles");

        ChangeRolesResponse response = roleService.changeRoles(support,
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        publishedEvents(1);
    }

        /**
     * Whoever has the role name ADMIN but not the ms01:user:roles permission cannot change roles
     */
    @Test
    void changeRoles_adminNameWithoutThePermission_isForbidden() {
        RequesterIdentity withoutPermission = TestIdentities.withPermissions(ADMIN_ID, Set.of("ADMIN"),
                "ms01:user:read");

        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(withoutPermission,
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository);
    }

        /**
     * Whoever has ADMIN together with other roles can change roles (MR-01: union of permissions)
     */
    @Test
    void changeRoles_adminWithOtherRoles_isAllowed() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(requesterWith("ESTUDIANTE", "ADMIN"),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        publishedEvents(1);
    }

        /**
     * The authorization is checked before the body: a non-administrator with an invalid body receives 403 and not 400
     */
    @Test
    void changeRoles_nonAdminWithInvalidBody_getsForbiddenFirst() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(
                requesterWith("PROFESOR"), request(null, null, null), CORRELATION_ID));

        publishedEvents(1);
    }

    // ------------------------------------------------------------------ A3

        /**
     * A3: if any user does not exist it answers 404, no change is applied (not even to those that do exist) and a FALLO
     * is audited for each non-existent id
     */
    @Test
    void changeRoles_unknownUser_isNotFoundAndChangesNobody() {
        UserEntity existing = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(existing));

        UsersNotFoundException error = assertThrows(UsersNotFoundException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null),
                        CORRELATION_ID));

        assertEquals("Usuario no encontrado.", error.getMessage());
        assertEquals(List.of(USER_B), error.getMissingIds());
        assertEquals(List.of("ESTUDIANTE"), existing.getRoleNames());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("usuario:" + USER_B, event.recursoAfectado());
        assertEquals("USUARIO_NO_ENCONTRADO", event.detalle());
    }

    // ------------------------------------------------------------------ A4

        /**
     * A4 / RN-MS01-13: if the result leaves a user without roles it answers 400, applies no change and audits a FALLO
     */
    @Test
    void changeRoles_userWouldLoseAllRoles_isRejected() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), null, List.of("ESTUDIANTE")), CORRELATION_ID));

        assertEquals("Cada usuario debe conservar al menos un rol.", error.getMessage());
        assertEquals(List.of("ESTUDIANTE"), target.getRoleNames());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("usuario:" + USER_A, event.recursoAfectado());
        assertEquals("USUARIO_SIN_ROLES", event.detalle());
    }

        /**
     * Atomicity: if one of several users would be left without roles, none of the others is modified
     */
    @Test
    void changeRoles_oneOfSeveralWouldBeEmpty_changesNobody() {
        UserEntity safe = user(USER_A, "ESTUDIANTE", "PROFESOR");
        UserEntity risky = user(USER_B, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(safe, risky));

        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), null, List.of("ESTUDIANTE")),
                CORRELATION_ID));

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), safe.getRoleNames());
        assertEquals(List.of("ESTUDIANTE"), risky.getRoleNames());
        verify(userRepository, never()).saveAllAndFlush(any());
        assertEquals("usuario:" + USER_B, publishedEvents(1).get(0).recursoAfectado());
    }

        /**
     * Removing the only role is allowed if another one is added in the same request
     */
    @Test
    void changeRoles_removingTheOnlyRoleWhileAddingAnother_isAllowed() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), List.of("ESTUDIANTE")), CORRELATION_ID);

        assertEquals(List.of("PROFESOR"), response.resultados().get(0).roles());
        publishedEvents(1);
    }

    // ------------------------------------------------------------------ limit of roles per user

        /**
     * Generates valid and distinct role names
     *
     * @param count number of names
     * @return the names ROL_01, ROL_02, ...
     */
    private String[] roleNames(int count) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = String.format("ROL_%02d", i + 1);
        }
        return names;
    }

        /**
     * RN-MS01-40: a user can end up with exactly 50 roles
     */
    @Test
    void changeRoles_userReachingExactlyTheMaximum_isAllowed() {
        UserEntity target = user(USER_A, roleNames(GlobalValues.MAX_ROLES_PER_USER - 1));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(GlobalValues.MAX_ROLES_PER_USER, response.resultados().get(0).roles().size());
        assertEquals(GlobalValues.MAX_ROLES_PER_USER, target.getRoles().size());
        verify(userRepository).saveAllAndFlush(any());
        publishedEvents(1);
    }

        /**
     * RN-MS01-40: if the result would leave the user with more than 50 roles it answers 422, changes nothing and audits
     * a FALLO
     */
    @Test
    void changeRoles_userExceedingTheMaximum_isRejectedAndAudited() {
        UserEntity target = user(USER_A, roleNames(GlobalValues.MAX_ROLES_PER_USER));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        assertEquals("Un usuario no puede tener más de 50 roles.", error.getMessage());
        assertEquals(GlobalValues.MAX_ROLES_PER_USER, target.getRoles().size());
        assertNull(target.getValidFrom());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("usuario:" + USER_A, event.recursoAfectado());
        assertEquals("MAXIMO_DE_ROLES_POR_USUARIO", event.detalle());
    }

        /**
     * Atomicity: if one of several users would exceed the limit, none of the others is modified
     */
    @Test
    void changeRoles_oneOfSeveralExceedsTheMaximum_changesNobody() {
        UserEntity safe = user(USER_A, "ESTUDIANTE");
        UserEntity full = user(USER_B, roleNames(GlobalValues.MAX_ROLES_PER_USER));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(safe, full));

        assertThrows(BusinessRuleException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        assertEquals(List.of("ESTUDIANTE"), safe.getRoleNames());
        assertEquals(GlobalValues.MAX_ROLES_PER_USER, full.getRoles().size());
        assertNull(safe.getValidFrom());
        verify(userRepository, never()).saveAllAndFlush(any());
        List<AuditEvent> events = publishedEvents(1);
        assertEquals("usuario:" + USER_B, events.get(0).recursoAfectado());
    }

        /**
     * RN-MS01-40: the agregar and retirar lists have no limit per operation, only the total roles of the user
     */
    @Test
    void changeRoles_listsWithMoreThanEightRoles_areAccepted() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        List<String> toAdd = List.of(roleNames(30));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), toAdd, null), CORRELATION_ID);

        assertEquals(31, response.resultados().get(0).roles().size());
        assertEquals(31, target.getRoles().size());
        publishedEvents(1);
    }

        /**
     * Removing many roles at once is also valid as long as the user keeps at least one
     */
    @Test
    void changeRoles_removingManyRolesAtOnce_isAllowed() {
        String[] current = roleNames(40);
        UserEntity target = user(USER_A, current);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        List<String> toRemove = List.of(Arrays.copyOf(current, 39));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), null, toRemove), CORRELATION_ID);

        assertEquals(List.of("ROL_40"), response.resultados().get(0).roles());
        publishedEvents(1);
    }

    // ------------------------------------------------------------------ validation, audit, errors

        /**
     * An invalid body answers 400 without querying users or auditing (there was no change to audit)
     */
    @Test
    void changeRoles_invalidBody_isRejectedBeforeTouchingAnything() {
        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("super-usuario"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository, auditPublisher);
    }

        /**
     * A role with a valid format that does not exist in the table answers 400 in the agregar field, without querying
     * users or auditing
     */
    @Test
    void changeRoles_roleToAddThatDoesNotExist_isRejected() {
        when(roleRepository.findAllByNameIn(Set.of("SUPERUSUARIO"))).thenReturn(List.of());

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), List.of("SUPERUSUARIO"), null), CORRELATION_ID));

        assertEquals("Datos inválidos.", error.getMessage());
        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals("Seleccione roles válidos.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(userRepository, auditPublisher);
    }

        /**
     * A role to remove that does not exist in the table answers 400 in the retirar field
     */
    @Test
    void changeRoles_roleToRemoveThatDoesNotExist_isRejected() {
        when(roleRepository.findAllByNameIn(Set.of("FANTASMA"))).thenReturn(List.of());

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), null, List.of("FANTASMA")), CORRELATION_ID));

        assertEquals("retirar", error.getErrors().get(0).campo());
        verifyNoInteractions(userRepository, auditPublisher);
    }

        /**
     * If both agregar and retirar carry non-existent roles, the error carries one detail per list
     */
    @Test
    void changeRoles_bothListsWithUnknownRoles_reportsBothFields() {
        when(roleRepository.findAllByNameIn(Set.of("NUEVO", "VIEJO"))).thenReturn(List.of());

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), List.of("NUEVO"), List.of("VIEJO")), CORRELATION_ID));

        assertEquals(2, error.getErrors().size());
    }

        /**
     * An invalid or absent X-Correlation-ID is replaced by a generated one, which is used in the audit
     */
    @Test
    void changeRoles_invalidOrMissingCorrelationId_generatesOne() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        roleService.changeRoles(admin(), request(List.of(USER_A.toString()), List.of("PROFESOR"), null),
                "no es valido porque tiene espacios");
        roleService.changeRoles(admin(), request(List.of(USER_A.toString()), List.of("AUDITOR"), null), null);

        List<AuditEvent> events = publishedEvents(2);
        for (AuditEvent event : events) {
            assertNotNull(event.correlationId());
            assertNotEquals("no es valido porque tiene espacios", event.correlationId());
            UUID.fromString(event.correlationId());
        }
    }

        /**
     * A valid X-Correlation-ID is used trimmed
     */
    @Test
    void changeRoles_validCorrelationId_isUsedTrimmed() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        roleService.changeRoles(admin(), request(List.of(USER_A.toString()), List.of("PROFESOR"), null),
                "  c19b472d-7a2a-4d3b-a12b-abcdef123456  ");

        assertEquals("c19b472d-7a2a-4d3b-a12b-abcdef123456", publishedEvents(1).get(0).correlationId());
    }

        /**
     * RNF-MS01-10: if MS08 fails, the response to the client does not change
     */
    @Test
    void changeRoles_auditFailure_doesNotChangeTheResponse() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("MS08 unavailable")).when(auditPublisher).publish(any());

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), response.resultados().get(0).roles());
    }

        /**
     * If the persistence fails no success event is emitted
     */
    @Test
    void changeRoles_persistenceFailure_emitsNoSuccessEvent() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("database failure")).when(userRepository).saveAllAndFlush(any());

        assertThrows(IllegalStateException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(auditPublisher);
    }

        /**
     * The role change runs in a single transaction, which is what makes it atomic
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void changeRoles_runsInOneTransaction() throws NoSuchMethodException {
        assertTrue(RoleServiceImpl.class
                .getMethod("changeRoles", RequesterIdentity.class, ChangeRolesRequest.class, String.class)
                .isAnnotationPresent(Transactional.class));
    }
}
