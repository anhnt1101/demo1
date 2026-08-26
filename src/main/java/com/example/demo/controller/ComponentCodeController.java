package com.example.demo.controller;

import com.example.demo.entity.ComponentCode;
import com.example.demo.service.ComponentCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/component-code")
@CrossOrigin(origins = "http://localhost:4200/")
public class ComponentCodeController {

    @Autowired
    private ComponentCodeService componentCodeService;

    @GetMapping
    public ResponseEntity<List<ComponentCode>> getAll(){
        return ResponseEntity.ok(componentCodeService.getAll());
    }

}
