# MS01 - IDENTITY AND ACCESS

MS01 - Users, authentication, roles, permissions, and sessions service.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |
| Rodrigo Gualtero                | `Rodrigoreg92`                    |

---

# MS01 - Identidad y Acceso [CampusSeguro]

Microservicio encargado de la autenticación, autorización, gestión de usuarios, roles, permisos y sesiones para la plataforma CampusSeguro.

## Documentación

La documentación de MS01 está centralizada en el repositorio [documentation](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/main/ms01-identity-and-access):

* [Requerimientos y tabla de endpoints](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/1_requirements.md)
* [Formularios y validaciones](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/2_forms-inputs.md)
* [Especificación del JWT](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/3_jwt-spec.md)
* [Modelo de datos](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/4_modelo-datos.md)
* [Contrato OpenAPI](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/ms01-identity-and-access/api/swagger.yml)
* Reglas transversales: [Estándares de Integración](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Est%C3%A1ndares-de-Integraci%C3%B3n-y-Contratos-Transversales) · [Matriz Rol‐Permiso](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Matriz-Rol%E2%80%90Permiso)

## Stack Tecnológico
* Java 21 / Spring Boot 4
* Spring Security · Spring Data JPA
* JWT RS256 (emisión; la validación la hace el API Gateway)
* PostgreSQL · Maven · Docker
