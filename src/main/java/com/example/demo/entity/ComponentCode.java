package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "PMH_COMPONENTS")
public class ComponentCode {

    @Id
    private Long id;

    @Column(name = "COMPONENT_CODE")
    private String componentCode;

    @Column(name = "COMPONENT_NAME")
    private String componentName;

}
