package com.niloy.notification.consumer;

import com.niloy.notification.config.RabbitMQConfig;
import com.niloy.notification.event.LeaveNotificationEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LeaveNotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consumeLeaveNotification(LeaveNotificationEvent event) {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("SIMULATED NOTIFICATION LOG ENTRY");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Event Type  : " + event.getEventType());
        System.out.println("Employee ID : " + event.getEmployeeId());
        if (event.getEmployeeName() != null) {
            System.out.println("Employee Name: " + event.getEmployeeName());
        }
        System.out.println("Manager ID  : " + event.getManagerId());
        System.out.println("Leave ID    : " + event.getLeaveId());
        System.out.println("Leave Type  : " + event.getLeaveType());
        System.out.println("Date Range  : " + event.getStartDate() + " to " + event.getEndDate());
        System.out.println("Total Days  : " + event.getNumberOfDays());
        System.out.println("Status      : " + event.getStatus());
        System.out.println("Details     : " + event.getComments());
        System.out.println("--------------------------------------------------------------------------------");
    }
}
