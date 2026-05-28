package com.niloy.leave.consumer;

import com.niloy.leave.config.RabbitMQConfig;
import com.niloy.leave.event.EmployeeCreatedEvent;
import com.niloy.leave.model.LeaveBalance;
import com.niloy.leave.repository.LeaveBalanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmployeeCreatedConsumer {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @RabbitListener(queues = RabbitMQConfig.EMPLOYEE_QUEUE)
    public void consumeEmployeeCreated(EmployeeCreatedEvent event) {
        log.info("Received employee.created event — employeeId={}, username={}", event.getEmployeeId(), event.getUsername());

        // Check and insert each leave type individually to be idempotent.
        // If a message is redelivered (e.g. after a restart), we skip types already initialized.
        initBalanceIfAbsent(event.getEmployeeId(), "CASUAL",    12);
        initBalanceIfAbsent(event.getEmployeeId(), "SICK",      10);
        initBalanceIfAbsent(event.getEmployeeId(), "PRIVILEGE", 15);
        log.info("Leave balances initialized — employeeId={}", event.getEmployeeId());
    }

    private void initBalanceIfAbsent(Long employeeId, String leaveType, int allocated) {
        boolean exists = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(employeeId, leaveType)
                .isPresent();
        if (!exists) {
            leaveBalanceRepository.save(new LeaveBalance(null, employeeId, leaveType, allocated, 0));
            log.debug("Leave balances already exist for employeeId={} — skipping", employeeId);
        }
    }
}

