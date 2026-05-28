package com.niloy.employee;

import com.niloy.employee.config.RabbitMQConfig;
import com.niloy.employee.event.EmployeeCreatedEvent;
import com.niloy.employee.model.Employee;
import com.niloy.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
public class EmployeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDatabase(EmployeeRepository employeeRepository, RabbitTemplate rabbitTemplate) {
        return args -> {
            if (employeeRepository.count() == 0) {
                // Seed manager
                Employee manager = new Employee(3L, "manager1", "Manager One", "manager1@company.com", "MANAGER", null);
                employeeRepository.save(manager);
                publishEvent(rabbitTemplate, manager);

                // Seed employees
                Employee emp1 = new Employee(1L, "employee1", "Employee One", "employee1@company.com", "EMPLOYEE", 3L);
                employeeRepository.save(emp1);
                publishEvent(rabbitTemplate, emp1);

                Employee emp2 = new Employee(2L, "employee2", "Employee Two", "employee2@company.com", "EMPLOYEE", 3L);
                employeeRepository.save(emp2);
                publishEvent(rabbitTemplate, emp2);

                log.info("Mock employees seeded and events published.");
            }
        };
    }

    private void publishEvent(RabbitTemplate rabbitTemplate, Employee employee) {
        try {
            EmployeeCreatedEvent event = new EmployeeCreatedEvent(
                    employee.getId(),
                    employee.getUsername(),
                    employee.getFullName(),
                    employee.getEmail(),
                    employee.getManagerId()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);
        } catch (Exception e) {
            log.error("Failed to publish employee.created event for username={}", employee.getUsername(), e);
        }
    }
}
