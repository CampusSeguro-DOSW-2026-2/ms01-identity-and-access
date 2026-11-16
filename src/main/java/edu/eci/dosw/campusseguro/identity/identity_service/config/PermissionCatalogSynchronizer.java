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
 * Sincroniza al arrancar los permisos de MS01 del enum Permission con la tabla permisos (RN-MS01-42)
 *
 * Solo toca las filas con prefijo ms01: y todo ocurre en una sola transacción: inserta los permisos del enum que
 * faltan, no modifica los que ya existen (la descripción del enum solo se usa al insertar) y borra los que ya no
 * están en el enum, de los que deja un aviso con los roles que lo tenían y registra el corte de sesión de esos
 * roles antes de borrarlos, la clave foránea de rol_permiso los quita de los roles. Si la base rechaza el
 * borrado porque dejaría a un rol sin permisos o sin los mínimos de ADMIN o ESTUDIANTE, la transacción se
 * revierte y MS01 no arranca. Los permisos de ms02 a ms08 no se tocan: los administra el responsable de la base
 * de datos
 */
@Component
@Order(1)
public class PermissionCatalogSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSynchronizer.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final DatabaseTimeService timeService;

    /**
     * Crea el sincronizador con sus dependencias
     *
     * @param permissionRepository repositorio de acceso al catálogo de permisos
     * @param roleRepository repositorio de acceso a los roles y sus permisos
     * @param timeService reloj único de la base de datos con el que se fijan los cortes de sesión
     */
    public PermissionCatalogSynchronizer(PermissionRepository permissionRepository, RoleRepository roleRepository,
            DatabaseTimeService timeService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.timeService = timeService;
    }

    /**
     * Ejecuta la sincronización una vez arrancada la aplicación
     *
     * @param args argumentos de arranque de la aplicación
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        synchronize();
    }

    /**
     * Inserta los permisos de MS01 que faltan en la tabla y borra los que ya no están en el enum
     *
     * @return cantidad de permisos insertados más cantidad de permisos borrados
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
                log.info("Permiso {} agregado al catálogo", permission.getCode());
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
