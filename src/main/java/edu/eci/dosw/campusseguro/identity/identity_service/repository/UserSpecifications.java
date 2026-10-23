package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.Collection;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public class UserSpecifications {
    private UserSpecifications() {}

    public static Specification<UserEntity> emailContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(root.<String>get("email"), pattern, '\\');
    }

    public static Specification<UserEntity> hasState(UserState state) {
        return (root, query, cb) -> cb.equal(root.get("state"), state);
    }

    public static Specification<UserEntity> hasRole(UserRole role) {
        return (root, query, cb) -> cb.isMember(role, root.<Collection<UserRole>>get("roles"));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
