package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "username không được để trống")
    @Size(min = 4, max = 50, message = "username phải từ 4-50 ký tự")
    private String username;

    @NotBlank(message = "password không được để trống")
    @Size(min = 6, message = "password phải tối thiểu 6 ký tự")
    private String password;

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không đúng định dạng")
    private String email;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
