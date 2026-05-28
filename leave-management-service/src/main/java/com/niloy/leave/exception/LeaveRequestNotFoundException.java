package com.niloy.leave.exception;

public class LeaveRequestNotFoundException extends RuntimeException {
    public LeaveRequestNotFoundException(Long id) {
        super("Leave request not found with ID: " + id);
    }
}
