package com.niloy.leave.model;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_leave_type",
                columnNames = {"employeeId", "leaveType"}
        ))
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private String leaveType;

    @Column(nullable = false)
    private int allocated;

    @Column(nullable = false)
    private int used;

    public LeaveBalance() {
    }

    public LeaveBalance(Long id, Long employeeId, String leaveType, int allocated, int used) {
        this.id = id;
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.allocated = allocated;
        this.used = used;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public int getAllocated() {
        return allocated;
    }

    public void setAllocated(int allocated) {
        this.allocated = allocated;
    }

    public int getUsed() {
        return used;
    }

    public void setUsed(int used) {
        this.used = used;
    }

    public int getRemaining() {
        return allocated - used;
    }
}
