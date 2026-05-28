package com.niloy.leave.controller;

import com.niloy.leave.config.RabbitMQConfig;
import com.niloy.leave.event.LeaveNotificationEvent;
import com.niloy.leave.exception.AccessDeniedException;
import com.niloy.leave.exception.InsufficientLeaveBalanceException;
import com.niloy.leave.exception.InvalidLeaveRequestException;
import com.niloy.leave.exception.LeaveConflictException;
import com.niloy.leave.exception.LeaveRequestNotFoundException;
import com.niloy.leave.model.LeaveBalance;
import com.niloy.leave.model.LeaveRequest;
import com.niloy.leave.repository.LeaveBalanceRepository;
import com.niloy.leave.repository.LeaveRequestRepository;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
            throw new LeaveRequestNotFoundException(employeeId);
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

        log.info("Leave application received — employeeId={}, type={}, days={}", employeeId, request.getLeaveType(), request.getNumberOfDays());

        if (request.getStartDate().isBefore(LocalDate.now())) {
            log.warn("Leave rejected — past start date — employeeId={}", employeeId);
            throw new InvalidLeaveRequestException("Start date cannot be in the past");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new InvalidLeaveRequestException("Start date must be less than or equal to end date");
        }

        int calculatedDays = calculateLeaveDays(request.getStartDate(), request.getEndDate());
        if (calculatedDays <= 0) {
            throw new InvalidLeaveRequestException("Leave request must cover at least one working day (excluding weekends and public holidays)");
        }
        request.setNumberOfDays(calculatedDays);

        String leaveType = request.getLeaveType().toUpperCase();
        if (!leaveType.equals("CASUAL") && !leaveType.equals("SICK") && !leaveType.equals("PRIVILEGE")) {
            throw new InvalidLeaveRequestException("Invalid leave type. Must be CASUAL, SICK, or PRIVILEGE");
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(employeeId, leaveType)
                .orElseThrow(() -> new InvalidLeaveRequestException("Leave balance record not found for type: " + leaveType));

        if (balance.getRemaining() < request.getNumberOfDays()) {
            log.warn("Leave rejected — insufficient balance — employeeId={}, remaining={}, requested={}", employeeId, balance.getRemaining(), request.getNumberOfDays());
            throw new InsufficientLeaveBalanceException(balance.getRemaining(), request.getNumberOfDays());
        }

        boolean hasOverlap = leaveRequestRepository.existsOverlappingRequest(
                employeeId, request.getStartDate(), request.getEndDate());
        if (hasOverlap) {
            log.warn("Leave rejected — overlapping dates — employeeId={}", employeeId);
            throw new LeaveConflictException();
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

        log.info("Leave applied successfully — leaveId={}, employeeId={}", savedRequest.getId(), employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
    }

    private int calculateLeaveDays(LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate current = startDate;

        java.util.Set<java.time.MonthDay> publicHolidays = java.util.Set.of(
            java.time.MonthDay.of(5, 1),   // 1 May
            java.time.MonthDay.of(8, 15),  // 15 Aug
            java.time.MonthDay.of(11, 4),  // 4 Nov
            java.time.MonthDay.of(11, 21)  // 21 Nov
        );

        while (!current.isAfter(endDate)) {
            java.time.DayOfWeek dayOfWeek = current.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY);
            boolean isPublicHoliday = publicHolidays.contains(java.time.MonthDay.from(current));

            if (!isWeekend && !isPublicHoliday) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
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
            throw new AccessDeniedException("Only managers can view team leave requests");
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
            log.warn("Forbidden — userId={} (role={}) attempted manager-only operation", managerId, userRole);
            throw new AccessDeniedException("Only managers can approve leave requests");
        }

        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Leave request not found — id={}", id);
                    return new LeaveRequestNotFoundException(id);
                });

        if (!request.getManagerId().equals(managerId)) {
            throw new AccessDeniedException("You are not authorized to approve this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            throw new InvalidLeaveRequestException("Only PENDING requests can be approved. Current status: " + request.getStatus());
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(request.getEmployeeId(), request.getLeaveType())
                .orElseThrow(() -> new InsufficientLeaveBalanceException(0, request.getNumberOfDays()));

        if (balance.getRemaining() < request.getNumberOfDays()) {
            throw new InsufficientLeaveBalanceException(balance.getRemaining(), request.getNumberOfDays());
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

        log.info("Leave approved — leaveId={}, managerId={}", savedRequest.getId(), managerId);
        return ResponseEntity.ok(savedRequest);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeave(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") Long managerId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!userRole.equals("MANAGER")) {
            log.warn("Forbidden — userId={} (role={}) attempted manager-only operation", managerId, userRole);
            throw new AccessDeniedException("Only managers can reject leave requests");
        }

        String comment = body.getOrDefault("comment", "Rejected by manager");

        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Leave request not found — id={}", id);
                    return new LeaveRequestNotFoundException(id);
                });

        if (!request.getManagerId().equals(managerId)) {
            throw new AccessDeniedException("You are not authorized to reject this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            throw new InvalidLeaveRequestException("Only PENDING requests can be rejected. Current status: " + request.getStatus());
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

        log.info("Leave rejected — leaveId={}, managerId={}", savedRequest.getId(), managerId);
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

        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new LeaveRequestNotFoundException(id));

        if (!request.getEmployeeId().equals(employeeId)) {
            throw new AccessDeniedException("You are not authorized to cancel this request");
        }

        if (!request.getStatus().equals("PENDING")) {
            throw new InvalidLeaveRequestException("Only PENDING requests can be cancelled. Current status: " + request.getStatus());
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

        log.info("Leave cancelled — leaveId={}, employeeId={}", savedRequest.getId(), employeeId);
        return ResponseEntity.ok(savedRequest);
    }
}
