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
import java.util.HashSet;
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
 * RF-MS01-05 / historias KAIR-36 y KAIR-41: consulta de roles con permisos, flujo base del cambio de roles,
 * flujos alternos A1 a A4, atomicidad, auditoria CAMBIO_ROL y reglas RN-MS01-11 a RN-MS01-14
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
     * Crea el servicio con un reloj fijo para que la fecha de los eventos y del corte de sesión sea predecible,
     * por defecto todos los roles pedidos existen
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        roleService = new RoleServiceImpl(userRepository, roleRepository, new AuditRecorder(auditPublisher, clock),
                FixedDatabaseTime.at(NOW));
        lenient().when(roleRepository.countByNameIn(any()))
                .thenAnswer(invocation -> (long) ((Collection<?>) invocation.getArgument(0)).size());
    }

    /**
     * Identidad de un administrador
     *
     * @return identidad con el rol ADMIN
     */
    private RequesterIdentity admin() {
        return TestIdentities.of(ADMIN_ID, "ADMIN");
    }

    /**
     * Identidad de un usuario con los roles indicados
     *
     * @param roles roles del solicitante
     * @return identidad con esos roles
     */
    private RequesterIdentity requesterWith(String... roles) {
        return TestIdentities.of(ADMIN_ID, roles);
    }

    /**
     * Rol guardado con los permisos indicados
     *
     * @param name nombre del rol
     * @param permissions códigos de permiso del rol
     * @return la entidad del rol
     */
    private RoleEntity role(String name, String... permissions) {
        RoleEntity entity = new RoleEntity();
        entity.setName(name);
        entity.setDescription("Rol " + name);
        entity.setPermissions(Arrays.asList(permissions));
        return entity;
    }

    /**
     * Usuario con roles modificables, como los que entrega Hibernate
     *
     * @param id identificador del usuario
     * @param roles roles actuales
     * @return la entidad
     */
    private UserEntity user(UUID id, String... roles) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail(id + "@mail.escuelaing.edu.co");
        entity.setRoles(new HashSet<>(List.of(roles)));
        return entity;
    }

    /**
     * Arma el cuerpo de la peticion
     *
     * @param ids identificadores de usuario
     * @param add nombres de roles a agregar
     * @param remove nombres de roles a retirar
     * @return la peticion
     */
    private ChangeRolesRequest request(List<String> ids, List<String> add, List<String> remove) {
        ChangeRolesRequest request = new ChangeRolesRequest();
        request.setIds(ids);
        request.setAgregar(add);
        request.setRetirar(remove);
        return request;
    }

    /**
     * Captura los eventos de auditoria publicados
     *
     * @param expected cantidad de eventos que debieron publicarse
     * @return los eventos, en el orden en que se publicaron
     */
    private List<AuditEvent> publishedEvents(int expected) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------ GET /roles

    /**
     * Quien tiene el permiso ms01:role:read_all recibe todos los roles guardados, ordenados por nombre, cada uno
     * con los códigos de sus permisos
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
     * El permiso decide, no el nombre del rol: un rol personalizado con ms01:role:read_all también recibe el
     * catálogo completo
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
     * Quien no tiene ms01:role:read_all recibe solo sus propios roles, ordenados por nombre
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
     * Los códigos guardados que ya no existen en el catálogo no se entregan
     */
    @Test
    void getRoles_ignoresStoredCodesOutsideTheCatalog() {
        when(roleRepository.findAllByNameIn(any())).thenReturn(List.of(
                role("BRIGADISTA", "ms05:panel:read_own", "ms99:viejo:permiso")));

        List<RolePermissionsResponse> roles = roleService.getRoles(requesterWith("BRIGADISTA"));

        assertEquals(List.of("ms05:panel:read_own"), roles.get(0).permisos());
    }

    /**
     * Un rol del solicitante que ya no existe en la tabla no aparece en la respuesta
     */
    @Test
    void getRoles_roleMissingFromTheTable_isOmitted() {
        when(roleRepository.findAllByNameIn(any())).thenReturn(List.of());

        assertTrue(roleService.getRoles(requesterWith("AUDITOR")).isEmpty());
    }

    // ------------------------------------------------------------------ PATCH /users/roles: flujo base

    /**
     * Flujo base: agregar un rol responde con los roles resultantes, persiste el cambio y audita un
     * evento EXITO con quien, a quien y los roles anterior y nuevo
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
        assertEquals(Set.of("ESTUDIANTE", "PROFESOR"), target.getRoles());
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
     * El usuario modificado recibe el corte de sesión con la hora del reloj truncada a segundos (RF-MS01-11)
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
     * El corte es la hora de la base de datos, se consulta una sola vez y se aplica a todos los usuarios
     * modificados
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
     * Un usuario cuyos roles no cambian no recibe corte de sesión
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
     * Un rol personalizado que existe en la tabla se puede asignar
     */
    @Test
    void changeRoles_customRoleThatExists_isAssigned() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("MONITOR_EVACUACION"), null), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE", "MONITOR_EVACUACION"), response.resultados().get(0).roles());
        verify(roleRepository).countByNameIn(Set.of("MONITOR_EVACUACION"));
        publishedEvents(1);
    }

    /**
     * Retirar un rol deja al usuario con los demas
     */
    @Test
    void changeRoles_removeRole_keepsTheOthers() {
        UserEntity target = user(USER_A, "ESTUDIANTE", "PROFESOR");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), null, List.of("PROFESOR")), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE"), response.resultados().get(0).roles());
        assertEquals(Set.of("ESTUDIANTE"), target.getRoles());
        assertEquals("agregados=;retirados=PROFESOR", publishedEvents(1).get(0).detalle());
    }

    /**
     * Agregar y retirar en la misma peticion permite reemplazar un rol por otro
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
     * Los roles resultantes se entregan en orden alfabetico, sin importar el orden en que se agregaron
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
     * Varios usuarios en una sola operacion: se responde en el orden de la peticion y se audita un
     * evento por usuario
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
     * Se consulta el repositorio una sola vez con los ids pedidos, sin repetidos
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
     * Agregar un rol que ya tiene, o retirar uno que no tiene, no cambia nada: no se reescribe la fila
     * del usuario, pero la peticion se audita
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
     * Solo se persisten los usuarios cuyos roles realmente cambian
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
     * Un usuario sin ningun rol se trata como un usuario sin roles actuales
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
     * A1 / MR-05 / RN-MS01-12: el administrador no puede modificar sus propios roles. Responde 403, no
     * toca la base de datos y audita un FALLO
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
     * A1: si entre los usuarios objetivo esta el propio administrador, no se cambia a ninguno (atomico)
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
     * A2 / RN-MS01-11: quien no tiene el permiso ms01:user:roles recibe 403, no se toca la base de datos y se
     * audita un FALLO sin usuario afectado
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
     * MR-01: ningun rol hereda de otro, asi que combinar COORDINADOR y AUDITOR no da permiso de cambiar
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
     * El permiso decide, no el nombre del rol: un rol personalizado con ms01:user:roles puede cambiar roles
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
     * Quien tiene el nombre de rol ADMIN pero no el permiso ms01:user:roles no puede cambiar roles
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
     * Quien tiene ADMIN junto con otros roles si puede cambiar roles (MR-01: union de permisos)
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
     * La autorizacion se comprueba antes que el cuerpo: un no administrador con un cuerpo invalido recibe
     * 403 y no 400
     */
    @Test
    void changeRoles_nonAdminWithInvalidBody_getsForbiddenFirst() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(
                requesterWith("PROFESOR"), request(null, null, null), CORRELATION_ID));

        publishedEvents(1);
    }

    // ------------------------------------------------------------------ A3

    /**
     * A3: si algun usuario no existe responde 404, no se aplica ningun cambio (ni a los que si existen)
     * y se audita un FALLO por cada id inexistente
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
        assertEquals(Set.of("ESTUDIANTE"), existing.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("usuario:" + USER_B, event.recursoAfectado());
        assertEquals("USUARIO_NO_ENCONTRADO", event.detalle());
    }

    // ------------------------------------------------------------------ A4

    /**
     * A4 / RN-MS01-13: si el resultado deja a un usuario sin roles responde 400, no aplica ningun cambio
     * y audita un FALLO
     */
    @Test
    void changeRoles_userWouldLoseAllRoles_isRejected() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), null, List.of("ESTUDIANTE")), CORRELATION_ID));

        assertEquals("Cada usuario debe conservar al menos un rol.", error.getMessage());
        assertEquals(Set.of("ESTUDIANTE"), target.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("usuario:" + USER_A, event.recursoAfectado());
        assertEquals("USUARIO_SIN_ROLES", event.detalle());
    }

    /**
     * Atomicidad: si uno de varios usuarios quedaria sin roles, ninguno de los demas se modifica
     */
    @Test
    void changeRoles_oneOfSeveralWouldBeEmpty_changesNobody() {
        UserEntity safe = user(USER_A, "ESTUDIANTE", "PROFESOR");
        UserEntity risky = user(USER_B, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(safe, risky));

        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), null, List.of("ESTUDIANTE")),
                CORRELATION_ID));

        assertEquals(Set.of("ESTUDIANTE", "PROFESOR"), safe.getRoles());
        assertEquals(Set.of("ESTUDIANTE"), risky.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());
        assertEquals("usuario:" + USER_B, publishedEvents(1).get(0).recursoAfectado());
    }

    /**
     * Retirar el unico rol esta permitido si en la misma peticion se agrega otro
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

    // ------------------------------------------------------------------ tope de roles por usuario

    /**
     * Genera nombres de rol válidos y distintos
     *
     * @param count cantidad de nombres
     * @return los nombres ROL_01, ROL_02, ...
     */
    private String[] roleNames(int count) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = String.format("ROL_%02d", i + 1);
        }
        return names;
    }

    /**
     * RN-MS01-40: un usuario puede quedar con exactamente 50 roles
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
     * RN-MS01-40: si el resultado dejaría al usuario con más de 50 roles responde 422, no cambia nada y audita
     * un FALLO
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
     * Atomicidad: si uno de varios usuarios superaría el tope, ninguno de los demás se modifica
     */
    @Test
    void changeRoles_oneOfSeveralExceedsTheMaximum_changesNobody() {
        UserEntity safe = user(USER_A, "ESTUDIANTE");
        UserEntity full = user(USER_B, roleNames(GlobalValues.MAX_ROLES_PER_USER));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(safe, full));

        assertThrows(BusinessRuleException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        assertEquals(Set.of("ESTUDIANTE"), safe.getRoles());
        assertEquals(GlobalValues.MAX_ROLES_PER_USER, full.getRoles().size());
        assertNull(safe.getValidFrom());
        verify(userRepository, never()).saveAllAndFlush(any());
        List<AuditEvent> events = publishedEvents(1);
        assertEquals("usuario:" + USER_B, events.get(0).recursoAfectado());
    }

    /**
     * RN-MS01-40: las listas agregar y retirar no tienen límite por operación, solo el total de roles del usuario
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
     * Retirar muchos roles de una sola vez también es válido mientras el usuario conserve al menos uno
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

    // ------------------------------------------------------------------ validacion, auditoria, errores

    /**
     * Un cuerpo invalido responde 400 sin consultar usuarios ni auditar (no hubo cambio que auditar)
     */
    @Test
    void changeRoles_invalidBody_isRejectedBeforeTouchingAnything() {
        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("super-usuario"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository, auditPublisher);
    }

    /**
     * Un rol con formato válido que no existe en la tabla responde 400 en el campo agregar, sin consultar
     * usuarios ni auditar
     */
    @Test
    void changeRoles_roleToAddThatDoesNotExist_isRejected() {
        when(roleRepository.countByNameIn(Set.of("SUPERUSUARIO"))).thenReturn(0L);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), List.of("SUPERUSUARIO"), null), CORRELATION_ID));

        assertEquals("Datos inválidos.", error.getMessage());
        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals("Seleccione roles válidos.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(userRepository, auditPublisher);
    }

    /**
     * Un rol a retirar que no existe en la tabla responde 400 en el campo retirar
     */
    @Test
    void changeRoles_roleToRemoveThatDoesNotExist_isRejected() {
        when(roleRepository.countByNameIn(Set.of("FANTASMA"))).thenReturn(0L);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), null, List.of("FANTASMA")), CORRELATION_ID));

        assertEquals("retirar", error.getErrors().get(0).campo());
        verifyNoInteractions(userRepository, auditPublisher);
    }

    /**
     * Si tanto agregar como retirar traen roles inexistentes, el error trae un detalle por lista
     */
    @Test
    void changeRoles_bothListsWithUnknownRoles_reportsBothFields() {
        when(roleRepository.countByNameIn(Set.of("NUEVO"))).thenReturn(0L);
        when(roleRepository.countByNameIn(Set.of("VIEJO"))).thenReturn(0L);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), List.of("NUEVO"), List.of("VIEJO")), CORRELATION_ID));

        assertEquals(2, error.getErrors().size());
    }

    /**
     * Un X-Correlation-ID invalido o ausente se reemplaza por uno generado, que se usa en la auditoria
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
     * Un X-Correlation-ID valido se usa recortado
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
     * RNF-MS01-10: si MS08 falla, la respuesta al cliente no cambia
     */
    @Test
    void changeRoles_auditFailure_doesNotChangeTheResponse() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("MS08 no disponible")).when(auditPublisher).publish(any());

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), response.resultados().get(0).roles());
    }

    /**
     * Si falla la persistencia no se emite ningun evento de exito
     */
    @Test
    void changeRoles_persistenceFailure_emitsNoSuccessEvent() {
        UserEntity target = user(USER_A, "ESTUDIANTE");
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("fallo de base de datos")).when(userRepository).saveAllAndFlush(any());

        assertThrows(IllegalStateException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(auditPublisher);
    }

    /**
     * El cambio de roles corre en una sola transaccion, que es lo que lo hace atomico
     *
     * @throws NoSuchMethodException si el metodo no existe
     */
    @Test
    void changeRoles_runsInOneTransaction() throws NoSuchMethodException {
        assertTrue(RoleServiceImpl.class
                .getMethod("changeRoles", RequesterIdentity.class, ChangeRolesRequest.class, String.class)
                .isAnnotationPresent(Transactional.class));
    }
}
