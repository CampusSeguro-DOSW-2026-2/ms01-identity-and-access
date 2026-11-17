package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.config.BootstrapAdminInitializer;
import edu.eci.dosw.campusseguro.identity.identity_service.config.PermissionCatalogSynchronizer;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PermissionRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;

/**
 * RN-MS01-42: at startup, MS01 synchronizes only the permissions with the ms01: prefix of the enum with the permissions
 * table, inserts the missing ones, deletes those that are no longer in the code and leaves untouched the existing ones
 * and those of the other modules
 */
@ExtendWith(MockitoExtension.class)
class PermissionCatalogSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00.750Z");
    private static final Instant CUT = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    private PermissionCatalogSynchronizer synchronizer;

        /**
     * Creates the synchronizer with a fixed database clock
     */
    @BeforeEach
    void setUp() {
        synchronizer = new PermissionCatalogSynchronizer(permissionRepository, roleRepository,
                FixedDatabaseTime.at(NOW));
    }

        /**
     * MS01 permissions that are already stored, with a description different from the one of the enum
     *
     * @return one entity for each permission of the enum
     */
    private List<PermissionEntity> storedMs01() {
        List<PermissionEntity> stored = new ArrayList<>();
        for (Permission permission : Permission.values()) {
            PermissionEntity entity = TestEntities.permission(permission.getCode());
            entity.setDescription("Descripción cambiada por el responsable de la base de datos");
            stored.add(entity);
        }
        return stored;
    }

        /**
     * With the table without MS01 permissions the 10 of the enum are inserted with their code and their description
     */
    @Test
    void synchronize_emptyCatalog_insertsEveryEnumPermission() {
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(List.of());

        int changes = synchronizer.synchronize();

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository, times(Permission.values().length)).save(captor.capture());
        assertEquals(Permission.values().length, changes);
        for (int i = 0; i < Permission.values().length; i++) {
            assertEquals(Permission.values()[i].getCode(), captor.getAllValues().get(i).getName());
            assertEquals(Permission.values()[i].getDescription(), captor.getAllValues().get(i).getDescription());
        }
        verify(permissionRepository, never()).deleteByCode(anyString());
        verifyNoInteractions(roleRepository);
    }

        /**
     * Only the missing permissions are inserted and the existing ones are not modified, even if their description
     * differs
     */
    @Test
    void synchronize_oneMissingPermission_insertsOnlyThatOneAndKeepsTheExistingOnes() {
        List<PermissionEntity> stored = storedMs01();
        PermissionEntity missing = stored.remove(0);
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(stored);

        int changes = synchronizer.synchronize();

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        assertEquals(1, changes);
        assertEquals(missing.getName(), captor.getValue().getName());
        for (PermissionEntity existing : stored) {
            assertEquals("Descripción cambiada por el responsable de la base de datos", existing.getDescription());
        }
        verify(permissionRepository, never()).deleteByCode(anyString());
    }

        /**
     * With all the MS01 permissions already stored nothing is inserted or deleted
     */
    @Test
    void synchronize_catalogAlreadySynchronized_changesNothing() {
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(storedMs01());

        int changes = synchronizer.synchronize();

        assertEquals(0, changes);
        verify(permissionRepository, never()).save(any());
        verify(permissionRepository, never()).deleteByCode(anyString());
        verifyNoInteractions(roleRepository);
    }

        /**
     * An ms01:* permission that is no longer in the enum is deleted: before that the session cut of the roles that have
     * it is recorded, and the enum is not touched
     */
    @Test
    void synchronize_permissionNoLongerInTheEnum_cutsTheRolesThenDeletesIt() {
        List<PermissionEntity> stored = storedMs01();
        stored.add(TestEntities.permission("ms01:legacy:old"));
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(stored);
        when(roleRepository.findNamesByPermission("ms01:legacy:old")).thenReturn(List.of("ADMIN", "MONITOR"));

        int changes = synchronizer.synchronize();

        assertEquals(1, changes);
        InOrder order = inOrder(roleRepository, permissionRepository);
        order.verify(roleRepository).findNamesByPermission("ms01:legacy:old");
        order.verify(roleRepository).markSessionCutByPermission("ms01:legacy:old", CUT);
        order.verify(permissionRepository).deleteByCode("ms01:legacy:old");
        verify(permissionRepository, never()).save(any());
    }

        /**
     * Only the permissions with the ms01: prefix are queried, those of the other modules are never read or deleted
     */
    @Test
    void synchronize_neverReadsOrDeletesPermissionsOfOtherModules() {
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(storedMs01());

        synchronizer.synchronize();

        verify(permissionRepository).findAllByNameStartingWith("ms01:");
        verify(permissionRepository, never()).findAll();
        verify(permissionRepository, never()).deleteByCode(anyString());
        verifyNoMoreInteractions(permissionRepository);
    }

        /**
     * If the database rejects the deletion (a role would lose one of its mandatory permissions) the error is propagated
     * so the transaction is rolled back and MS01 does not start
     */
    @Test
    void synchronize_databaseRejectsTheDeletion_propagatesTheError() {
        List<PermissionEntity> stored = storedMs01();
        stored.add(TestEntities.permission("ms01:legacy:old"));
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(stored);
        when(roleRepository.findNamesByPermission("ms01:legacy:old")).thenReturn(List.of("MONITOR"));
        when(permissionRepository.deleteByCode("ms01:legacy:old"))
                .thenThrow(new DataIntegrityViolationException("the role MONITOR would lose a mandatory permission"));

        assertThrows(DataIntegrityViolationException.class, () -> synchronizer.synchronize());
    }

        /**
     * When the application starts the runner executes the synchronization
     */
    @Test
    void run_executesTheSynchronization() {
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(storedMs01());

        synchronizer.run(null);

        verify(permissionRepository).findAllByNameStartingWith("ms01:");
    }

        /**
     * The runner runs in a single transaction and before the creation of the first administrator
     *
     * @throws NoSuchMethodException if the run method does not exist
     */
    @Test
    void run_isTransactionalAndRunsBeforeTheBootstrapAdmin() throws NoSuchMethodException {
        assertTrue(PermissionCatalogSynchronizer.class
                .getMethod("run", org.springframework.boot.ApplicationArguments.class)
                .isAnnotationPresent(Transactional.class));
        int order = PermissionCatalogSynchronizer.class.getAnnotation(Order.class).value();
        assertTrue(order < Ordered.LOWEST_PRECEDENCE);
        assertNull(BootstrapAdminInitializer.class.getAnnotation(Order.class));
    }
}
