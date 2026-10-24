package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Permisos de CampusSeguro (RN-MS01-14).
 *
 * Cada permiso se identifica con un codigo modulo:recurso:accion
 *
 */
public enum Permission {

    // MS01-Identidad y acceso
    MS01_PROFILE_READ("ms01:profile:read", "Consultar el propio perfil"),
    MS01_PROFILE_WRITE("ms01:profile:write", "Actualizar el propio perfil (nombres y apellidos)"),
    MS01_PASSWORD_CHANGE("ms01:password:change", "Cambiar la propia contraseña"),
    MS01_SESSION_LOGOUT("ms01:session:logout", "Cerrar sesión"),
    MS01_ROLE_READ_OWN("ms01:role:read_own", "Consultar los propios roles y sus permisos"),
    MS01_ROLE_READ_ALL("ms01:role:read_all", "Consultar el catálogo completo de roles y permisos"),
    MS01_USER_READ("ms01:user:read", "Listar, buscar y consultar usuarios"),
    MS01_USER_STATUS("ms01:user:status", "Activar o inactivar usuarios"),
    MS01_USER_ROLES("ms01:user:roles", "Agregar o retirar roles a usuarios"),

    // MS02-Campus y seguridad
    MS02_CAMPUS_READ("ms02:campus:read", "Consultar edificios, pisos, espacios y zonas seguras (puntos de encuentro)"),
    MS02_ROUTE_READ("ms02:route:read", "Consultar la ruta de evacuación recomendada y listar o ver las rutas de evacuación"),
    MS02_PREVENTIVE_INFO_READ("ms02:preventive_info:read", "Consultar información preventiva por edificio o espacio"),
    MS02_BUILDING_WRITE("ms02:building:write", "Crear, modificar e inactivar edificios y pisos"),
    MS02_SPACE_WRITE("ms02:space:write", "Gestionar espacios"),
    MS02_SAFE_ZONE_WRITE("ms02:safe_zone:write", "Gestionar zonas seguras y puntos de encuentro (un mismo recurso)"),
    MS02_ROUTE_WRITE("ms02:route:write", "Asociar espacios con rutas de evacuación y modificar su descripción o destino"),
    MS02_ROUTE_STATUS_WRITE("ms02:route_status:write", "Cambiar el estado de una ruta (disponible, bloqueada, restringida)"),
    MS02_QR_MANAGE("ms02:qr:manage", "Asociar códigos QR a zonas o edificios (opcional)"),

    // MS03-Alertas y emergencias
    MS03_ALERT_RECEIVE("ms03:alert:receive", "Recibir alertas y confirmar su recepción"),
    MS03_ALERT_MANAGE("ms03:alert:manage", "Crear, programar, activar, segmentar, modificar y finalizar alertas de cualquier tipo"),
    MS03_ALERT_MANAGE_DRILL("ms03:alert:manage_drill", "Igual que el anterior, solo para alertas de tipo SIMULACRO (originadas desde MS06)"),
    MS03_ALERT_READ_HISTORY("ms03:alert:read_history", "Consultar el historial de alertas"),

    // MS04-Evacuación y check-in
    MS04_CHECKIN_WRITE("ms04:checkin:write", "Reportar y actualizar el propio estado y punto de encuentro"),
    MS04_CHECKIN_READ_OWN("ms04:checkin:read_own", "Consultar el propio estado vigente"),
    MS04_ASSISTANCE_REQUEST("ms04:assistance:request", "Solicitar asistencia desde el check-in"),
    MS04_DASHBOARD_READ("ms04:dashboard:read", "Ver consolidación por punto de encuentro, personas pendientes y dashboard"),
    MS04_HISTORY_READ("ms04:history:read", "Consultar el histórico de check-ins de un evento"),

