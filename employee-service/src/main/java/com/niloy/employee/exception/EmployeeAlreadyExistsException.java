package com.niloy.employee.exception;

public class EmployeeAlreadyExistsException extends RuntimeException {
    public EmployeeAlreadyExistsException(Long id) {
        super("Employee already exists with ID: " + id);
    }
}
