package com.niloy.leave.repository;

import com.niloy.leave.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    
    @Query("SELECT r FROM LeaveRequest r WHERE r.employeeId = :employeeId " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<LeaveRequest> findByEmployeeIdAndStatus(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT r FROM LeaveRequest r WHERE r.managerId = :managerId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:employeeId IS NULL OR r.employeeId = :employeeId) " +
           "AND (:startDate IS NULL OR r.startDate >= :startDate) " +
           "AND (:endDate IS NULL OR r.endDate <= :endDate)")
    List<LeaveRequest> findPendingRequestsForManager(
            @Param("managerId") Long managerId,
            @Param("status") String status,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(r) > 0 FROM LeaveRequest r WHERE r.employeeId = :employeeId " +
           "AND r.status IN ('PENDING', 'APPROVED') " +
           "AND ((r.startDate <= :endDate AND r.endDate >= :startDate))")
    boolean existsOverlappingRequest(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
