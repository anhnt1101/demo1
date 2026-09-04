package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PMH_ROLES")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROLE_CODE", nullable = false,length = 50,unique = true)
    private String roleCode;

    @Column(name = "ROLE_NAME", nullable = false,length = 50,unique = true)
    private String roleName;

    @Column(name = "DESCRIPTION", length = 100)
    private String description;

}
