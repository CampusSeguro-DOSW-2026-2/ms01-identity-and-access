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
 * RN-MS01-42: al arrancar, MS01 sincroniza solo los permisos con prefijo ms01: del enum con la tabla permisos,
 * inserta los que faltan, borra los que ya no están en el código y deja intactos los existentes y los de los
 * demás módulos
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
     * Crea el sincronizador con un reloj de base de datos fijo
     */
    @BeforeEach
    void setUp() {
        synchronizer = new PermissionCatalogSynchronizer(permissionRepository, roleRepository,
                FixedDatabaseTime.at(NOW));
    }

    /**
     * Permisos de MS01 que ya están guardados, con una descripción distinta a la del enum
     *
     * @return una entidad por cada permiso del enum
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
     * Con la tabla sin permisos de MS01 se insertan los 10 del enum con su código y su descripción
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
     * Solo se insertan los permisos que faltan y los existentes no se modifican, aunque su descripción difiera
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
     * Con todos los permisos de MS01 ya guardados no se inserta ni se borra nada
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
     * Un permiso ms01:* que ya no está en el enum se borra: antes se registra el corte de sesión de los roles que
     * lo tienen, y el enum no se toca
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
     * Solo se consultan los permisos con prefijo ms01:, los de los demás módulos nunca se leen ni se borran
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
     * Si la base de datos rechaza el borrado (un rol quedaría sin permisos o sin los mínimos de ADMIN o
     * ESTUDIANTE) el error se propaga para que la transacción se revierta y MS01 no arranque
     */
    @Test
    void synchronize_databaseRejectsTheDeletion_propagatesTheError() {
        List<PermissionEntity> stored = storedMs01();
        stored.add(TestEntities.permission("ms01:legacy:old"));
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(stored);
        when(roleRepository.findNamesByPermission("ms01:legacy:old")).thenReturn(List.of("MONITOR"));
        when(permissionRepository.deleteByCode("ms01:legacy:old"))
                .thenThrow(new DataIntegrityViolationException("el rol MONITOR quedaría sin permisos"));

        assertThrows(DataIntegrityViolationException.class, () -> synchronizer.synchronize());
    }

    /**
     * Al arrancar la aplicación el runner ejecuta la sincronización
     */
    @Test
    void run_executesTheSynchronization() {
        when(permissionRepository.findAllByNameStartingWith("ms01:")).thenReturn(storedMs01());

        synchronizer.run(null);

        verify(permissionRepository).findAllByNameStartingWith("ms01:");
    }

    /**
     * El runner corre en una sola transacción y antes de la creación del primer administrador
     *
     * @throws NoSuchMethodException si el método run no existe
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
