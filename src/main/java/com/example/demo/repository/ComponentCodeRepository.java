package com.example.demo.repository;

import com.example.demo.entity.ComponentCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentCodeRepository extends JpaRepository<ComponentCode,String> {
    ComponentCode findComponentCodeByComponentCode(String componentCode);
}
