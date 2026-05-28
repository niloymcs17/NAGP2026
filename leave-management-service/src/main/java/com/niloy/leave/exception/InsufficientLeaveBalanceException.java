package com.niloy.leave.exception;

public class InsufficientLeaveBalanceException extends RuntimeException {
    public InsufficientLeaveBalanceException(int remaining, int requested) {
        super("Insufficient leave balance. Remaining: " + remaining + ", Requested: " + requested);
    }
}
