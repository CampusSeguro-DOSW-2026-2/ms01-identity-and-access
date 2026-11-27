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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.AlertRecipientRoleResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PermissionRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleRequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * RF-MS01-10: query, creation, edition, permission change and deletion of roles, the rules RG-08 and RN-MS01-14, the
 * audit of each operation and the session cut of RF-MS01-11
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
    private static final String PROFILE_WRITE = "ms01:profile:write";
    private static final String LOGOUT = "ms01:session:logout";

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleAdminServiceImpl service;

        /**
     * Creates the service with a fixed clock so the date of the events and of the cuts is predictable
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RoleAdminServiceImpl(roleRepository, permissionRepository, userRepository,
                new AuditRecorder(auditPublisher, clock), FixedDatabaseTime.at(NOW));
        List<PermissionEntity> catalog = new ArrayList<>();
        for (String code : List.of(MANAGE, "ms01:role:read_all", PERM_C, PROFILE_WRITE, LOGOUT, PERM_A, PERM_B)) {
            catalog.add(TestEntities.permission(code));
        }
        lenient().when(permissionRepository.findAll()).thenReturn(catalog);
    }

        /**
     * Identity with ms01:role:manage and ms01:role:read_all, the one of the ADMIN role
     *
     * @return the identity of the administrator
     */
    private RequesterIdentity admin() {
        return TestIdentities.of(ADMIN_ID, "ADMIN");
    }

        /**
     * Identity without the role management permissions
     *
     * @return the identity of a student
     */
    private RequesterIdentity student() {
        return TestIdentities.of(ADMIN_ID, "ESTUDIANTE");
    }

        /**
     * Stored role
     *
     * @param name name of the role
     * @param system true if it is a system role
     * @param permissions permission codes of the role
     * @return the entity of the role
     */
    private RoleEntity role(String name, boolean system, String... permissions) {
        RoleEntity entity = new RoleEntity();
        entity.setName(name);
        entity.setDescription("Descripción de " + name);
        entity.setPermissions(TestEntities.permissions(permissions));
        entity.setSystem(system);
        return entity;
    }

        /**
     * Role creation body
     *
     * @param name name of the role
     * @param description description of the role
     * @param permissions permissions of the role
     * @return the request
     */
    private CreateRoleRequest createRequest(String name, String description, List<String> permissions) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        request.setPermisos(permissions);
        return request;
    }

        /**
     * Role edit body
     *
     * @param name name of the role
     * @param description description of the role
     * @return the request
     */
    private UpdateRoleRequest updateRequest(String name, String description) {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        return request;
    }

        /**
     * Permission change body
     *
     * @param permissions permissions of the role
     * @return the request
     */
    private UpdateRolePermissionsRequest permissionsRequest(List<String> permissions) {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermisos(permissions);
        return request;
    }

        /**
     * Captures the only audit event published
     *
     * @return the event
     */
    private AuditEvent singleEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(1)).publish(captor.capture());
        return captor.getValue();
    }

        /**
     * Checks that a single FALLO event was published with the indicated reason and resource
     *
     * @param action expected action
     * @param resource expected affected resource, null if there is none
     * @param reason expected reason
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

    // ------------------------------------------------------------------ query

        /**
     * Whoever has ms01:role:read_all receives all the roles with their detail
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
     * Without ms01:role:read_all the list of roles is forbidden
     */
    @Test
    void listRoles_withoutReadAll_isForbidden() {
        assertThrows(ForbiddenOperationException.class, () -> service.listRoles(student()));

        verifyNoInteractions(roleRepository);
    }

        /**
     * A role is queried by its normalized name
     */
    @Test
    void getRole_normalizesThePathName() {
        when(roleRepository.findByName("MONITOR_EVACUACION")).thenReturn(Optional.of(role("MONITOR_EVACUACION", false,
                PERM_A)));

        RoleDetailResponse response = service.getRole(admin(), "  monitor_evacuacion ");

        assertEquals("MONITOR_EVACUACION", response.rol());
        assertEquals(List.of(PERM_A), response.permisos());
    }

        /**
     * The permissions of the role are delivered as they are stored, regardless of the module
     */
    @Test
    void getRole_returnsTheStoredPermissions() {
        when(roleRepository.findByName("OTRO")).thenReturn(Optional.of(role("OTRO", false, PERM_A, "ms99:x:y")));

        assertEquals(List.of(PERM_A, "ms99:x:y"), service.getRole(admin(), "OTRO").permisos());
    }

        /**
     * A non-existent role answers 404
     */
    @Test
    void getRole_unknownRole_isNotFound() {
        when(roleRepository.findByName("FANTASMA")).thenReturn(Optional.empty());

        RoleNotFoundException error = assertThrows(RoleNotFoundException.class,
                () -> service.getRole(admin(), "FANTASMA"));

        assertEquals("Rol no encontrado.", error.getMessage());
        assertEquals("FANTASMA", error.getRoleName());
    }

        /**
     * A name with an invalid format in the path answers 400 and does not query the table
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
     * Without ms01:role:read_all the query of a role is forbidden before validating the path
     */
    @Test
    void getRole_withoutReadAll_isForbiddenBeforeValidatingThePath() {
        assertThrows(ForbiddenOperationException.class, () -> service.getRole(student(), "rol-con-guiones"));
    }

        /**
     * The permission catalog comes from the database, sorted by code and with the stored description
     */
    @Test
    void listPermissions_returnsTheDatabaseCatalogSortedByCode() {
        PermissionEntity manage = TestEntities.permission(MANAGE);
        manage.setDescription("Gestionar roles");
        PermissionEntity other = TestEntities.permission("ms02:campus:read");
        when(permissionRepository.findAll()).thenReturn(List.of(manage, other, TestEntities.permission(PERM_A)));

        List<PermissionItemResponse> permissions = service.listPermissions(admin());

        assertEquals(List.of("ms01:role:manage", "ms02:campus:read", PERM_A),
                permissions.stream().map(PermissionItemResponse::permiso).toList());
        assertEquals("Gestionar roles", permissions.get(0).descripcion());
        verifyNoInteractions(roleRepository, userRepository, auditPublisher);
    }

        /**
     * Without ms01:role:read_all the permission catalog is forbidden
     */
    @Test
    void listPermissions_withoutReadAll_isForbidden() {
        assertThrows(ForbiddenOperationException.class, () -> service.listPermissions(student()));
    }

    // ------------------------------------------------------------------ alert recipients

        /**
     * Whoever manages alerts (COORDINADOR, with ms03:alert:manage) receives the roles with ms03:alert:receive, in the
     * order of the database, with their name and description, without touching other tables or the audit
     */
    @Test
    void listAlertRecipientRoles_withAlertManage_returnsTheRolesThatReceiveAlerts() {
        RoleEntity brigadista = TestEntities.role("BRIGADISTA", "ms03:alert:receive");
        brigadista.setDescription("Brigadista de emergencias del campus");
        when(roleRepository.findWithPermission("ms03:alert:receive"))
                .thenReturn(List.of(brigadista, TestEntities.role("ESTUDIANTE", "ms03:alert:receive")));

        List<AlertRecipientRoleResponse> roles = service.listAlertRecipientRoles(
                TestIdentities.of(ADMIN_ID, "COORDINADOR"));

        assertEquals(List.of(new AlertRecipientRoleResponse("BRIGADISTA", "Brigadista de emergencias del campus"),
                new AlertRecipientRoleResponse("ESTUDIANTE", "Descripción de ESTUDIANTE")), roles);
        verifyNoInteractions(permissionRepository, userRepository, auditPublisher);
    }

        /**
     * ms03:alert:manage_drill alone also authorizes the query, because the drill alerts are edited with the same form
     */
    @Test
    void listAlertRecipientRoles_withOnlyManageDrill_isAllowed() {
        when(roleRepository.findWithPermission("ms03:alert:receive")).thenReturn(List.of());

        assertEquals(List.of(), service.listAlertRecipientRoles(TestIdentities.withPermissions(ADMIN_ID,
                Set.of("MONITOR"), "ms01:profile:read", "ms03:alert:manage_drill")));
    }

        /**
     * When no role has ms03:alert:receive the answer is an empty list, not an error
     */
    @Test
    void listAlertRecipientRoles_withoutRecipientRoles_returnsAnEmptyList() {
        when(roleRepository.findWithPermission("ms03:alert:receive")).thenReturn(List.of());

        assertTrue(service.listAlertRecipientRoles(TestIdentities.of(ADMIN_ID, "COORDINADOR")).isEmpty());
    }

        /**
     * Without ms03:alert:manage or ms03:alert:manage_drill it is forbidden, even for ADMIN, which has
     * ms01:role:read_all but does not manage alerts, and the database is not queried
     */
    @Test
    void listAlertRecipientRoles_withoutAlertPermissions_isForbidden() {
        assertThrows(ForbiddenOperationException.class, () -> service.listAlertRecipientRoles(student()));
        assertThrows(ForbiddenOperationException.class, () -> service.listAlertRecipientRoles(admin()));
        verifyNoInteractions(roleRepository);
    }

    // ------------------------------------------------------------------ creation

    /**
     * Creating a role saves it as a non-system role, with an uppercase name and sorted permissions, which include
     * the two mandatory ones, audits it and sets no session cut
     */
    @Test
    void createRole_validRequest_savesAuditsAndSetsNoSessionCut() {
        when(roleRepository.existsByName("MONITOR_EVACUACION")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(admin(),
                createRequest(" monitor_evacuacion ", "  Apoya la evacuación  ", List.of(PERM_A, PERM_C, PERM_B)),
                CORRELATION_ID);

        ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository).saveAndFlush(captor.capture());
        RoleEntity saved = captor.getValue();
        assertEquals("MONITOR_EVACUACION", saved.getName());
        assertEquals("Apoya la evacuación", saved.getDescription());
        assertEquals(List.of(PERM_C, LOGOUT, PERM_B, PERM_A), PermissionCodes.ofRole(saved));
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
        assertEquals("descripcion=Apoya la evacuación;permisos=" + PERM_C + "," + LOGOUT + "," + PERM_B + ","
                + PERM_A, event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    /**
     * A role created with an empty permission list gets only the two mandatory permissions, without any error
     */
    @Test
    void createRole_emptyPermissionList_getsOnlyTheMandatoryPermissions() {
        when(roleRepository.existsByName("CUENTA_ESTATICA")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(admin(),
                createRequest("cuenta_estatica", "Cuenta de uso ocasional", List.of()), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT), response.permisos());
        assertEquals("descripcion=Cuenta de uso ocasional;permisos=" + PERM_C + "," + LOGOUT,
                singleEvent().detalle());
    }

    /**
     * A role created without the permission list gets only the two mandatory permissions
     */
    @Test
    void createRole_missingPermissionList_getsOnlyTheMandatoryPermissions() {
        when(roleRepository.existsByName("CUENTA_ESTATICA")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(admin(),
                createRequest("cuenta_estatica", "Cuenta de uso ocasional", null), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT), response.permisos());
    }

    /**
     * When the list brings only one of the mandatory permissions, the missing one is added and none is repeated
     */
    @Test
    void createRole_oneMandatoryPermissionMissing_addsTheOtherOne() {
        when(roleRepository.existsByName("MONITOR")).thenReturn(false);
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDetailResponse response = service.createRole(admin(),
                createRequest("monitor", "Monitor del campus", List.of(LOGOUT, PERM_A)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), response.permisos());
    }

        /**
     * Without ms01:role:manage creating a role answers 403, does not touch the table and audits the rejection without a
     * resource
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
     * Having the name ADMIN is not enough: what authorizes is the ms01:role:manage permission
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
     * What authorizes is the permission and not the name of the role: an identity with ms01:role:manage creates roles
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
     * An invalid body answers 400 with one error per field, without querying the table, and audits the rejection with
     * the normalized name
     */
    @Test
    void createRole_invalidBody_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("rol", "ab", List.of("MS01:PROFILE:READ")),
                        CORRELATION_ID));

        assertEquals(List.of("descripcion", "permisos"),
                error.getErrors().stream().map(detail -> detail.campo()).sorted().toList());
        verify(roleRepository, never()).existsByName(anyString());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:ROL", "VALIDACION");
    }

        /**
     * If not even the name has a valid format the rejection is audited without a resource
     */
    @Test
    void createRole_invalidName_isAuditedWithoutResource() {
        assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("1rol", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CREACION_ROL, null, "VALIDACION");
    }

        /**
     * DETAILS is reserved for the route GET /roles/details: creating a role with that name answers 400 with the error
     * in the rol field, saves nothing and audits the rejection
     */
    @Test
    void createRole_reservedName_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.createRole(admin(), createRequest("details", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals(List.of("rol"), error.getErrors().stream().map(detail -> detail.campo()).toList());
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
        verify(roleRepository, never()).existsByName(anyString());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:DETAILS", "VALIDACION");
    }

        /**
     * ms01:role:manage is exclusive to ADMIN: requesting it on a new role answers 422 and is audited
     */
    @Test
    void createRole_withManagePermission_isABusinessRuleError() {
        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A, MANAGE)),
                        CORRELATION_ID));

        assertEquals("El permiso ms01:role:manage es exclusivo del rol ADMIN.", error.getMessage());
        verify(roleRepository, never()).existsByName(anyString());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "REGLA_NEGOCIO");
    }

        /**
     * A name that already exists answers 409 and is audited
     */
    @Test
    void createRole_duplicatedName_isAConflict() {
        when(roleRepository.existsByName("PROFESOR")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.createRole(admin(), createRequest("profesor", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals("Ya existe un rol con ese nombre.", error.getMessage());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:PROFESOR", "CONFLICTO");
    }

        /**
     * If another request creates the same name at the same time, the database constraint is translated into 409
     */
    @Test
    void createRole_uniqueViolationOnSave_isAConflict() {
        when(roleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(ConflictException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "CONFLICTO");
    }

        /**
     * An unexpected failure when saving is audited and propagated
     */
    @Test
    void createRole_unexpectedFailureOnSave_isAuditedAndPropagated() {
        IllegalStateException failure = new IllegalStateException("database down");
        when(roleRepository.saveAndFlush(any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.createRole(admin(), createRequest("NUEVO", "Descripción", List.of(PERM_A)),
                        CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.CREACION_ROL, "rol:NUEVO", "ERROR_INTERNO");
    }

        /**
     * A field not defined in the contract is rejected with the message of the roles
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
     * A failure of the audit does not change the response of the creation
     */
    @Test
    void createRole_auditFailure_doesNotChangeTheResponse() {
        when(roleRepository.saveAndFlush(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("MS08 unavailable")).when(auditPublisher).publish(any());

        RoleDetailResponse response = service.createRole(admin(),
                createRequest("NUEVO", "Descripción", List.of(PERM_A)), CORRELATION_ID);

        assertEquals("NUEVO", response.rol());
    }

    // ------------------------------------------------------------------ edition

        /**
     * Changing only the description neither renames nor records a session cut, and audits the previous and new values
     */
    @Test
    void updateRole_onlyDescription_keepsTheNameAndSetsNoSessionCut() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenReturn(current);

        RoleDetailResponse response = service.updateRole(admin(), "monitor", updateRequest("MONITOR",
                "Nueva descripción"), CORRELATION_ID);

        assertEquals("MONITOR", response.rol());
        assertEquals("Nueva descripción", response.descripcion());
        assertNull(current.getValidFrom());
        verify(roleRepository).saveAndFlush(current);
        verifyNoInteractions(userRepository);

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.EDICION_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("nombre_anterior=MONITOR;descripcion_anterior=Descripción de MONITOR;nombre_nuevo=MONITOR;"
                + "descripcion_nueva=Nueva descripción", event.detalle());
    }

        /**
     * Renaming a role cuts the session of the users that have it before changing the name and saves the role with the
     * new name
     */
    @Test
    void updateRole_rename_cutsTheUsersThenRenames() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.existsByName("MONITOR_NUEVO")).thenReturn(false);
        when(roleRepository.saveAndFlush(current)).thenReturn(current);

        RoleDetailResponse response = service.updateRole(admin(), "MONITOR",
                updateRequest("monitor_nuevo", "Nueva descripción"), CORRELATION_ID);

        InOrder order = inOrder(userRepository, roleRepository);
        order.verify(userRepository).markSessionCutByRole("MONITOR", CUT);
        order.verify(roleRepository).saveAndFlush(current);
        assertEquals("MONITOR_NUEVO", current.getName());
        assertEquals("Nueva descripción", current.getDescription());
        assertEquals("MONITOR_NUEVO", response.rol());

        AuditEvent event = singleEvent();
        assertEquals("rol:MONITOR_NUEVO", event.recursoAfectado());
        assertEquals("nombre_anterior=MONITOR;descripcion_anterior=Descripción de MONITOR;"
                + "nombre_nuevo=MONITOR_NUEVO;descripcion_nueva=Nueva descripción", event.detalle());
    }

        /**
     * A system role can change its description
     */
    @Test
    void updateRole_systemRoleDescription_isAllowed() {
        RoleEntity current = role("ESTUDIANTE", true, PERM_C);
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenReturn(current);

        RoleDetailResponse response = service.updateRole(admin(), "ESTUDIANTE",
                updateRequest("ESTUDIANTE", "Descripción nueva"), CORRELATION_ID);

        assertEquals("Descripción nueva", response.descripcion());
        assertTrue(response.system());
    }

        /**
     * A system role cannot be renamed: it answers 409 and is audited
     */
    @Test
    void updateRole_renamingASystemRole_isAConflict() {
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(role("ADMIN", true, MANAGE)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "ADMIN", updateRequest("SUPERADMIN", "Descripción"),
                        CORRELATION_ID));

        assertEquals("Los roles de sistema no se pueden renombrar.", error.getMessage());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:ADMIN", "ROL_DE_SISTEMA");
    }

        /**
     * Renaming to a name that already exists answers 409 and records no cuts
     */
    @Test
    void updateRole_renameToAnExistingName_isAConflict() {
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(roleRepository.existsByName("PROFESOR")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("profesor", "Descripción"),
                        CORRELATION_ID));

        assertEquals("Ya existe un rol con ese nombre.", error.getMessage());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "CONFLICTO");
    }

        /**
     * A database constraint violation when renaming is translated into 409
     */
    @Test
    void updateRole_renameRejectedByTheDatabase_isAConflict() {
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(roleRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(ConflictException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("OTRO", "Descripción"), CORRELATION_ID));

        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "CONFLICTO");
    }

        /**
     * An unexpected failure when editing is audited and propagated
     */
    @Test
    void updateRole_unexpectedFailure_isAuditedAndPropagated() {
        RoleEntity current = role("MONITOR", false, PERM_A);
        IllegalStateException failure = new IllegalStateException("database down");
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(current));
        when(roleRepository.saveAndFlush(current)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("MONITOR", "Descripción"),
                        CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

        /**
     * Without ms01:role:manage editing a role answers 403 before validating anything
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
     * The path is validated before the role and the body: an invalid name answers 400 without a resource
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
     * A non-existent role answers 404 before validating the body
     */
    @Test
    void updateRole_unknownRole_isNotFoundBeforeValidatingTheBody() {
        when(roleRepository.findByName("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> service.updateRole(admin(), "FANTASMA", updateRequest("1", "x"), CORRELATION_ID));

        assertSingleFailure(AuditAction.EDICION_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

        /**
     * An invalid body on an existing role answers 400 and is audited with the role of the path
     */
    @Test
    void updateRole_invalidBody_isRejectedAndAudited() {
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("MONITOR", "<b>x</b>"), CORRELATION_ID));

        assertEquals("descripcion", error.getErrors().get(0).campo());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "VALIDACION");
    }

        /**
     * Renaming a role to DETAILS answers 400 with the error in the rol field, neither cuts sessions nor changes the key
     * and audits the rejection with the role of the path
     */
    @Test
    void updateRole_renameToTheReservedName_isRejectedAndAudited() {
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updateRole(admin(), "MONITOR", updateRequest("details", "Descripción"),
                        CORRELATION_ID));

        assertEquals(List.of("rol"), error.getErrors().stream().map(detail -> detail.campo()).toList());
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
        verify(userRepository, never()).markSessionCutByRole(anyString(), any());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.EDICION_ROL, "rol:MONITOR", "VALIDACION");
    }

    // ------------------------------------------------------------------ permissions

        /**
     * Changing the permissions replaces the list, records the cut of the role and audits what was added and what was
     * removed
     */
    @Test
    void updatePermissions_replacesTheListSetsTheRoleCutAndAudits() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_B);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        RoleDetailResponse response = service.updatePermissions(admin(), "monitor",
                permissionsRequest(List.of(PERM_C, LOGOUT, PERM_A)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), PermissionCodes.ofRole(entity));
        assertEquals(CUT, entity.getValidFrom());
        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), response.permisos());
        verifyNoInteractions(userRepository);

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.CAMBIO_PERMISOS_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("agregados=" + PERM_A + ";retirados=" + PERM_B, event.detalle());
    }

        /**
     * Sending the same list records the cut of the role all the same, and the detail is left unchanged
     */
    @Test
    void updatePermissions_sameList_stillSetsTheRoleCut() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of(PERM_C, LOGOUT, PERM_A)),
                CORRELATION_ID);

        assertEquals(CUT, entity.getValidFrom());
        assertEquals("agregados=;retirados=", singleEvent().detalle());
    }

        /**
     * ADMIN can change its permissions as long as it keeps ms01:role:manage
     */
    @Test
    void updatePermissions_adminKeepingManage_isAllowed() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_A);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));
        when(roleRepository.saveAndFlush(admin)).thenReturn(admin);

        RoleDetailResponse response = service.updatePermissions(admin(), "ADMIN",
                permissionsRequest(List.of(MANAGE, PERM_C, PROFILE_WRITE, LOGOUT)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, PROFILE_WRITE, MANAGE, LOGOUT), response.permisos());
    }

        /**
     * A role other than ADMIN cannot have ms01:role:manage: it answers 422 and changes nothing
     */
    @Test
    void updatePermissions_manageOnAnotherRole_isABusinessRuleError() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "MONITOR",
                        permissionsRequest(List.of(PERM_C, LOGOUT, PERM_A, MANAGE)), CORRELATION_ID));

        assertEquals("El permiso ms01:role:manage es exclusivo del rol ADMIN.", error.getMessage());
        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), PermissionCodes.ofRole(entity));
        assertNull(entity.getValidFrom());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "REGLA_NEGOCIO");
    }

        /**
     * ADMIN cannot lose ms01:role:manage: it answers 422 and changes nothing
     */
    @Test
    void updatePermissions_adminLosingManage_isABusinessRuleError() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_A);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ADMIN", permissionsRequest(List.of(PERM_A)),
                        CORRELATION_ID));

        assertEquals("El rol ADMIN debe conservar el permiso ms01:role:manage.", error.getMessage());
        assertEquals(List.of(MANAGE, PERM_A), PermissionCodes.ofRole(admin));
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:ADMIN", "REGLA_NEGOCIO");
    }

    /**
     * A list with a repeated permission is answered with 400, changes nothing and is audited
     */
    @Test
    void updatePermissions_repeatedPermission_isRejectedAndAudited() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of(PERM_A, PERM_A)),
                        CORRELATION_ID));

        assertEquals("permisos", error.getErrors().get(0).campo());
        assertEquals("Permiso repetido: " + PERM_A, error.getErrors().get(0).mensaje());
        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), PermissionCodes.ofRole(entity));
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "VALIDACION");
    }

    /**
     * A replacement list without ms01:profile:read or ms01:session:logout is answered with 422 and changes
     * nothing, for any role
     */
    @Test
    void updatePermissions_missingMandatoryPermission_isABusinessRuleError() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        for (List<String> list : List.of(List.of(PERM_C, PERM_A), List.of(LOGOUT, PERM_A), List.of(PERM_A))) {
            BusinessRuleException error = assertThrows(BusinessRuleException.class,
                    () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(list), CORRELATION_ID));

            assertEquals("El rol MONITOR debe conservar los permisos ms01:profile:read y ms01:session:logout.",
                    error.getMessage());
        }

        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), PermissionCodes.ofRole(entity));
        assertNull(entity.getValidFrom());
        verify(roleRepository, never()).saveAndFlush(any());
        verify(auditPublisher, times(3)).publish(any(AuditEvent.class));
    }

    /**
     * An empty replacement list is answered with 422 because it lacks the mandatory permissions
     */
    @Test
    void updatePermissions_emptyList_isABusinessRuleError() {
        RoleEntity entity = role("MONITOR", false, PERM_C, LOGOUT, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "MONITOR", permissionsRequest(List.of()), CORRELATION_ID));

        assertEquals("El rol MONITOR debe conservar los permisos ms01:profile:read y ms01:session:logout.",
                error.getMessage());
        assertEquals(List.of(PERM_C, LOGOUT, PERM_A), PermissionCodes.ofRole(entity));
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "REGLA_NEGOCIO");
    }

    /**
     * ESTUDIANTE cannot lose ms01:profile:write: it is answered with 422 and nothing changes
     */
    @Test
    void updatePermissions_studentLosingProfileWrite_isABusinessRuleError() {
        RoleEntity student = role("ESTUDIANTE", true, PERM_C, PROFILE_WRITE, LOGOUT, PERM_A);
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.of(student));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ESTUDIANTE",
                        permissionsRequest(List.of(PERM_C, LOGOUT, PERM_A)), CORRELATION_ID));

        assertEquals("El rol ESTUDIANTE debe conservar el permiso ms01:profile:write.", error.getMessage());
        assertEquals(List.of(PERM_C, PROFILE_WRITE, LOGOUT, PERM_A), PermissionCodes.ofRole(student));
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:ESTUDIANTE", "REGLA_NEGOCIO");
    }

    /**
     * ESTUDIANTE also cannot lose the permissions that all roles keep
     */
    @Test
    void updatePermissions_studentLosingALogout_isABusinessRuleError() {
        RoleEntity student = role("ESTUDIANTE", true, PERM_C, PROFILE_WRITE, LOGOUT, PERM_A);
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.of(student));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ESTUDIANTE",
                        permissionsRequest(List.of(PERM_C, PROFILE_WRITE, PERM_A)), CORRELATION_ID));

        assertEquals("El rol ESTUDIANTE debe conservar los permisos ms01:profile:read y ms01:session:logout.",
                error.getMessage());
        verify(roleRepository, never()).saveAndFlush(any());
    }

    /**
     * ADMIN keeps ms01:role:manage but cannot lose ms01:profile:write either
     */
    @Test
    void updatePermissions_adminLosingProfileWrite_isABusinessRuleError() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_C, PROFILE_WRITE, LOGOUT);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ADMIN",
                        permissionsRequest(List.of(MANAGE, PERM_C, LOGOUT)), CORRELATION_ID));

        assertEquals("El rol ADMIN debe conservar el permiso ms01:profile:write.", error.getMessage());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:ADMIN", "REGLA_NEGOCIO");
    }

    /**
     * ADMIN cannot lose ms01:profile:read: the rule of the permissions that all roles keep applies first
     */
    @Test
    void updatePermissions_adminLosingProfileRead_isABusinessRuleError() {
        RoleEntity admin = role("ADMIN", true, MANAGE, PERM_C, PROFILE_WRITE, LOGOUT);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.updatePermissions(admin(), "ADMIN",
                        permissionsRequest(List.of(MANAGE, PROFILE_WRITE, LOGOUT)), CORRELATION_ID));

        assertEquals("El rol ADMIN debe conservar los permisos ms01:profile:read y ms01:session:logout.",
                error.getMessage());
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:ADMIN", "REGLA_NEGOCIO");
    }

    /**
     * A custom role can lose ms01:profile:write, the minimum with that permission applies only to ESTUDIANTE and
     * ADMIN
     */
    @Test
    void updatePermissions_customRoleWithoutProfileWrite_isAllowed() {
        RoleEntity entity = role("MONITOR", false, PERM_A, PERM_C, PROFILE_WRITE, LOGOUT);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        RoleDetailResponse response = service.updatePermissions(admin(), "MONITOR",
                permissionsRequest(List.of(PERM_B, PERM_C, LOGOUT)), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT, PERM_B), response.permisos());
    }

        /**
     * A permission that is not in the database catalog is rejected with 400 and changes nothing
     */
    @Test
    void updatePermissions_permissionOutsideTheDatabaseCatalog_isRejected() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.updatePermissions(admin(), "MONITOR",
                        permissionsRequest(List.of(PERM_C, LOGOUT, "ms99:x:y")), CORRELATION_ID));

        assertEquals("Permiso no válido: ms99:x:y", error.getErrors().get(0).mensaje());
        assertEquals(List.of(PERM_A), PermissionCodes.ofRole(entity));
        verify(roleRepository, never()).saveAndFlush(any());
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "VALIDACION");
    }

        /**
     * A permission added to the database after startup can already be assigned without restarting MS01
     */
    @Test
    void updatePermissions_permissionAddedToTheDatabase_isAssignable() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(permissionRepository.findAll()).thenReturn(List.of(TestEntities.permission(PERM_A),
                TestEntities.permission(PERM_C), TestEntities.permission(LOGOUT),
                TestEntities.permission("ms09:nuevo:permiso")));
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenReturn(entity);

        RoleDetailResponse response = service.updatePermissions(admin(), "MONITOR",
                permissionsRequest(List.of(PERM_C, LOGOUT, "ms09:nuevo:permiso")), CORRELATION_ID);

        assertEquals(List.of(PERM_C, LOGOUT, "ms09:nuevo:permiso"), response.permisos());
    }

        /**
     * A non-existent role answers 404 before validating the list
     */
    @Test
    void updatePermissions_unknownRole_isNotFound() {
        when(roleRepository.findByName("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> service.updatePermissions(admin(), "FANTASMA", permissionsRequest(List.of()),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

        /**
     * Without ms01:role:manage the permission change answers 403
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
     * An invalid name in the path answers 400
     */
    @Test
    void updatePermissions_invalidPathName_isRejected() {
        assertThrows(InvalidRequestException.class,
                () -> service.updatePermissions(admin(), "rol-malo", permissionsRequest(List.of(PERM_A)),
                        CORRELATION_ID));

        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, null, "VALIDACION");
    }

        /**
     * An unexpected failure when saving is audited and propagated
     */
    @Test
    void updatePermissions_unexpectedFailure_isAuditedAndPropagated() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        IllegalStateException failure = new IllegalStateException("database down");
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(roleRepository.saveAndFlush(entity)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.updatePermissions(admin(), "MONITOR",
                        permissionsRequest(List.of(PERM_C, LOGOUT, PERM_B)), CORRELATION_ID));

        assertSame(failure, thrown);
        assertSingleFailure(AuditAction.CAMBIO_PERMISOS_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

    // ------------------------------------------------------------------ deletion

        /**
     * Deleting a role cuts the session of its users, moves to ESTUDIANTE those who only had that role, removes it from
     * the others and deletes the role, in that order, and audits the counts
     */
    @Test
    void deleteRole_cutsMovesRemovesAndDeletesInOrder() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));
        when(userRepository.countWithOnlyRole("MONITOR")).thenReturn(2L);
        when(userRepository.countWithRole("MONITOR")).thenReturn(5L);

        service.deleteRole(admin(), "monitor", CORRELATION_ID);

        InOrder order = inOrder(userRepository, roleRepository);
        order.verify(userRepository).markSessionCutByRole("MONITOR", CUT);
        order.verify(userRepository).replaceOnlyRole("MONITOR", "ESTUDIANTE");
        order.verify(userRepository).removeRoleFromAll("MONITOR");
        order.verify(roleRepository).delete(entity);
        order.verify(roleRepository).flush();

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.ELIMINACION_ROL, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("rol:MONITOR", event.recursoAfectado());
        assertEquals("usuarios_a_estudiante=2;usuarios_que_solo_perdieron_el_rol=3", event.detalle());
    }

        /**
     * A role without users is deleted and the detail carries zeros
     */
    @Test
    void deleteRole_withoutUsers_deletesAndAuditsZeros() {
        RoleEntity entity = role("MONITOR", false, PERM_A);
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(entity));

        service.deleteRole(admin(), "MONITOR", CORRELATION_ID);

        verify(roleRepository).delete(entity);
        assertEquals("usuarios_a_estudiante=0;usuarios_que_solo_perdieron_el_rol=0", singleEvent().detalle());
    }

        /**
     * A system role cannot be deleted: it answers 409 and does not touch users
     */
    @Test
    void deleteRole_systemRole_isAConflict() {
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.of(role("ESTUDIANTE", true, PERM_C)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.deleteRole(admin(), "ESTUDIANTE", CORRELATION_ID));

        assertEquals("Los roles de sistema no se pueden eliminar.", error.getMessage());
        verifyNoInteractions(userRepository);
        verify(roleRepository, never()).delete(any(RoleEntity.class));
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:ESTUDIANTE", "ROL_DE_SISTEMA");
    }

        /**
     * A non-existent role answers 404
     */
    @Test
    void deleteRole_unknownRole_isNotFound() {
        when(roleRepository.findByName("FANTASMA")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> service.deleteRole(admin(), "FANTASMA", CORRELATION_ID));

        verifyNoInteractions(userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:FANTASMA", "ROL_NO_ENCONTRADO");
    }

        /**
     * An invalid name in the path answers 400
     */
    @Test
    void deleteRole_invalidPathName_isRejected() {
        assertThrows(InvalidRequestException.class, () -> service.deleteRole(admin(), "rol-malo", CORRELATION_ID));

        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, null, "VALIDACION");
    }

        /**
     * Without ms01:role:manage deleting a role answers 403
     */
    @Test
    void deleteRole_withoutManage_isForbidden() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.deleteRole(student(), "MONITOR", CORRELATION_ID));

        verifyNoInteractions(roleRepository, userRepository);
        assertSingleFailure(AuditAction.ELIMINACION_ROL, null, "SIN_PERMISO");
    }

        /**
     * If any statement of the deletion fails the failure is audited and propagated so the transaction is rolled back
     */
    @Test
    void deleteRole_unexpectedFailure_isAuditedAndPropagated() {
        IllegalStateException failure = new IllegalStateException("database down");
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));
        when(userRepository.replaceOnlyRole(anyString(), anyString())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.deleteRole(admin(), "MONITOR", CORRELATION_ID));

        assertSame(failure, thrown);
        verify(roleRepository, never()).delete(any(RoleEntity.class));
        assertSingleFailure(AuditAction.ELIMINACION_ROL, "rol:MONITOR", "ERROR_INTERNO");
    }

    // ------------------------------------------------------------------ transactions and correlation

        /**
     * The write operations run in a single transaction and the read ones in a read-only one
     *
     * @throws NoSuchMethodException if any method does not exist
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
        assertTrue(type.getMethod("listPermissions", RequesterIdentity.class).getAnnotation(Transactional.class)
                .readOnly());
        assertTrue(type.getMethod("listAlertRecipientRoles", RequesterIdentity.class)
                .getAnnotation(Transactional.class).readOnly());
    }

        /**
     * An absent or invalid X-Correlation-ID is replaced by a generated one that is used in the audit
     */
    @Test
    void writeOperations_withoutAValidCorrelationId_generateOne() {
        when(roleRepository.findByName("MONITOR")).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        service.deleteRole(admin(), "MONITOR", "no es válido porque tiene espacios");

        String correlation = singleEvent().correlationId();
        assertEquals(36, correlation.length());
        UUID.fromString(correlation);
    }

        /**
     * The query operations do not audit
     */
    @Test
    void readOperations_doNotAudit() {
        when(roleRepository.findByName(eq("MONITOR"))).thenReturn(Optional.of(role("MONITOR", false, PERM_A)));

        service.getRole(admin(), "MONITOR");
        service.listPermissions(admin());

        verifyNoInteractions(auditPublisher);
    }
}
