package com.example.demo.service.impl;


import com.example.demo.dto.Response.AuthResponse;
import com.example.demo.dto.Request.LoginRequest;
import com.example.demo.dto.Response.LoginResponse;
import com.example.demo.dto.Request.RegisterRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.jwt.JwtUtil;
import com.example.demo.repository.Role.RoleRepository;
import com.example.demo.repository.User.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtTokenUtils;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final RoleRepository roleRepository;

    @Transactional
    public AuthResponse register(RegisterRequest req) {

        // 1. Kiểm tra username
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }

        // 2. Kiểm tra email
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }
        System.out.println("123123122222222222222" + req.getRole() );
        // 3. Lấy ROLE_USER
        Role userRole = roleRepository.findByRoleCode(req.getRole())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Role chưa được seed trong DB"
                        )
                );

        // 4. Tạo User
        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .roles(Set.of(userRole))
                .enabled(true)
                .build();

        // 5. Lưu DB
        userRepository.save(user);

        // 6. Tạo JWT
        String token = jwtTokenUtils.generateToken(user);

        // 7. Lấy danh sách role trả về frontend
        List<String> roles = user.getRoles()
                .stream()
                .map(Role::getRoleName)
                .toList();

        // 8. Trả response
        return new AuthResponse(
                token,
                user.getUsername(),
                roles
        );
    }

    public LoginResponse login(LoginRequest request) {
        // authenticate() tự gọi CustomUserDetailsService + so khớp password qua BCrypt.
        // Sai username/password -> ném BadCredentialsException -> GlobalExceptionHandler trả 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenUtils.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return new LoginResponse(token, userDetails.getUsername(), roles);
    }
}