package com.vault.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(UUID eventId, String entityType, UUID entityId, String action, UUID actorId,
                       String payload, Instant timestamp) {}
