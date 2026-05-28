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
            // Initialize default balances for seeded users (ID 1, 2, 3) if they don't already exist
            for (long id = 1; id <= 3; id++) {
                seedBalanceIfAbsent(leaveBalanceRepository, id, "CASUAL", 12);
                seedBalanceIfAbsent(leaveBalanceRepository, id, "SICK", 10);
                seedBalanceIfAbsent(leaveBalanceRepository, id, "PRIVILEGE", 15);
            }
        };
    }

    private void seedBalanceIfAbsent(LeaveBalanceRepository repository, long employeeId, String leaveType, int allocated) {
        boolean exists = repository
                .findByEmployeeIdAndLeaveTypeIgnoreCase(employeeId, leaveType)
                .isPresent();
        if (!exists) {
            repository.save(new LeaveBalance(null, employeeId, leaveType, allocated, 0));
            System.out.println("Seeded " + leaveType + " balance for Employee ID: " + employeeId);
        }
    }
}
