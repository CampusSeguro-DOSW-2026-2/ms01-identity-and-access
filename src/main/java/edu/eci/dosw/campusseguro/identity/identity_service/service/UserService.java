package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Registro de usuarios (RF-MS01-01), gestión del perfil propio (RF-MS01-06) y administración de usuarios:
 * consulta y activación o inactivación (RF-MS01-07)
 */
public interface UserService {

    /**
     * Registra un usuario institucional (RF-MS01-01), cada intento, exitoso o fallido, se audita en MS08
     *
     * @param request datos del formulario de registro
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el usuario creado con su id, correo, roles y estado
     * @throws InvalidRequestException si los datos incumplen el formulario F-01
     * @throws EmailAlreadyExistsException si el correo ya está registrado
     */
    UserResponse create(UserCreateRequest request, String correlationId);

    /**
     * Consulta el perfil del propio solicitante (RF-MS01-03, RF-MS01-06)
     *
     * @param requester identidad de quien consulta
     * @return los datos del usuario identificado por la cabecera de identidad
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws UserNotFoundException si el usuario ya no existe
     */
    UserProfileResponse getMyProfile(RequesterIdentity requester);

    /**
     * Actualiza el perfil propio (RF-MS01-06), la actualización reemplaza el perfil completo
     *
     * @param requester identidad de quien actualiza
     * @param request nombres y apellidos nuevos
     * @return el perfil actualizado
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si los datos incumplen el formulario F-03
     * @throws UserNotFoundException si el usuario ya no existe
     */
    UserProfileResponse updateProfile(RequesterIdentity requester, UserUpdateProfileRequest request);

    /**
     * Consulta un usuario por su id (RF-MS01-05, RF-MS01-07)
     *
     * @param requester identidad de quien consulta
     * @param id id del usuario consultado
     * @return los datos del usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws UserNotFoundException si el usuario no existe
     */
    UserProfileResponse getUserById(RequesterIdentity requester, UUID id);

    /**
     * Lista y busca usuarios con paginación y filtros opcionales (RF-MS01-07, formulario F-07)
     *
     * @param requester identidad de quien consulta
     * @param correo búsqueda parcial por correo, puede ser null
     * @param estado filtro de estado, puede ser null
     * @param rol filtro de rol, puede ser null
     * @param page número de página recibido como texto, null equivale a 0
     * @param size tamaño de página recibido como texto, null equivale a 20
     * @return la página de usuarios con sus roles completos
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si algún parámetro incumple el formulario F-07
     */
    PageResponse<UserProfileResponse> search(RequesterIdentity requester, String correo, String estado, String rol,
            String page, String size);

    /**
     * Activa o inactiva a uno o varios usuarios en una sola operación atómica (RF-MS01-07, formulario
     * F-08), cada usuario procesado se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param request usuarios objetivo y nuevo estado
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el estado anterior y el nuevo de cada usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido o se incluye a sí
     *         mismo
     * @throws InvalidRequestException si el cuerpo incumple el formulario F-08
     * @throws UsersNotFoundException si algún usuario objetivo no existe
     */
    ChangeStatusResponse changeUserStatus(RequesterIdentity requester, ChangeStatusRequest request,
            String correlationId);
}
