package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleServiceImpl;

/**
 * RF-MS01-05 / historias KAIR-36 y KAIR-41: consulta de roles con permisos, flujo base del cambio de roles,
 * flujos alternos A1 a A4, atomicidad, auditoria CAMBIO_ROL y reglas RN-MS01-11 a RN-MS01-14.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID USER_A = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final UUID USER_B = UUID.fromString("7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d");
    private static final String CORRELATION_ID = "corr-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleServiceImpl roleService;

    /**
     * Crea el servicio con un reloj fijo para que la fecha de los eventos sea predecible.
     */
    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(userRepository, auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Identidad de un administrador.
     *
     * @return identidad con el rol ADMIN
     */
    private RequesterIdentity admin() {
        return new RequesterIdentity(ADMIN_ID, EnumSet.of(UserRole.ADMIN));
    }

    /**
     * Identidad de un usuario con los roles indicados.
     *
     * @param roles roles del solicitante
     * @return identidad con esos roles
     */
    private RequesterIdentity requesterWith(UserRole... roles) {
        EnumSet<UserRole> set = EnumSet.noneOf(UserRole.class);
        set.addAll(List.of(roles));
        return new RequesterIdentity(ADMIN_ID, set);
    }

    /**
     * Usuario con roles modificables, como los que entrega Hibernate.
     *
     * @param id identificador del usuario
     * @param roles roles actuales
     * @return la entidad
     */
    private UserEntity user(UUID id, UserRole... roles) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail(id + "@mail.escuelaing.edu.co");
        entity.setRoles(new HashSet<>(List.of(roles)));
        return entity;
    }

    /**
     * Arma el cuerpo de la peticion.
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
     * Captura los eventos de auditoria publicados.
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
     * El ADMIN recibe los ocho roles del catalogo, cada uno con los codigos de sus permisos.
     */
    @Test
    void getRoles_admin_returnsTheWholeCatalog() {
        List<RolePermissionsResponse> roles = roleService.getRoles(admin());

        assertEquals(8, roles.size());
        for (int i = 0; i < roles.size(); i++) {
            assertEquals(UserRole.values()[i], roles.get(i).rol());
            assertEquals(UserRole.values()[i].getPermissionCodes(), roles.get(i).permisos());
        }
        assertTrue(roles.get(6).permisos().contains("ms01:user:roles"));
        verifyNoInteractions(userRepository, auditPublisher);
    }

    /**
     * Un usuario que no es administrador recibe solo su propio rol.
     */
    @Test
    void getRoles_nonAdmin_returnsOnlyItsOwnRole() {
        List<RolePermissionsResponse> roles = roleService.getRoles(requesterWith(UserRole.BRIGADISTA));

        assertEquals(1, roles.size());
        assertEquals(UserRole.BRIGADISTA, roles.get(0).rol());
        assertTrue(roles.get(0).permisos().contains("ms05:panel:read_own"));
    }

    /**
     * Un usuario con varios roles recibe cada uno de ellos y ningun otro.
     */
    @Test
    void getRoles_userWithSeveralRoles_returnsEachOfItsRoles() {
        List<RolePermissionsResponse> roles = roleService.getRoles(
                requesterWith(UserRole.PROFESOR, UserRole.ESTUDIANTE));

        assertEquals(2, roles.size());
        assertEquals(UserRole.ESTUDIANTE, roles.get(0).rol());
        assertEquals(UserRole.PROFESOR, roles.get(1).rol());
    }

    /**
     * El AUDITOR puede listar usuarios pero no ve el catalogo completo de roles.
     */
    @Test
    void getRoles_auditor_doesNotSeeTheWholeCatalog() {
        List<RolePermissionsResponse> roles = roleService.getRoles(requesterWith(UserRole.AUDITOR));

        assertEquals(1, roles.size());
        assertEquals(UserRole.AUDITOR, roles.get(0).rol());
    }

    /**
     * Un usuario que ademas tiene ADMIN recibe el catalogo completo (MR-01: union de permisos).
     */
    @Test
    void getRoles_userWithAdminAmongItsRoles_returnsTheWholeCatalog() {
        List<RolePermissionsResponse> roles = roleService.getRoles(
                requesterWith(UserRole.ESTUDIANTE, UserRole.ADMIN));

        assertEquals(8, roles.size());
    }

    // ------------------------------------------------------------------ PATCH /users/roles: flujo base

    /**
     * Flujo base: agregar un rol responde con los roles resultantes, persiste el cambio y audita un
     * evento EXITO con quien, a quien y los roles anterior y nuevo.
     */
    @Test
    void changeRoles_addRole_updatesPersistsAndAudits() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        assertEquals(USER_A.toString(), response.resultados().get(0).id());
        assertEquals(List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), response.resultados().get(0).roles());
        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), target.getRoles());
        verify(userRepository).saveAllAndFlush(List.of(target));

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditAction.CAMBIO_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertEquals("users/" + USER_A, event.recursoAfectado());
        assertEquals("rol_anterior=ESTUDIANTE;rol_nuevo=ESTUDIANTE,PROFESOR", event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertEquals(NOW, event.fecha());
        assertNull(event.correo());
    }

    /**
     * Retirar un rol deja al usuario con los demas.
     */
    @Test
    void changeRoles_removeRole_keepsTheOthers() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE, UserRole.PROFESOR);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), null, List.of("PROFESOR")), CORRELATION_ID);

        assertEquals(List.of(UserRole.ESTUDIANTE), response.resultados().get(0).roles());
        assertEquals(Set.of(UserRole.ESTUDIANTE), target.getRoles());
        assertEquals("rol_anterior=ESTUDIANTE,PROFESOR;rol_nuevo=ESTUDIANTE", publishedEvents(1).get(0).detalle());
    }

    /**
     * Agregar y retirar en la misma peticion permite reemplazar un rol por otro.
     */
    @Test
    void changeRoles_addAndRemove_replacesTheRole() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("BRIGADISTA"), List.of("ESTUDIANTE")), CORRELATION_ID);

        assertEquals(List.of(UserRole.BRIGADISTA), response.resultados().get(0).roles());
    }

    /**
     * Los roles resultantes se entregan en orden alfabetico, sin importar el orden en que se agregaron.
     */
    @Test
    void changeRoles_resultingRolesAreSortedAlphabetically() {
        UserEntity target = user(USER_A, UserRole.PROFESOR);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("ORGANIZADOR", "ADMINISTRATIVO"), null),
                CORRELATION_ID);

        assertEquals(List.of(UserRole.ADMINISTRATIVO, UserRole.ORGANIZADOR, UserRole.PROFESOR),
                response.resultados().get(0).roles());
    }

    /**
     * Varios usuarios en una sola operacion: se responde en el orden de la peticion y se audita un
     * evento por usuario.
     */
    @Test
    void changeRoles_severalUsers_respondsInRequestOrderAndAuditsEach() {
        UserEntity first = user(USER_A, UserRole.ESTUDIANTE);
        UserEntity second = user(USER_B, UserRole.PROFESOR);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(first, second));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_B.toString(), USER_A.toString()), List.of("AUDITOR"), null), CORRELATION_ID);

        assertEquals(USER_B.toString(), response.resultados().get(0).id());
        assertEquals(List.of(UserRole.AUDITOR, UserRole.PROFESOR), response.resultados().get(0).roles());
        assertEquals(USER_A.toString(), response.resultados().get(1).id());
        assertEquals(List.of(UserRole.AUDITOR, UserRole.ESTUDIANTE), response.resultados().get(1).roles());

        List<AuditEvent> events = publishedEvents(2);
        assertEquals("users/" + USER_B, events.get(0).recursoAfectado());
        assertEquals("users/" + USER_A, events.get(1).recursoAfectado());
        verify(userRepository).saveAllAndFlush(any());
    }

    /**
     * Se consulta el repositorio una sola vez con los ids pedidos, sin repetidos.
     */
    @Test
    void changeRoles_looksUpTheDistinctIdsInOneQuery() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
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
     * del usuario, pero la peticion se audita.
     */
    @Test
    void changeRoles_noEffectiveChange_doesNotSaveButAudits() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("ESTUDIANTE"), List.of("PROFESOR")), CORRELATION_ID);

        assertEquals(List.of(UserRole.ESTUDIANTE), response.resultados().get(0).roles());
        verify(userRepository, never()).saveAllAndFlush(any());
        assertEquals("rol_anterior=ESTUDIANTE;rol_nuevo=ESTUDIANTE", publishedEvents(1).get(0).detalle());
    }

    /**
     * Solo se persisten los usuarios cuyos roles realmente cambian.
     */
    @Test
    void changeRoles_savesOnlyTheUsersThatChange() {
        UserEntity changes = user(USER_A, UserRole.ESTUDIANTE);
        UserEntity unchanged = user(USER_B, UserRole.PROFESOR);
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

        assertEquals(List.of(UserRole.PROFESOR), response.resultados().get(0).roles());
        assertEquals("rol_anterior=;rol_nuevo=PROFESOR", publishedEvents(1).get(0).detalle());
    }

    // ------------------------------------------------------------------ A1, A2

    /**
     * A1 / MR-05 / RN-MS01-12: el administrador no puede modificar sus propios roles. Responde 403, no
     * toca la base de datos y audita un FALLO.
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
        assertEquals("users/" + ADMIN_ID, event.recursoAfectado());
        assertEquals("AUTOMODIFICACION_DE_ROLES", event.detalle());
    }

    /**
     * A1: si entre los usuarios objetivo esta el propio administrador, no se cambia a ninguno (atomico).
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
     * A2 / RN-MS01-11: quien no es administrador recibe 403, no se toca la base de datos y se audita un
     * FALLO sin usuario afectado.
     */
    @Test
    void changeRoles_nonAdmin_isForbiddenAndAudited() {
        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> roleService.changeRoles(requesterWith(UserRole.ESTUDIANTE),
                        request(List.of(USER_A.toString()), List.of("ADMIN"), null), CORRELATION_ID));

        assertEquals("No tiene permisos para realizar esta operación.", error.getMessage());
        verifyNoInteractions(userRepository);
        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertNull(event.recursoAfectado());
        assertEquals("SIN_PERMISO", event.detalle());
    }

    /**
     * MR-01: ningun rol hereda de otro, asi que combinar COORDINADOR y AUDITOR no da permiso de cambiar
     * roles.
     */
    @Test
    void changeRoles_rolesWithoutTheUserRolesPermission_areForbiddenEvenCombined() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(
                requesterWith(UserRole.COORDINADOR, UserRole.AUDITOR),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository);
    }

    /**
     * Quien tiene ADMIN junto con otros roles si puede cambiar roles (MR-01: union de permisos).
     */
    @Test
    void changeRoles_adminWithOtherRoles_isAllowed() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(requesterWith(UserRole.ESTUDIANTE, UserRole.ADMIN),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        publishedEvents(1);
    }

    /**
     * La autorizacion se comprueba antes que el cuerpo: un no administrador con un cuerpo invalido recibe
     * 403 y no 400.
     */
    @Test
    void changeRoles_nonAdminWithInvalidBody_getsForbiddenFirst() {
        assertThrows(ForbiddenOperationException.class, () -> roleService.changeRoles(
                requesterWith(UserRole.PROFESOR), request(null, null, null), CORRELATION_ID));

        publishedEvents(1);
    }

    // ------------------------------------------------------------------ A3

    /**
     * A3: si algun usuario no existe responde 404, no se aplica ningun cambio (ni a los que si existen)
     * y se audita un FALLO por cada id inexistente.
     */
    @Test
    void changeRoles_unknownUser_isNotFoundAndChangesNobody() {
        UserEntity existing = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(existing));

        UsersNotFoundException error = assertThrows(UsersNotFoundException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString(), USER_B.toString()), List.of("PROFESOR"), null),
                        CORRELATION_ID));

        assertEquals("Usuario no encontrado.", error.getMessage());
        assertEquals(List.of(USER_B), error.getMissingIds());
        assertEquals(Set.of(UserRole.ESTUDIANTE), existing.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("users/" + USER_B, event.recursoAfectado());
        assertEquals("USUARIO_NO_ENCONTRADO", event.detalle());
    }

    // ------------------------------------------------------------------ A4

    /**
     * A4 / RN-MS01-13: si el resultado deja a un usuario sin roles responde 400, no aplica ningun cambio
     * y audita un FALLO.
     */
    @Test
    void changeRoles_userWouldLoseAllRoles_isRejected() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> roleService.changeRoles(admin(),
                        request(List.of(USER_A.toString()), null, List.of("ESTUDIANTE")), CORRELATION_ID));

        assertEquals("Cada usuario debe conservar al menos un rol.", error.getMessage());
        assertEquals(Set.of(UserRole.ESTUDIANTE), target.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());

        AuditEvent event = publishedEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("users/" + USER_A, event.recursoAfectado());
        assertEquals("USUARIO_SIN_ROLES", event.detalle());
    }

    /**
     * Atomicidad: si uno de varios usuarios quedaria sin roles, ninguno de los demas se modifica.
     */
    @Test
    void changeRoles_oneOfSeveralWouldBeEmpty_changesNobody() {
        UserEntity safe = user(USER_A, UserRole.ESTUDIANTE, UserRole.PROFESOR);
        UserEntity risky = user(USER_B, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(safe, risky));

        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString(), USER_B.toString()), null, List.of("ESTUDIANTE")),
                CORRELATION_ID));

        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), safe.getRoles());
        assertEquals(Set.of(UserRole.ESTUDIANTE), risky.getRoles());
        verify(userRepository, never()).saveAllAndFlush(any());
        assertEquals("users/" + USER_B, publishedEvents(1).get(0).recursoAfectado());
    }

    /**
     * Retirar el unico rol esta permitido si en la misma peticion se agrega otro.
     */
    @Test
    void changeRoles_removingTheOnlyRoleWhileAddingAnother_isAllowed() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), List.of("ESTUDIANTE")), CORRELATION_ID);

        assertEquals(List.of(UserRole.PROFESOR), response.resultados().get(0).roles());
        publishedEvents(1);
    }

    // ------------------------------------------------------------------ validacion, auditoria, errores

    /**
     * Un cuerpo invalido responde 400 sin consultar la base de datos ni auditar (no hubo cambio que
     * auditar).
     */
    @Test
    void changeRoles_invalidBody_isRejectedBeforeTouchingAnything() {
        assertThrows(InvalidRequestException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("SUPERUSUARIO"), null), CORRELATION_ID));

        verifyNoInteractions(userRepository, auditPublisher);
    }

    /**
     * Un X-Correlation-ID invalido o ausente se reemplaza por uno generado, que se usa en la auditoria.
     */
    @Test
    void changeRoles_invalidOrMissingCorrelationId_generatesOne() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
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
     * Un X-Correlation-ID valido se usa recortado.
     */
    @Test
    void changeRoles_validCorrelationId_isUsedTrimmed() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        roleService.changeRoles(admin(), request(List.of(USER_A.toString()), List.of("PROFESOR"), null),
                "  c19b-472d-a12b  ");

        assertEquals("c19b-472d-a12b", publishedEvents(1).get(0).correlationId());
    }

    /**
     * RNF-MS01-10: si MS08 falla, la respuesta al cliente no cambia.
     */
    @Test
    void changeRoles_auditFailure_doesNotChangeTheResponse() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("MS08 no disponible")).when(auditPublisher).publish(any());

        ChangeRolesResponse response = roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID);

        assertEquals(List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), response.resultados().get(0).roles());
    }

    /**
     * Si falla la persistencia no se emite ningun evento de exito.
     */
    @Test
    void changeRoles_persistenceFailure_emitsNoSuccessEvent() {
        UserEntity target = user(USER_A, UserRole.ESTUDIANTE);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));
        doThrow(new IllegalStateException("fallo de base de datos")).when(userRepository).saveAllAndFlush(any());

        assertThrows(IllegalStateException.class, () -> roleService.changeRoles(admin(),
                request(List.of(USER_A.toString()), List.of("PROFESOR"), null), CORRELATION_ID));

        verifyNoInteractions(auditPublisher);
    }

    /**
     * El cambio de roles corre en una sola transaccion, que es lo que lo hace atomico.
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
