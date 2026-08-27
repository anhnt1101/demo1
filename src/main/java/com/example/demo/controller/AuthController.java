//package com.example.demo.controller;
//
//import com.example.demo.dto.Response.AuthResponse;
//import com.example.demo.dto.Request.LoginRequest;
//import com.example.demo.dto.Response.LoginResponse;
//import com.example.demo.dto.Request.RegisterRequest;
//import com.example.demo.service.impl.AuthService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/auth")
//@RequiredArgsConstructor
//public class AuthController {
//    private final AuthService authService;
//
//    @PostMapping("/login")
//    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
//        return ResponseEntity.ok(authService.login(request));
//    }
//
//    @PostMapping("/register")
//    public ResponseEntity<AuthResponse> register(
//            @Valid @RequestBody RegisterRequest req
//    ) {
//        AuthResponse response = authService.register(req);
//
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(response);
//    }
//
//}
