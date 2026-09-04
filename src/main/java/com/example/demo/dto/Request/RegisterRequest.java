package com.example.demo.dto.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "username không được để trống")
    @Size(min = 4, max = 50, message = "username phải từ 4-50 ký tự")
    private String username;

    @NotBlank(message = "password không được để trống")
    @Size(min = 6, message = "password phải tối thiểu 6 ký tự")
    private String password;

    @NotBlank(message = "password không được để trống")
    private String role;

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không đúng định dạng")
    private String email;

}
