package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AuthResponse {
    private final String accessToken;
    private final String tokenType = "Bearer";
    private final String username;
    private final List<String> roles;
}
