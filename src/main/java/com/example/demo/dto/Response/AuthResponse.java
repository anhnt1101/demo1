package com.example.demo.dto.Response;

import lombok.Data;

import java.util.List;

@Data
public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final String username;
    private final List<String> roles;

    public AuthResponse(
            String accessToken,
            String username,
            List<String> roles
    ) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.username = username;
        this.roles = roles;
    }
}
