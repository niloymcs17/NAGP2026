package com.niloy.employee.model;

import jakarta.persistence.*;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    private Long id; // Matches the User ID from Authentication Service

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String role; // "EMPLOYEE" or "MANAGER"

    private Long managerId;

    public Employee() {
    }

    public Employee(Long id, String username, String fullName, String email, String role, Long managerId) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.managerId = managerId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }
}
