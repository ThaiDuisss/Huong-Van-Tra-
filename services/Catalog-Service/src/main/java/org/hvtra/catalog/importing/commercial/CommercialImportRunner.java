package org.hvtra.catalog.importing.commercial;

import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Manually invoked application facade. Never runs at startup or creates an Authentication. */
@Service
public class CommercialImportRunner {
    public static final String READ_PERMISSION = "catalog:commercial-import:read";
    public static final String APPROVE_PERMISSION = "catalog:commercial-import:approve";
    public static final String EXECUTE_PERMISSION = "catalog:commercial-import:execute";

    private final CommercialImportAdapter adapter;
    private final CommercialImportCodec codec;
    private final boolean executionEnabled;

    public CommercialImportRunner(CommercialImportAdapter adapter, CommercialImportCodec codec,
                                  @Value("${catalog.commercial-import.execute-enabled:false}") boolean executionEnabled) {
        this.adapter = adapter;
        this.codec = codec;
        this.executionEnabled = executionEnabled;
    }

    public record CallerStatus(UUID actorId, boolean canRead, boolean canApprove, boolean canExecute,
                               boolean executionEnabled, String invocation) { }
    public record ApprovalResult(UUID approvalId) { }

    /** Authentication is established upstream by the existing signed-header filter. */
    public CallerStatus caller() {
        UUID actor = requireCaller(READ_PERMISSION);
        return new CallerStatus(actor, true, SecurityUtils.hasPermission(APPROVE_PERMISSION),
                SecurityUtils.hasPermission(EXECUTE_PERMISSION), executionEnabled, "MANUAL_AUTHENTICATED_REQUEST_ONLY");
    }

    public CommercialImportAdapter.ValidationReport validate(String planJson) {
        requireCaller(READ_PERMISSION);
        return adapter.validateJson(planJson);
    }

    public CommercialImportAdapter.ValidationReport dryRun(String planJson) {
        requireCaller(READ_PERMISSION);
        return adapter.dryRunJson(planJson);
    }

    public ApprovalResult approve(String planJson, CommercialImportAdapter.ApprovalEvidence evidence) {
        requireCaller(APPROVE_PERMISSION);
        // The adapter still checks execution lock, fresh preflight, exact hashes,
        // evidence and the genuine caller before persisting its existing approval.
        return new ApprovalResult(adapter.recordExecutionApproval(codec.parse(planJson), evidence));
    }

    public CommercialImportAdapter.ExecutionResult execute(String planJson, UUID approvalId) {
        requireCaller(EXECUTE_PERMISSION);
        return adapter.executeJson(planJson, approvalId);
    }

    public CommercialImportAdapter.RecoveryReport reconcile(String planJson) {
        requireCaller(READ_PERMISSION);
        return adapter.reconcile(codec.parse(planJson));
    }

    private static UUID requireCaller(String permission) {
        // No role fallback, impersonation, body actor, token generation or context injection.
        if (!SecurityUtils.isSuperAdmin() || !SecurityUtils.hasPermission(permission)) {
            throw new AccessDeniedException("EXPLICIT_COMMERCIAL_IMPORT_PERMISSION_REQUIRED");
        }
        String subject = SecurityUtils.currentUserId();
        try {
            UUID actor = UUID.fromString(subject);
            if (!actor.toString().equalsIgnoreCase(subject)) throw new IllegalArgumentException();
            return actor;
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("AUTHENTICATED_UUID_ACTOR_REQUIRED");
        }
    }
}
