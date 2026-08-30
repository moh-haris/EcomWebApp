package com.haris.SpringEcom.repo;


import com.haris.SpringEcom.model.AuthProviderType;
import com.haris.SpringEcom.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByProviderIdAndProviderType(String provider, AuthProviderType providerType);

}
