package com.app.heartbound.repositories;

import com.app.heartbound.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    // Add custom query methods here if needed in the future
}
