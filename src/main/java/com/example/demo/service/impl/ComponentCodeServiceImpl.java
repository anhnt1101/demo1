package com.example.demo.service.impl;

import com.example.demo.entity.ComponentCode;
import com.example.demo.repository.ComponentCodeRepository;
import com.example.demo.service.ComponentCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComponentCodeServiceImpl implements ComponentCodeService {

    @Autowired
    ComponentCodeRepository componentCodeRepository;

    @Override
    public List<ComponentCode> getAll() {
        return componentCodeRepository.findAll();
    }
}
