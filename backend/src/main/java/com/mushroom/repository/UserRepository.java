package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByRoleOrderById(String role);
    long countByRole(String role);
}
