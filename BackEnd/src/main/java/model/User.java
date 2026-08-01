package com.haris.SpringEcom.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;


@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users", indexes = {
        @Index(name = "idx_provider_id_provider_type", columnList = "providerId, providerType")
})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;
    private String password;

    private String providerId;

    @Enumerated(EnumType.STRING)
    private AuthProviderType providerType;

    // RBAC: Every user has a single role (USER or ADMIN).
    // Stored as a plain string in the DB column (e.g., "ADMIN", "USER")
    // so it is human-readable without needing to decode a number.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * RBAC: Spring Security calls this method on every secured request.
     * We return a SimpleGrantedAuthority with the "ROLE_" prefix because
     * @PreAuthorize("hasRole('ADMIN')") internally looks for "ROLE_ADMIN".
     * If role is somehow null (legacy data), we fall back to USER to be safe.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role effectiveRole = (this.role != null) ? this.role : Role.USER;
        return List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name()));
    }
}