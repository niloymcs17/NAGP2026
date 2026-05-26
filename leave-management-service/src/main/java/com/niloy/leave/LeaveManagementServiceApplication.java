package com.niloy.leave;

import com.niloy.leave.model.LeaveBalance;
import com.niloy.leave.repository.LeaveBalanceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
public class LeaveManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveManagementServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDatabase(LeaveBalanceRepository leaveBalanceRepository) {
        return args -> {
            if (leaveBalanceRepository.count() == 0) {
                // Initialize default balances for seeded users (ID 1, 2, 3)
                for (long id = 1; id <= 3; id++) {
                    leaveBalanceRepository.save(new LeaveBalance(null, id, "CASUAL", 12, 0));
                    leaveBalanceRepository.save(new LeaveBalance(null, id, "SICK", 10, 0));
                    leaveBalanceRepository.save(new LeaveBalance(null, id, "PRIVILEGE", 15, 0));
                }
                System.out.println("Default leave balances seeded for pre-existing employees.");
            }
        };
    }
}
