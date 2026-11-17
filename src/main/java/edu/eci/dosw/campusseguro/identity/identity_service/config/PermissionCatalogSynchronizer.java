package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PermissionRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;

/**
 * Synchronizes at startup the MS01 permissions of the Permission enum with the permissions table (RN-MS01-42)
 *
 * It touches only the rows with the ms01: prefix and everything happens in a single transaction: it inserts the enum
 * permissions that are missing, it does not modify the ones that already exist (the description of the enum is used
 * only when inserting) and it deletes the ones that are no longer in the enum, leaving a warning with the roles that
 * had each one and recording the session cut of those roles before deleting them, the foreign key of role_permissions
 * removes them from the roles. If the database rejects the deletion because it would leave a role without permissions
 * or without the minimums of ADMIN or ESTUDIANTE, the transaction is rolled back and MS01 does not start. The
 * permissions from ms02 to ms08 are not touched: they are managed by whoever is responsible for the database
 */
@Component
@Order(1)
public class PermissionCatalogSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSynchronizer.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final DatabaseTimeService timeService;

        /**
     * Creates the synchronizer with its dependencies
     *
     * @param permissionRepository repository to access the permission catalog
     * @param roleRepository repository to access the roles and their permissions
     * @param timeService single database clock with which the session cuts are set
     */
    public PermissionCatalogSynchronizer(PermissionRepository permissionRepository, RoleRepository roleRepository,
            DatabaseTimeService timeService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.timeService = timeService;
    }

        /**
     * Runs the synchronization once the application has started
     *
     * @param args startup arguments of the application
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        synchronize();
    }

        /**
     * Inserts the MS01 permissions that are missing in the table and deletes the ones that are no longer in the enum
     *
     * @return number of inserted permissions plus number of deleted permissions
     */
    public int synchronize() {
        List<PermissionEntity> stored = permissionRepository.findAllByNameStartingWith(Permission.PREFIX);
        Set<String> storedCodes = new HashSet<>();
        for (PermissionEntity entity : stored) {
            storedCodes.add(entity.getName());
        }

        Set<String> declaredCodes = new HashSet<>();
        int inserted = 0;
        for (Permission permission : Permission.values()) {
            declaredCodes.add(permission.getCode());
            if (!storedCodes.contains(permission.getCode())) {
                PermissionEntity entity = new PermissionEntity();
                entity.setName(permission.getCode());
                entity.setDescription(permission.getDescription());
                permissionRepository.save(entity);
                inserted++;
                log.info("Permission {} added to the catalog", permission.getCode());
            }
        }

        int deleted = 0;
        for (String code : storedCodes) {
            if (!declaredCodes.contains(code)) {
                List<String> affectedRoles = roleRepository.findNamesByPermission(code);
                log.warn("El permiso {} ya no está en el código de MS01: se borra del catálogo y se quita de los "
                        + "roles {}", code, affectedRoles);
                roleRepository.markSessionCutByPermission(code, timeService.now());
                permissionRepository.deleteByCode(code);
                deleted++;
            }
        }
        return inserted + deleted;
    }
}
