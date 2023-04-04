package com.meonghae.userservice.repository;

import com.meonghae.userservice.dto.UserDto;
import com.meonghae.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    UserDto findByNickname(String username);
    boolean existsByEmail(String email);
}
