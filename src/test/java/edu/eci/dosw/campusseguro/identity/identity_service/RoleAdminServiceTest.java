package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleRequestValidator;

/**
 * RF-MS01-10: consulta, creación, edición, cambio de permisos y borrado de roles, las reglas RG-08 y RN-MS01-14,
 * la auditoría de cada operación y el corte de sesión de RF-MS01-11
 */
@ExtendWith(MockitoExtension.class)
class RoleAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00.750Z");
    private static final Instant CUT = Instant.parse("2026-11-11T12:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String MANAGE = "ms01:role:manage";
    private static final String PERM_A = "ms04:checkin:write";
    private static final String PERM_B = "ms04:checkin:read_own";
    private static final String PERM_C = "ms01:profile:read";

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleAdminServiceImpl service;

    /**
     * Crea el servicio con un reloj fijo para que la fecha de los eventos y de los cortes sea predecible
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RoleAdminServiceImpl(roleRepository, userRepository, new AuditRecorder(auditPublisher, clock),
                FixedDatabaseTime.at(NOW));
    }

    /**
     * Identidad con ms01:role:manage y ms01:role:read_all, la del rol ADMIN
     *
     * @return la identidad del administrador
     */
    private RequesterIdentity admin() {
        return TestIdentities.of(ADMIN_ID, "ADMIN");
    }

    /**
     * Identidad sin los permisos de gestión de roles
     *
     * @return la identidad de un estudiante
     */
    private RequesterIdentity student() {
        return TestIdentities.of(ADMIN_ID, "ESTUDIANTE");
    }

    /**
     * Rol guardado
     *
     * @param name nombre del rol
     * @param system true si es un rol de sistema
     * @param permissions códigos de permiso del rol
     * @return la entidad del rol
     */
    private RoleEntity role(String name, boolean system, String... permissions) {
        RoleEntity entity = new RoleEntity();
        entity.setName(name);
        entity.setDescription("Descripción de " + name);
        entity.setPermissions(Arrays.asList(permissions));
        entity.setSystem(system);
        return entity;
    }

    /**
     * Cuerpo de creación de rol
     *
     * @param name nombre del rol
     * @param description descripción del rol
     * @param permissions permisos del rol
     * @return la petición
     */
    private CreateRoleRequest createRequest(String name, String description, List<String> permissions) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        request.setPermisos(permissions);
        return request;
    }

    /**
     * Cuerpo de edición de rol
     *
     * @param name nombre del rol
     * @param description descripción del rol
     * @return la petición
     */
    private UpdateRoleRequest updateRequest(String name, String description) {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        return request;
    }

    /**
     * Cuerpo de cambio de permisos
     *
     * @param permissions permisos del rol
     * @return la petición
     */
    private UpdateRolePermissionsRequest permissionsRequest(List<String> permissions) {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermisos(permissions);
        return request;
    }

    /**
     * Captura el único evento de auditoría publicado
     *
     * @return el evento
     */
    private AuditEvent singleEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(1)).publish(captor.capture());
        return captor.getValue();
    }

    /**
     * Comprueba que se publicó un único evento FALLO con el motivo y el recurso indicados
     *
     * @param action acción esperada
     * @param resource recurso afectado esperado, null si no hay
     * @param reason motivo esperado
     */
    private void assertSingleFailure(AuditAction action, String resource, String reason) {
        AuditEvent event = singleEvent();
        assertEquals(action, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertEquals(resource, event.recursoAfectado());
        assertEquals(reason, event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
    }

    // ------------------------------------------------------------------ consulta

    /**
     * Quien tiene ms01:role:read_all recibe todos los roles con su detalle
     */
    @Test
    void listRoles_withReadAll_returnsEveryRoleWithItsDetail() {
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                role("ADMIN", true, MANAGE, "ms01:role:read_all"), role("MONITOR", false, PERM_A)));

        List<RoleDetailResponse> roles = service.listRoles(admin());

        assertEquals(2, roles.size());
        assertEquals("ADMIN", roles.get(0).rol());
        assertTrue(roles.get(0).system());
        assertEquals(List.of("ms01:role:manage", "ms01:role:read_all"), roles.get(0).permisos());
        assertEquals("MONITOR", roles.get(1).rol());
        assertEquals("Descripción de MONITOR", roles.get(1).descripcion());
        assertFalse(roles.get(1).system());
        verifyNoInteractions(auditPublisher);
    }

    /**
     * Sin ms01:role:read_all la lista de roles se prohíbe
     */
    @Test
    void listRoles_withoutReadAll_isForbidden() {
        assertThrows(ForbiddenOperationException.class, () -> service.listRoles(student()));

        verifyNoInteractions(roleRepository);
    }

    /**
     * Un rol se consulta por su nombre normalizado
     */
    @Test
    void getRole_normalizesThePathName() {
        when(roleRepository.findById("MONITOR_EVACUACION")).thenReturn(Optional.of(role("MONITOR_EVACUACION", false,
                PERM_A)));

        RoleDetailResponse response = service.getRole(admin(), "  monitor_evacuacion ");

        assertEquals("MONITOR_EVACUACION", response.rol());
        assertEquals(List.of(PERM_A), response.permisos());
    }

    /**
     * Los permisos guardados que ya no están en el catálogo no se entregan
     */
    @Test
    void getRole_ignoresPermissionsOutsideTheCatalog() {
        when(roleRepository.findById("VIEJO")).thenReturn(Optional.of(role("VIEJO", false, PERM_A, "ms99:x:y")));

        assertEquals(List.of(PERM_A), service.getRole(admin(), "VIEJO").permisos());
    }

    /**
     * Un rol inexistente responde 404
     */
    @Test
    void getRole_unknownRole_isNotFound() {
        when(roleRepository.findById("FANTASMA")).thenReturn(Optional.empty());

        RoleNotFoundException error = assertThrows(RoleNotFoundException.class,
                () -> service.getRole(admin(), "FANTASMA"));

        assertEquals("Rol no encontrado.", error.getMessage());
        assertEquals("FANTASMA", error.getRoleName());
    }

    /**
     * Un nombre con formato inválido en la ruta responde 400 y no consulta la tabla
     */
    @Test
    void getRole_invalidPathName_isRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.getRole(admin(), "rol-con-guiones"));

        assertEquals("rol", error.getErrors().get(0).campo());
        assertEquals("Rol no válido.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(roleRepository);
    }

    /**
     * Sin ms01:role:read_all la consulta de un rol se prohíbe antes de validar la ruta
     */
    @Test
    void getRole_withoutReadAll_isForbiddenBeforeValidatingThePath() {
        assertThrows(ForbiddenOperationException.class, () -> service.getRole(student(), "rol-con-guiones"));
    }

    /**
     * El catálogo de permisos se entrega ordenado por código, completo y con su descripción
     */
    @Test
    void listPermissions_returnsTheWholeCatalogSortedByCode() {
        List<PermissionItemResponse> permissions = service.listPermissions(admin());

        assertEquals(Permission.values().length, permissions.size());
        List<String> codes = permissions.stream().map(PermissionItemResponse::permiso).toList();
        assertEquals(codes.stream().sorted().toList(), codes);
        PermissionItemResponse manage = permissions.stream()
                .filter(item -> item.permiso().equals(MANAGE))
                .findFirst()
                .orElseThrow();
        assertEquals(Permission.MS01_ROLE_MANAGE.getDescription(), manage.descripcion());
        verifyNoInteractions(roleRepository, userRepository, auditPublisher);
    }

    /**
     * Sin ms01:role:read_all el catálogo de permisos se prohíbe
     */
    @Test
    void listPermissions_withoutReadAll_isForbidden() {
        assertThrows(ForbiddenOperationException.class, () -> service.listPermissions(student()));
    }

    // ------------------------------------------------------------------ creación

    /**
     * Crear un rol lo guarda como rol que no es de sistema, con nombre en mayúsculas y permisos ordenados, lo
     * audita y no registra corte de sesión
     */
    @Test
    void createRole_validRequest_savesAuditsAndSetsNoSessionCut() {
        when(roleRepository.existsById("MONITOR_EVACUACION")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(admin(),
                createRequest(" monitor_evacuacion ", "  Apoya la evacuación  ", List.of(PERM_A, PERM_C, PERM_B)),
                CORRELATION_ID);

        ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository).saveAndFlush(captor.capture());
        RoleEntity saved = captor.getValue();
        assertEquals("MONITOR_EVACUACION", saved.getName());
        assertEquals("Apoya la evacuación", saved.getDescription());
        assertEquals(List.of(PERM_C, PERM_B, PERM_A), saved.getPermissions());
        assertFalse(saved.isSystem());
        assertNull(saved.getValidFrom());
        assertEquals("MONITOR_EVACUACION", response.rol());
        assertFalse(response.system());
        verifyNoInteractions(userRepository);

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.CREACION_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(ADMIN_ID, event.usuarioId());
        assertEquals("rol:MONITOR_EVACUACION", event.recursoAfectado());
        assertEquals("descripcion=Apoya la evacuación;permisos=" + PERM_C + "," + PERM_B + "," + PERM_A,
                event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    /**
     * Sin ms01:role:manage crear un rol responde 403, no toca la tabla y audita el rechazo sin recurso
     */
    @Test
    void createRole_withoutManage_isForbiddenAndAudited() {
        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> service.createRole(student(), createRequest("ROL", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals("No cuenta con los permisos suficientes para realizar esta operación.", error.getMessage());
        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.CREACION_ROL, null, "SIN_PERMISO");
    }

    /**
     * Tener el nombre ADMIN no basta: lo que autoriza es el permiso ms01:role:manage
     */
    @Test
    void createRole_adminNameWithoutThePermission_isForbidden() {
        RequesterIdentity fake = TestIdentities.withPermissions(ADMIN_ID, Set.of("ADMIN"), "ms01:role:read_all");

        assertThrows(ForbiddenOperationException.class,
                () -> service.createRole(fake, createRequest("ROL", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        verifyNoInteractions(roleRepository);
    }

    /**
     * Lo que autoriza es el permiso y no el nombre del rol: una identidad con ms01:role:manage crea roles
     */
    @Test
    void createRole_anyIdentityWithManage_isAllowed() {
        RequesterIdentity custom = TestIdentities.withPermissions(ADMIN_ID, Set.of("SOPORTE"), MANAGE);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(custom,
                createRequest("NUEVO", "Descripción", List.of(PERM_A)), CORRELATION_ID);

        assertEquals("NUEVO", response.rol());
    }

    /**
     * Un cuerpo inválido responde 400 con un error por campo, sin consultar la tabla, y audita el rechazo con el
     * nombre normalizado
     */
    @Test
    void createRole_invalidBody_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("rol", "ab", List.of("MS01:PROFILE:READ")),
                        CORRELATION_ID));

        assertEquals(List.of("descripcion", "permisos"),
                error.getErrors().stream().map(detail -> detail.campo()).sorted().toList());
        verify(roleRepository, never()).existsById(anyString());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:ROL", "VALIDACION");
    }

    /**
     * Si ni el nombre tiene un formato válido el rechazo se audita sin recurso
     */
    @Test
    void createRole_invalidName_isAuditedWithoutResource() {
        assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("1rol", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CREACION_ROL, null, "VALIDACION");
    }

    /**
     * DETAILS está reservado para la ruta GET /roles/details: crear un rol con ese nombre responde 400 con el
     * error en el campo rol, no guarda nada y audita el rechazo
     */
    @Test
    void createRole_reservedName_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("details", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals(List.of("rol"), error.getErrors().stream().map(detail -> detail.campo()).toList());
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
        verify(roleRepository, never()).existsById(anyString());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:DETAILS", "VALIDACION");
    }

    /**
     * ms01:role:manage es exclusivo de ADMIN: pedirlo en un rol nuevo responde 422 y se audita
     */
    @Test
    void createRole_withManagePermission_isABusinessRuleError() {
        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A, MANAGE)),
                        CORRELATION_ID));

        assertEquals("El permiso ms01:role:manage es exclusivo del rol ADMIN.", error.getMessage());
        verify(roleRepository, never()).existsById(anyString());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "REGLA_NEGOCIO");
    }

    /**
     * Un nombre que ya existe responde 409 y se audita
     */
    @Test
    void createRole_duplicatedName_isAConflict() {
        when(roleRepository.existsById("PROFESOR")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.createRole(admin(), createRequest("profesor", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals("Ya existe un rol con ese nombre.", error.getMessage());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:PROFESOR", "CONFLICTO");
    }

    /**
     * Si otra petición crea el mismo nombre a la vez, la restricción de la base de datos se traduce en 409
     */
    @Test
    void createRole_uniqueViolationOnSave_isAConflict() {
        when(roleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThrows(ConflictException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "CONFLICTO");
    }

    /**
     * Un fallo inesperado al guardar se audita y se propaga
     */
    @Test
    void createRole_unexpectedFailureOnSave_isAuditedAndPropagated() {
        IllegalStateException failure = new IllegalStateException("base caída");
        when(roleRepository.saveAndFlush(any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "ERROR_INTERNO");
    }

    /**
     * Un campo no definido en el contrato se rechaza con el mensaje de los roles
     */
    @Test
    void createRole_unknownField_isRejectedPerField() {
        CreateRoleRequest request = createRequest("NUEVO", "Descripción", List.of(PERM_A));
        request.registerUnknownField("es_sistema", true);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), request, CORRELATION_ID));

        assertEquals("es_sistema", error.getErrors().get(0).campo());
        assertEquals("Campo no permitido.", error.getErrors().get(0).mensaje());
    }

    /**
     * Un fallo de la auditoría no cambia la respuesta de la creación
     */
    @Test
    void createRole_auditFailure_doesNotChangeTheResponse() {
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("MS08 no disponible")).when(auditPublisher).publish(any());

        RoleDetailResponse response = service.createRole(admin(),
                createRequest("NUEVO", "Descripción", List.of(PERM_A)), CORRELATION_ID);

        assertEquals("NUEVO", response.rol());
    }

    // ------------------------------------------------------------------ edición

    /**
     * Cambiar solo la descripción no renombra ni registra corte de sesión, y audita los valores anterior y nuevo
     */
    @Test
    void updateRole_onlyDescription_keepsTheNameAndSetsNoSessionCut() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenReturn(current);

        RoleDetailResponse response = service.updateRole(admin(), "monitor", updateRequest("MONITOR",
                "Nueva descripción"), CORRELATION_ID);

        assertEquals("MONITOR", response.rol());
        assertEquals("Nueva descripción", response.descripcion());
        assertNull(current.getValidFrom());
        verify(roleRepository, never()).rename(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(userRepository);

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.EDICION_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("nombre_anterior=MONITOR;descripcion_anterior=Descripción de MONITOR;nombre_nuevo=MONITOR;"
                + "descripcion_nueva=Nueva descripción", event.detalle());
    }

    /**
     * Renombrar un rol corta la sesión de los usuarios que lo tienen antes de cambiar el nombre y cambia la
     * clave con una sola sentencia
     */
    @Test
    void updateRole_rename_cutsTheUsersThenRenames() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        RoleEntity renamed = role("MONITOR_NUEVO", false, PERM_A);
        renamed.setDescription("Nueva descripción");
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.existsById("MONITOR_NUEVO")).thenReturn(false);
        when(roleRepository.findById("MONITOR_NUEVO")).thenReturn(Optional.of(renamed));

        RoleDetailResponse response = service.updateRole(admin(), "MONITOR",
                updateRequest("monitor_nuevo", "Nueva descripción"), CORRELATION_ID);

        InOrder order = inOrder(userRepository, roleRepository);
        order.verify(userRepository).markSessionCutByRole("MONITOR", CUT);
        order.verify(roleRepository).rename("MONITOR", "MONITOR_NUEVO", "Nueva descripción", CUT);
        assertEquals("MONITOR_NUEVO", response.rol());
        verify(roleRepository, never()).saveAndFlush(any());

        AuditEvent event = singleEvent();
        assertEquals("rol:MONITOR_NUEVO", event.recursoAfectado());
        assertEquals("nombre_anterior=MONITOR;descripcion_anterior=Descripción de MONITOR;"
                + "nombre_nuevo=MONITOR_NUEVO;descripcion_nueva=Nueva descripción", event.detalle());
    }

    /**
     * Un rol de sistema puede cambiar de descripción
     */
    @Test
    void updateRole_systemRoleDescription_isAllowed() {
        RoleEntity current = role("ESTUDIANTE", true, PERM_C);
        when(roleRepository.findById("ESTUDIANTE")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenReturn(current);

        RoleDetailResponse response = service.updateRole(admin(), "ESTUDIANTE",
                updateRequest("ESTUDIANTE", "Descripción nueva"), CORRELATION_ID);

        assertEquals("Descripción nueva", response.descripcion());
        assertTrue(response.system());
    }

    /**
     * Un rol de sistema no se puede renombrar: responde 409 y se audita
     */
    @Test
    void updateRole_renamingASystemRole_isAConflict() {
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(role("ADMIN", true, MANAGE)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "ADMIN", updateRequest("SUPERADMIN", "Descripción"),
                        CORRELATION_ID));

        assertEquals("Los roles de sistema no se pueden renombrar.", error.getMessage());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:ADMIN", "ROL_DE_SISTEMA");
    }

    /**
     * Renombrar a un nombre que ya existe responde 409 y no registra cortes
     */
    @Test
    void updateRole_renameToAnExistingName_isAConflict() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(roleRepository.existsById("PROFESOR")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("profesor", "Descripción"),
                        CORRELATION_ID));

        assertEquals("Ya existe un rol con ese nombre.", error.getMessage());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "CONFLICTO");
    }

    /**
     * Una violación de restricción de la base de datos al renombrar se traduce en 409
     */
    @Test
    void updateRole_renameRejectedByTheDatabase_isAConflict() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(roleRepository.rename(anyString(), anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("OTRO", "Descripción"), CORRELATION_ID));

        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "CONFLICTO");
    }

    /**
     * Un fallo inesperado al editar se audita y se propaga
     */
    @Test
    void updateRole_unexpectedFailure_isAuditedAndPropagated() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        IllegalStateException failure = new IllegalStateException("base caída");
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("MONITOR", "Descripción"),
                        CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

    /**
     * Sin ms01:role:manage editar un rol responde 403 antes de validar nada
     */
    @Test
    void updateRole_withoutManage_isForbiddenAndAudited() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.updateRole(student(), "MONITOR", updateRequest("MONITOR", "Descripción"),
                        CORRELATION_ID));

        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.EDICION_ROL, null, "SIN_PERMISO");
    }

    /**
     * La ruta se valida antes que el rol y que el cuerpo: un nombre inválido responde 400 sin recurso
     */
    @Test
    void updateRole_invalidPathName_isRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.updateRole(admin(), "rol-malo", updateRequest("MONITOR", "Descripción"),
                        CORRELATION_ID));

        verifyNoInteractions(roleRepository);
        assertSingleFailure(AuditAction.EDICION_ROL, null, "VALIDACION");
    }

    /**
     * Un rol inexistente responde 404 antes de validar el cuerpo
     */
    @Test
    void updateRole_unknownRole_isNotFoundBeforeValidatingTheBody() {
        when(roleRepository.findById("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> service.updateRole(admin(), "FANTASMA", updateRequest("1", "x"), CORRELATION_ID));

        assertSingleFailure(AuditAction.EDICION_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

    /**
     * Un cuerpo inválido sobre un rol existente responde 400 y se audita con el rol de la ruta
     */
    @Test
    void updateRole_invalidBody_isRejectedAndAudited() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("MONITOR", "<b>x</b>"), CORRELATION_ID));

        assertEquals("descripcion", error.getErrors().get(0).campo());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "VALIDACION");
    }

    /**
     * Renombrar un rol a DETAILS responde 400 con el error en el campo rol, no corta sesiones ni cambia la
     * clave y audita el rechazo con el rol de la ruta
     */
    @Test
    void updateRole_renameToTheReservedName_isRejectedAndAudited() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("details", "Descripción"),
                        CORRELATION_ID));

        assertEquals(List.of("rol"), error.getErrors().stream().map(detail -> detail.campo()).toList());
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        verify(roleRepository, never()).rename(anyString(), anyString(), anyString(), any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "VALIDACION");
    }

    // ------------------------------------------------------------------ permisos

    /**
     * Cambiar los permisos reemplaza la lista, registra el corte del rol y audita lo agregado y lo retirado
     */
    @Test
    void updatePermissions_replacesTheListSetsTheRoleCutAndAudits() {
        RoleEntity entity = role("MONITOR", false, PERM_A, PERM_B);
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        RoleDetailResponse response = service.updatePermissions(admin(), "monitor",
                permissionsRequest(List.of(PERM_C, PERM_A)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, PERM_A), entity.getPermissions());
        assertEquals(CUT, entity.getValidFrom());
        assertEquals(List.of(PERM_C, PERM_A), response.permisos());
        verifyNoInteractions(userRepository);

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.CAMBIO_PERMISOS_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("agregados=" + PERM_C + ";retirados=" + PERM_B, event.detalle());
    }

    /**
     * Enviar la misma lista registra el corte del rol igual, y el detalle queda sin cambios
     */
    @Test
    void updatePermissions_sameList_stillSetsTheRoleCut() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of(PERM_A)), CORRELATION_ID);

        assertEquals(CUT, entity.getValidFrom());
        assertEquals("agregados=;retirados=", singleEvent().detalle());
    }

    /**
     * ADMIN puede cambiar sus permisos mientras conserve ms01:role:manage
     */
    @Test
    void updatePermissions_adminKeepingManage_isAllowed() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_A);
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(admin));
        when(roleRepository.saveAndFlush(admin)).thenReturn(admin);

        RoleDetailResponse response = service.updatePermissions(admin(), "ADMIN",
                permissionsRequest(List.of(MANAGE, PERM_C)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, MANAGE), response.permisos());
    }

    /**
     * Un rol distinto de ADMIN no puede tener ms01:role:manage: responde 422 y no cambia nada
     */
    @Test
    void updatePermissions_manageOnAnotherRole_isABusinessRuleError() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(entity));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of(PERM_A, MANAGE)),
                        CORRELATION_ID));

        assertEquals("El permiso ms01:role:manage es exclusivo del rol ADMIN.", error.getMessage());
        assertEquals(List.of(PERM_A), entity.getPermissions());
        assertNull(entity.getValidFrom());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "REGLA_NEGOCIO");
    }

    /**
     * ADMIN no puede perder ms01:role:manage: responde 422 y no cambia nada
     */
    @Test
    void updatePermissions_adminLosingManage_isABusinessRuleError() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_A);
        when(roleRepository.findById("ADMIN")).thenReturn(Optional.of(admin));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ADMIN", permissionsRequest(List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals("El rol ADMIN debe conservar el permiso ms01:role:manage.", error.getMessage());
        assertEquals(List.of(MANAGE, PERM_A), admin.getPermissions());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:ADMIN", "REGLA_NEGOCIO");
    }

    /**
     * Una lista inválida responde 400, no cambia nada y se audita
     */
    @Test
    void updatePermissions_invalidList_isRejectedAndAudited() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(entity));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of()), CORRELATION_ID));

        assertEquals("permisos", error.getErrors().get(0).campo());
        assertEquals("Seleccione al menos un permiso.", error.getErrors().get(0).mensaje());
        assertEquals(List.of(PERM_A), entity.getPermissions());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "VALIDACION");
    }

    /**
     * Un rol inexistente responde 404 antes de validar la lista
     */
    @Test
    void updatePermissions_unknownRole_isNotFound() {
        when(roleRepository.findById("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> service.updatePermissions(admin(), "FANTASMA", permissionsRequest(List.of()),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

    /**
     * Sin ms01:role:manage el cambio de permisos responde 403
     */
    @Test
    void updatePermissions_withoutManage_isForbidden() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.updatePermissions(student(), "MONITOR", permissionsRequest(List.of(PERM_A)),
                        CORRELATION_ID));

        verifyNoInteractions(roleRepository);
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, null, "SIN_PERMISO");
    }

    /**
     * Un nombre inválido en la ruta responde 400
     */
    @Test
    void updatePermissions_invalidPathName_isRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.updatePermissions(admin(), "rol-malo", permissionsRequest(List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, null, "VALIDACION");
    }

    /**
     * Un fallo inesperado al guardar se audita y se propaga
     */
    @Test
    void updatePermissions_unexpectedFailure_isAuditedAndPropagated() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        IllegalStateException failure = new IllegalStateException("base caída");
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of(PERM_B)),
                        CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

    // ------------------------------------------------------------------ borrado

    /**
     * Borrar un rol corta la sesión de sus usuarios, pasa a ESTUDIANTE a quienes solo tenían ese rol, lo retira
     * a los demás y borra el rol, en ese orden, y audita los conteos
     */
    @Test
    void deleteRole_cutsMovesRemovesAndDeletesInOrder() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(userRepository.countWithOnlyRole("MONITOR")).thenReturn(2L);
        when(userRepository.countWithRole("MONITOR")).thenReturn(5L);

        service.deleteRole(admin(), "monitor", CORRELATION_ID);

        InOrder order = inOrder(userRepository, roleRepository);
        order.verify(userRepository).markSessionCutByRole("MONITOR", CUT);
        order.verify(userRepository).replaceOnlyRole("MONITOR", "ESTUDIANTE");
        order.verify(userRepository).removeRoleFromAll("MONITOR");
        order.verify(roleRepository).deleteById("MONITOR");
        order.verify(roleRepository).flush();

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.ELIMINACION_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("usuarios_a_estudiante=2;usuarios_que_solo_perdieron_el_rol=3", event.detalle());
    }

    /**
     * Un rol sin usuarios se borra y el detalle lleva ceros
     */
    @Test
    void deleteRole_withoutUsers_deletesAndAuditsZeros() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        service.deleteRole(admin(), "MONITOR", CORRELATION_ID);

        verify(roleRepository).deleteById("MONITOR");
        assertEquals("usuarios_a_estudiante=0;usuarios_que_solo_perdieron_el_rol=0", singleEvent().detalle());
    }

    /**
     * Un rol de sistema no se puede borrar: responde 409 y no toca usuarios
     */
    @Test
    void deleteRole_systemRole_isAConflict() {
        when(roleRepository.findById("ESTUDIANTE")).thenReturn(Optional.of(role("ESTUDIANTE", true, PERM_C)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.deleteRole(admin(), "ESTUDIANTE", CORRELATION_ID));

        assertEquals("Los roles de sistema no se pueden eliminar.", error.getMessage());
        verifyNoInteractions(userRepository);
        verify(roleRepository, never()).deleteById(anyString());
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:ESTUDIANTE", "ROL_DE_SISTEMA");
    }

    /**
     * Un rol inexistente responde 404
     */
    @Test
    void deleteRole_unknownRole_isNotFound() {
        when(roleRepository.findById("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> service.deleteRole(admin(), "FANTASMA", CORRELATION_ID));

        verifyNoInteractions(userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

    /**
     * Un nombre inválido en la ruta responde 400
     */
    @Test
    void deleteRole_invalidPathName_isRejected() {
        assertThrows(InvalidRequestException.class, () -> service.deleteRole(admin(), "rol-malo", CORRELATION_ID));

        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, null, "VALIDACION");
    }

    /**
     * Sin ms01:role:manage borrar un rol responde 403
     */
    @Test
    void deleteRole_withoutManage_isForbidden() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.deleteRole(student(), "MONITOR", CORRELATION_ID));

        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, null, "SIN_PERMISO");
    }

    /**
     * Si falla alguna sentencia del borrado se audita el fallo y se propaga para que la transacción se revierta
     */
    @Test
    void deleteRole_unexpectedFailure_isAuditedAndPropagated() {
        IllegalStateException failure = new IllegalStateException("base caída");
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(userRepository.replaceOnlyRole(anyString(), anyString())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.deleteRole(admin(), "MONITOR", CORRELATION_ID));

        assertSame(failure, thrown);
        verify(roleRepository, never()).deleteById(anyString());
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

    // ------------------------------------------------------------------ transacciones y correlación

    /**
     * Las operaciones de escritura corren en una sola transacción y las de lectura en una de solo lectura
     *
     * @throws NoSuchMethodException si algún método no existe
     */
    @Test
    void writeOperations_runInOneTransaction() throws NoSuchMethodException {
        Class<?> type = RoleAdminServiceImpl.class;

        assertTrue(type.getMethod("createRole", RequesterIdentity.class, CreateRoleRequest.class, String.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(type.getMethod("updateRole", RequesterIdentity.class, String.class, UpdateRoleRequest.class,
                String.class).isAnnotationPresent(Transactional.class));
        assertTrue(type.getMethod("updatePermissions", RequesterIdentity.class, String.class,
                UpdateRolePermissionsRequest.class, String.class).isAnnotationPresent(Transactional.class));
        assertTrue(type.getMethod("deleteRole", RequesterIdentity.class, String.class, String.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(type.getMethod("listRoles", RequesterIdentity.class).getAnnotation(Transactional.class)
                .readOnly());
        assertTrue(type.getMethod("getRole", RequesterIdentity.class, String.class)
                .getAnnotation(Transactional.class).readOnly());
    }

    /**
     * Un X-Correlation-ID ausente o inválido se reemplaza por uno generado que se usa en la auditoría
     */
    @Test
    void writeOperations_withoutAValidCorrelationId_generateOne() {
        when(roleRepository.findById("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        service.deleteRole(admin(), "MONITOR", "no es válido porque tiene espacios");

        String correlation = singleEvent().correlationId();
        assertEquals(36, correlation.length());
        UUID.fromString(correlation);
    }

    /**
     * Las operaciones de consulta no auditan
     */
    @Test
    void readOperations_doNotAudit() {
        when(roleRepository.findById(eq("MONITOR"))).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        service.getRole(admin(), "MONITOR");
        service.listPermissions(admin());

        verifyNoInteractions(auditPublisher);
    }
}
