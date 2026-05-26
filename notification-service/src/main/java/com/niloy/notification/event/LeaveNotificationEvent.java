package com.niloy.notification.event;

import java.io.Serializable;
import java.time.LocalDate;

public class LeaveNotificationEvent implements Serializable {
    private String eventType;
    private Long employeeId;
    private String employeeName;
    private Long managerId;
    private Long leaveId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private int numberOfDays;
    private String status;
    private String comments;

    public LeaveNotificationEvent() {
    }

    public LeaveNotificationEvent(String eventType, Long employeeId, String employeeName, Long managerId, Long leaveId, String leaveType, LocalDate startDate, LocalDate endDate, int numberOfDays, String status, String comments) {
        this.eventType = eventType;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.managerId = managerId;
        this.leaveId = leaveId;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfDays = numberOfDays;
        this.status = status;
        this.comments = comments;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public Long getLeaveId() {
        return leaveId;
    }

    public void setLeaveId(Long leaveId) {
        this.leaveId = leaveId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getNumberOfDays() {
        return numberOfDays;
    }

    public void setNumberOfDays(int numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
