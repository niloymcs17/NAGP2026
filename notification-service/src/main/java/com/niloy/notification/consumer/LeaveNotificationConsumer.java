package com.niloy.notification.consumer;

import com.niloy.notification.config.RabbitMQConfig;
import com.niloy.notification.event.LeaveNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeaveNotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consumeLeaveNotification(LeaveNotificationEvent event) {
        log.info("SIMULATED NOTIFICATION — type={}, employeeId={}, leaveId={}, status={}, dates={} to {}",
                event.getEventType(),
                event.getEmployeeId(),
                event.getLeaveId(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate()
        );
    }
}
