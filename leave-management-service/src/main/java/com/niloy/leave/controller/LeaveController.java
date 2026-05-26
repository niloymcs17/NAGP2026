package com.niloy.leave.controller;

import com.niloy.leave.config.RabbitMQConfig;
import com.niloy.leave.event.LeaveNotificationEvent;
import com.niloy.leave.model.LeaveBalance;
import com.niloy.leave.model.LeaveRequest;
import com.niloy.leave.repository.LeaveBalanceRepository;
import com.niloy.leave.repository.LeaveRequestRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leaves")
public class LeaveController {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/balances")
    public ResponseEntity<?> getBalances(
            @RequestHeader("X-User-Id") Long employeeId) {
        
        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployeeId(employeeId);
        if (balances.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Leave balances not found for employee");
        }
        return ResponseEntity.ok(balances);
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyForLeave(
            @RequestBody LeaveRequest request,
            @RequestHeader("X-User-Id") Long employeeId,
            @RequestHeader("X-User-Username") String username) {

        request.setEmployeeId(employeeId);
        request.setStatus("PENDING");

        if (request.getStartDate().isBefore(LocalDate.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Start date cannot be in the past");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Start date must be less than or equal to end date");
        }

        String leaveType = request.getLeaveType().toUpperCase();
        if (!leaveType.equals("CASUAL") && !leaveType.equals("SICK") && !leaveType.equals("PRIVILEGE")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid leave type. Must be CASUAL, SICK, or PRIVILEGE");
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(employeeId, leaveType)
                .orElse(null);

        if (balance == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Leave balance record not found for type: " + leaveType);
        }

        if (balance.getRemaining() < request.getNumberOfDays()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient leave balance. Remaining: " 
                    + balance.getRemaining() + ", Requested: " + request.getNumberOfDays());
        }

        boolean hasOverlap = leaveRequestRepository.existsOverlappingRequest(
                employeeId, request.getStartDate(), request.getEndDate());
        if (hasOverlap) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Overlapping leave request detected for these dates.");
        }

        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        LeaveNotificationEvent event = new LeaveNotificationEvent(
                "APPLICATION",
                employeeId,
                username,
                request.getManagerId(),
                savedRequest.getId(),
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getNumberOfDays(),
                "PENDING",
                "Applied successfully: " + request.getReason()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.LEAVE_EXCHANGE, RabbitMQConfig.LEAVE_ROUTING_KEY, event);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getTeamRequests(
            @RequestHeader("X-User-Id") Long managerId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (!userRole.equals("MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can view team leave requests");
        }

        List<LeaveRequest> requests = leaveRequestRepository.findPendingRequestsForManager(
                managerId, status, employeeId, startDate, endDate);
        
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveLeave(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long managerId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!userRole.equals("MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can approve leave requests");
        }

        LeaveRequest request = leaveRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Leave request not found");
        }

        if (!request.getManagerId().equals(managerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to approve this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only PENDING requests can be approved. Current status: " + request.getStatus());
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(request.getEmployeeId(), request.getLeaveType())
                .orElse(null);

        if (balance == null || balance.getRemaining() < request.getNumberOfDays()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot approve. Insufficient employee leave balance.");
        }

        balance.setUsed(balance.getUsed() + request.getNumberOfDays());
        leaveBalanceRepository.save(balance);

        request.setStatus("APPROVED");
        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        LeaveNotificationEvent event = new LeaveNotificationEvent(
                "APPROVAL",
                request.getEmployeeId(),
                null,
                managerId,
                savedRequest.getId(),
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getNumberOfDays(),
                "APPROVED",
                "Leave request approved by manager"
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.LEAVE_EXCHANGE, RabbitMQConfig.LEAVE_ROUTING_KEY, event);

        return ResponseEntity.ok(savedRequest);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeave(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") Long managerId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!userRole.equals("MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can reject leave requests");
        }

        String comment = body.getOrDefault("comment", "Rejected by manager");

        LeaveRequest request = leaveRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Leave request not found");
        }

        if (!request.getManagerId().equals(managerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to reject this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only PENDING requests can be rejected. Current status: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setRejectionReason(comment);
        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        LeaveNotificationEvent event = new LeaveNotificationEvent(
                "REJECTION",
                request.getEmployeeId(),
                null,
                managerId,
                savedRequest.getId(),
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getNumberOfDays(),
                "REJECTED",
                "Leave request rejected. Reason: " + comment
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.LEAVE_EXCHANGE, RabbitMQConfig.LEAVE_ROUTING_KEY, event);

        return ResponseEntity.ok(savedRequest);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestHeader("X-User-Id") Long employeeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        String filterStatus = null;
        if (status != null && !status.equalsIgnoreCase("ALL")) {
            filterStatus = status.toUpperCase();
        }

        Page<LeaveRequest> history = leaveRequestRepository.findByEmployeeIdAndStatus(
                employeeId,
                filterStatus,
                PageRequest.of(page, size, Sort.by("startDate").descending())
        );

        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelLeave(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employeeId) {

        LeaveRequest request = leaveRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Leave request not found");
        }

        if (!request.getEmployeeId().equals(employeeId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to cancel this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only PENDING requests can be cancelled. Current status: " + request.getStatus());
        }

        request.setStatus("CANCELLED");
        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        LeaveNotificationEvent event = new LeaveNotificationEvent(
                "CANCELLATION",
                employeeId,
                null,
                request.getManagerId(),
                savedRequest.getId(),
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getNumberOfDays(),
                "CANCELLED",
                "Leave request cancelled by employee"
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.LEAVE_EXCHANGE, RabbitMQConfig.LEAVE_ROUTING_KEY, event);

        return ResponseEntity.ok(savedRequest);
    }
}
