package com.niloy.employee.controller;

import com.niloy.employee.config.RabbitMQConfig;
import com.niloy.employee.event.EmployeeCreatedEvent;
import com.niloy.employee.model.Employee;
import com.niloy.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping
    public ResponseEntity<?> createEmployee(
            @RequestBody Employee employee,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        
        if (userRole != null && !userRole.equals("MANAGER")) {
            log.warn("Access denied — role '{}' cannot create employees", userRole);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can create employees");
        }

        if (employeeRepository.existsById(employee.getId())) {
            log.warn("Employee creation rejected — ID {} already exists", employee.getId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Employee already exists with this ID");
        }

        Employee savedEmployee = employeeRepository.save(employee);

        // Publish event
        EmployeeCreatedEvent event = new EmployeeCreatedEvent(
                savedEmployee.getId(),
                savedEmployee.getUsername(),
                savedEmployee.getFullName(),
                savedEmployee.getEmail(),
                savedEmployee.getManagerId()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);

        log.info("Employee created — id={}, username={}", savedEmployee.getId(), savedEmployee.getUsername());
        log.info("Published employee.created event for username: {}", savedEmployee.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(savedEmployee);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEmployeeById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long currentUserId,
            @RequestHeader("X-User-Role") String currentUserRole) {

        Employee employee = employeeRepository.findById(id).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found");
        }

        if (currentUserRole.equals("EMPLOYEE")) {
            if (!currentUserId.equals(id)) {
                log.warn("Access denied — userId={} attempted to access employee id={}", currentUserId, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied. Employees can only access their own data.");
            }
        } else if (currentUserRole.equals("MANAGER")) {
            if (!currentUserId.equals(id) && !currentUserId.equals(employee.getManagerId())) {
                log.warn("Access denied — userId={} attempted to access employee id={}", currentUserId, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied. Managers can only access their own or their team members' data.");
            }
        }

        log.debug("Employee retrieved — id={}", id);
        return ResponseEntity.ok(employee);
    }

    @GetMapping("/team")
    public ResponseEntity<?> getTeam(
            @RequestHeader("X-User-Id") Long managerId,
            @RequestHeader("X-User-Role") String currentUserRole) {

        if (!currentUserRole.equals("MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied. Only managers can view team members.");
        }

        List<Employee> team = employeeRepository.findByManagerId(managerId);
        return ResponseEntity.ok(team);
    }
}
