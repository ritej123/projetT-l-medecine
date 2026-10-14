package com.telemed.platform.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    long countByEnabledFalse();

    long countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Role role, java.time.Instant from, java.time.Instant to);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.Instant from, java.time.Instant to);

    @Query("""
            select new com.telemed.platform.user.api.AdminAuditLogResponse(
                log.id, actor.email, log.action, log.targetType, log.targetId, log.result, log.createdAt)
            from AdminAuditLog log join log.actor actor
            order by log.createdAt desc
            """)
    org.springframework.data.domain.Page<com.telemed.platform.user.api.AdminAuditLogResponse> findAdminAuditLogs(
            org.springframework.data.domain.Pageable pageable);
}
