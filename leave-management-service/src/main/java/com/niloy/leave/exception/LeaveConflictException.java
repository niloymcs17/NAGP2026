package com.niloy.leave.exception;

public class LeaveConflictException extends RuntimeException {
    public LeaveConflictException() {
        super("Overlapping leave request detected for these dates.");
    }
}
