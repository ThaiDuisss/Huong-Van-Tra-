package org.hvtra.catalog.importing.commercial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operator-only requests through the existing authenticated Gateway Catalog route.
 * This path is outside all public matchers. Direct unsigned calls are rejected by
 * InternalHeaderAuthenticationFilter; every facade call also enforces role/permission.
 * No method grants roles, changes execute-enabled, or executes on application startup.
 */
@RestController
@RequestMapping("/api/catalog/internal/commercial-import")
public class CommercialImportRunnerController {
    private final CommercialImportRunner runner;

    public CommercialImportRunnerController(CommercialImportRunner runner) {
        this.runner = runner;
    }

    public record PlanCommand(@NotBlank @Size(max = 5_000_000) String planJson) { }
    public record ApprovalCommand(@NotBlank @Size(max = 5_000_000) String planJson,
                                  @NotNull @Valid CommercialImportAdapter.ApprovalEvidence evidence) { }
    public record ExecuteCommand(@NotBlank @Size(max = 5_000_000) String planJson,
                                 @NotNull UUID approvalId) { }

    @GetMapping("/caller")
    public ApiResponse<CommercialImportRunner.CallerStatus> caller() {
        return ApiResponse.success(runner.caller());
    }

    @PostMapping("/validate")
    public ApiResponse<CommercialImportAdapter.ValidationReport> validate(@Valid @RequestBody PlanCommand command) {
        return ApiResponse.success(runner.validate(command.planJson()));
    }

    @PostMapping("/dry-run")
    public ApiResponse<CommercialImportAdapter.ValidationReport> dryRun(@Valid @RequestBody PlanCommand command) {
        return ApiResponse.success(runner.dryRun(command.planJson()));
    }

    @PostMapping("/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommercialImportRunner.ApprovalResult> approve(@Valid @RequestBody ApprovalCommand command) {
        return ApiResponse.created("Đã ghi nhận phê duyệt thực thi import", runner.approve(command.planJson(), command.evidence()));
    }

    @PostMapping("/execute")
    public ApiResponse<CommercialImportAdapter.ExecutionResult> execute(@Valid @RequestBody ExecuteCommand command) {
        return ApiResponse.success(runner.execute(command.planJson(), command.approvalId()));
    }

    @PostMapping("/reconcile")
    public ApiResponse<CommercialImportAdapter.RecoveryReport> reconcile(@Valid @RequestBody PlanCommand command) {
        return ApiResponse.success(runner.reconcile(command.planJson()));
    }
}
