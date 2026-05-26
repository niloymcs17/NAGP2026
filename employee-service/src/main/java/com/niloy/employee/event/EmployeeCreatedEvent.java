package com.niloy.employee.event;

import java.io.Serializable;

public class EmployeeCreatedEvent implements Serializable {
    private Long employeeId;
    private String username;
    private String fullName;
    private String email;
    private Long managerId;

    public EmployeeCreatedEvent() {
    }

    public EmployeeCreatedEvent(Long employeeId, String username, String fullName, String email, Long managerId) {
        this.employeeId = employeeId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.managerId = managerId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
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

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }
}
