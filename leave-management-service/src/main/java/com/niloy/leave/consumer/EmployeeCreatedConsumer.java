package com.niloy.leave.consumer;

import com.niloy.leave.config.RabbitMQConfig;
import com.niloy.leave.event.EmployeeCreatedEvent;
import com.niloy.leave.model.LeaveBalance;
import com.niloy.leave.repository.LeaveBalanceRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmployeeCreatedConsumer {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @RabbitListener(queues = RabbitMQConfig.EMPLOYEE_QUEUE)
    public void consumeEmployeeCreated(EmployeeCreatedEvent event) {
        System.out.println("Consuming employee.created event for: " + event.getUsername());

        if (leaveBalanceRepository.findByEmployeeId(event.getEmployeeId()).isEmpty()) {
            leaveBalanceRepository.save(new LeaveBalance(null, event.getEmployeeId(), "CASUAL", 12, 0));
            leaveBalanceRepository.save(new LeaveBalance(null, event.getEmployeeId(), "SICK", 10, 0));
            leaveBalanceRepository.save(new LeaveBalance(null, event.getEmployeeId(), "PRIVILEGE", 15, 0));
            System.out.println("Default leave balances initialized for Employee ID: " + event.getEmployeeId());
        } else {
            System.out.println("Leave balances already initialized for Employee ID: " + event.getEmployeeId());
        }
    }
}
