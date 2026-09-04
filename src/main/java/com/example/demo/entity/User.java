package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.type.YesNoConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PMH_USERS")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "PASSWORD", nullable = false, length = 255)
    private String password; // luôn lưu dạng đã BCrypt hash

    @Column(name = "EMAIL", nullable = false, length = 100)
    private String email;

    @Convert(converter = YesNoConverter.class)
    @Column(name = "ENABLED", length = 1)
    private Boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "PMH_USER_ROLE",
            joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID")
    )
    private Set<Role> roles = new HashSet<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleCode()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
    // Cột ROLE / ROLES (STRINGARRAY) trên bảng USERS không map ở đây —
    // xem ghi chú đầu bài về 2 cột này.

}
