package com.example.demo.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CreateExportRequest {

    @NotBlank(message = "exportType không được để trống")
    private String exportType;

    private Map<String, Object> params;
}