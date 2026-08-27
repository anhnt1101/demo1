package com.example.demo.dto.Request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequest {
    private Integer page;
    private Integer size;
    private String paramType;
    private String paramValue;
    private String paramName;
    private Integer status;
    private Integer isActive;

}

