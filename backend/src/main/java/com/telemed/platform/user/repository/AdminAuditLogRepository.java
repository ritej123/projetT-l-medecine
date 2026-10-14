package com.telemed.platform.user.repository;

import java.util.UUID;

import com.telemed.platform.user.domain.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
}