    // MS05-Brigadistas e incidentes
    MS05_BRIGADIER_ASSIGN("ms05:brigadier:assign", "Asignar brigadistas a edificios, zonas o puntos de encuentro"),
    MS05_PANEL_READ_OWN("ms05:panel:read_own", "Consultar las propias zonas y tareas asignadas"),
    MS05_ASSISTANCE_READ("ms05:assistance:read", "Consultar solicitudes de asistencia"),
    MS05_ASSISTANCE_REGISTER("ms05:assistance:register", "Registrar una solicitud de asistencia"),
    MS05_ASSISTANCE_ASSIGN("ms05:assistance:assign", "Asignar una solicitud a un brigadista y cerrarla"),
    MS05_ASSISTANCE_UPDATE_STATUS("ms05:assistance:update_status", "Cambiar el estado de atención (en atención, resuelta)"),
    MS05_INCIDENT_REPORT("ms05:incident:report", "Registrar un incidente con descripción y evidencia"),
    MS05_INCIDENT_READ("ms05:incident:read", "Consultar y dar seguimiento a incidentes"),
    MS05_INCIDENT_UPDATE("ms05:incident:update", "Actualizar un incidente"),

    // MS06-Simulacros
    MS06_DRILL_MANAGE("ms06:drill:manage", "Crear y editar simulacros, escenario y participantes"),
    MS06_DRILL_EXECUTE("ms06:drill:execute", "Iniciar y terminar un simulacro"),
    MS06_DRILL_EVALUATE("ms06:drill:evaluate", "Evaluar un simulacro"),
    MS06_DRILL_READ("ms06:drill:read", "Consultar simulacros y su seguimiento"),
    MS06_DRILL_COMPARE_REPORT("ms06:drill:compare_report", "Comparar simulacros y consultar el informe final"),

    // MS07-Formación y asistente IA
    MS07_CONTENT_READ("ms07:content:read", "Consultar contenidos del centro de aprendizaje"),
    MS07_QUIZ_TAKE("ms07:quiz:take", "Realizar evaluaciones y consultar el propio progreso"),
    MS07_TUTOR_USE("ms07:tutor:use", "Usar el tutor CampusSeguro"),
    MS07_CONTENT_MANAGE("ms07:content:manage", "Gestionar contenidos y la información que alimenta al tutor"),

    // MS08-Analítica y auditoría
    MS08_INDICATORS_READ("ms08:indicators:read", "Consultar indicadores operativos e históricos"),
    MS08_AUDIT_READ("ms08:audit:read", "Consultar la auditoría");

    private final String code;
    private final String description;

    /**
     * Crea un permiso
     *
     * @param code codigo del permiso
     * @param description descripcion del permiso
     */
    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Devuelve el codigo del permiso
     *
     * @return codigo del permiso
     */
    public String getCode() {
        return code;
    }

    /**
     * Devuelve la descripcion del permiso
     *
     * @return descripcion del permiso
     */
    public String getDescription() {
        return description;
    }

    /**
     * Otorga los permisos a todos los roles
     *
     * @return un conjunto con los permisos comunes a todos los roles
     */
    public static Set<Permission> grantedToEveryRole() {
        return EnumSet.of(
                MS01_PROFILE_READ,
                MS01_PROFILE_WRITE,
                MS01_PASSWORD_CHANGE,
                MS01_SESSION_LOGOUT,
                MS01_ROLE_READ_OWN,
                MS02_CAMPUS_READ,
                MS02_ROUTE_READ,
                MS02_PREVENTIVE_INFO_READ);
    }

    /**
     * Otorga los permisos de la comunidad (COM en la matriz)
     *
     * @return un conjunto con los permisos de los roles considerados parte de la comunidad
     */
    public static Set<Permission> grantedToCommunity() {
        Set<Permission> granted = grantedToEveryRole();
        granted.addAll(EnumSet.of(
                MS03_ALERT_RECEIVE,
                MS04_CHECKIN_WRITE,
                MS04_CHECKIN_READ_OWN,
                MS04_ASSISTANCE_REQUEST,
                MS07_CONTENT_READ,
                MS07_QUIZ_TAKE,
                MS07_TUTOR_USE));
        return granted;
    }
}
