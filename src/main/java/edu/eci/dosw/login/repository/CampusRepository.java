package edu.eci.dosw.login.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.eci.dosw.login.entity.*;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusRepository extends JpaRepository<UserEntity, Long>{
    
}
