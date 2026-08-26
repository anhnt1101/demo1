package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class StatusListRequest {

    private List<Long> ids;

    private Integer status;

}
