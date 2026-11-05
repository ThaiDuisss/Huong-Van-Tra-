package org.hvtra.catalog.importing.commercial;

import jakarta.validation.Validator;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.outbox.publisher.CatalogOutboxPublisher;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.uuid;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.CategoryInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.IdentityDecision;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.Kind;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.ProductInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.Row;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.VariantInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportStore.Snapshot;

/** Application-only entry point. Deliberately has no HTTP controller or startup runner. */
@Service
public class CommercialImportAdapter {
    public static final String CONTRACT_SHA = "00267effe77f5e4bee1842d8567ccd13d6d8c4751518727b6126c8050919deb3";
    public static final String SOURCE_SNAPSHOT_SHA = "53a843b7241629618774c36dc2e19f153687ab498abd433228090e9cc737cb07";
    private final CommercialImportStore store;
    private final CommercialImportCodec codec;
    private final Validator validator;
    private final CatalogOutboxPublisher outbox;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;
    private final boolean executionEnabled;
    private final boolean compatibleTransactionManager;

    public CommercialImportAdapter(CommercialImportStore store, CommercialImportCodec codec, Validator validator,
                                   CatalogOutboxPublisher outbox, PlatformTransactionManager manager,
                                   @Value("${catalog.commercial-import.execute-enabled:false}") boolean executionEnabled) {
        this.store = store;
        this.codec = codec;
        this.validator = validator;
        this.outbox = outbox;
        this.executionEnabled = executionEnabled;
        this.compatibleTransactionManager = manager instanceof JpaTransactionManager jpa && store.usesDataSource(jpa.getDataSource());
        reads = new TransactionTemplate(manager);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setReadOnly(true);
        reads.setTimeout(120);
        writes = new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        writes.setTimeout(120);
    }

    public record Issue(String entity, UUID sourceId, String field, String code) { }
    public record ValidationReport(String mode, String planHash, String actionsHash,
                                   String targetFingerprint, String schemaFingerprint,
                                   boolean validForApproval, boolean executionLocked,
                                   List<Issue> issues, List<Map<String, Object>> actions) { }
    public record ApprovalEvidence(String confirmedPlanHash, String confirmedActionsHash,
                                   String confirmedTargetFingerprint, String confirmedSchemaFingerprint,
                                   String commercialDecisionReference, String backupRecoveryReference,
                                   String downstreamVerificationReference,
                                   String sourceRevalidationReference, Instant sourceRevalidatedAt,
                                   Instant expiresAt) { }
    public record ExecutionResult(String state, String planHash, int created, int skipped, String afterFingerprint) { }
    public record RecoveryReport(String state, String planHash, List<Issue> issues) { }
    private record Preflight(ValidationReport report, Snapshot snapshot) { }

    public ValidationReport validateJson(String json) { return validate(codec.parse(json)); }
    public ValidationReport dryRunJson(String json) { return dryRun(codec.parse(json)); }
    public ValidationReport validate(CommercialImportPlan plan) {
        return reads.execute(tx -> preflight(plan, "VALIDATE").report());
    }
    public ValidationReport dryRun(CommercialImportPlan plan) {
        return reads.execute(tx -> preflight(plan, "DRY_RUN").report());
    }

    /** In-memory application of explicitly approved group decisions; never grants execution approval. */
    public CommercialImportPlan applyCommercialDecisions(CommercialImportPlan plan, CommercialImportDecisions decisions) {
        actorWithPermission("catalog:commercial-import:approve");
        return decisions.apply(plan);
    }

    public CommercialImportPlan applyCommercialDecisionsJson(String planJson, String decisionsJson) {
        actorWithPermission("catalog:commercial-import:approve");
        return codec.parseDecisions(decisionsJson).apply(codec.parse(planJson));
    }

    /** Future authorized operator flow. Neither approval creation nor execute is available by default. */
    public UUID recordExecutionApproval(CommercialImportPlan plan, ApprovalEvidence evidence) {
        requireExecutionEnabled();
        UUID actor = actorWithPermission("catalog:commercial-import:approve");
        if (evidence == null || evidence.expiresAt() == null || !evidence.expiresAt().isAfter(Instant.now())
                || evidence.expiresAt().isAfter(Instant.now().plus(Duration.ofHours(24)))
                || !reference(evidence.commercialDecisionReference()) || !reference(evidence.backupRecoveryReference())
                || !reference(evidence.downstreamVerificationReference()) || !reference(evidence.sourceRevalidationReference())
                || !freshSourceEvidence(evidence.sourceRevalidatedAt())) throw new IllegalArgumentException("COMPLETE_REVIEWED_APPROVAL_EVIDENCE_REQUIRED");
        return writes.execute(tx -> {
            Preflight p = preflight(plan, "APPROVAL_PREFLIGHT");
            requireValid(p.report());
            if (!Objects.equals(p.report().planHash(), evidence.confirmedPlanHash())
                    || !Objects.equals(p.report().actionsHash(), evidence.confirmedActionsHash())
                    || !Objects.equals(p.report().targetFingerprint(), evidence.confirmedTargetFingerprint())
                    || !Objects.equals(p.report().schemaFingerprint(), evidence.confirmedSchemaFingerprint())) throw new IllegalStateException("REVIEWED_PREFLIGHT_HASHES_DO_NOT_MATCH");
            UUID id = UUID.randomUUID(); // Approval identity only; never a source entity identity.
            store.saveApproval(id, p.report().planHash(), p.report().actionsHash(), p.report().targetFingerprint(),
                    p.report().schemaFingerprint(), actor, evidence, Instant.now());
            return id;
        });
    }

    public ExecutionResult executeJson(String json, UUID approvalId) {
        requireExecutionEnabled();
        return execute(codec.parse(json), approvalId);
    }

    public ExecutionResult execute(CommercialImportPlan plan, UUID approvalId) {
        requireExecutionEnabled(); // Before any DB transaction, lookup or publisher call.
        UUID actor = actorWithPermission("catalog:commercial-import:execute");
        if (approvalId == null) throw new IllegalArgumentException("EXPLICIT_EXECUTION_APPROVAL_REQUIRED");
        String planHash = codec.hash(plan);
        try {
            return writes.execute(tx -> executeInTransaction(plan, approvalId, actor, planHash));
        } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
            // The transaction has exited/rolled back. Do not turn an uncertain outcome into success.
            throw new IllegalStateException("IMPORT_CONFLICT_RECONCILE_BEFORE_RETRY:" + planHash);
        }
    }

    public RecoveryReport reconcile(CommercialImportPlan plan) {
        actorWithPermission("catalog:commercial-import:read");
        return reads.execute(tx -> {
            String hash = codec.hash(plan);
            Snapshot state = store.snapshot();
            if (!state.infrastructureReady()) return new RecoveryReport("INFRASTRUCTURE_NOT_READY", hash, List.of(new Issue("PLAN", null, "migration", "INFRASTRUCTURE_NOT_READY")));
            boolean committed = store.run(hash).isPresent();
            List<Issue> issues = ownedRowsIssues(plan, state);
            if (committed && issues.isEmpty()) return new RecoveryReport("COMMITTED_AND_IDENTICAL", hash, List.of());
            boolean anyEntry = plan.rows().stream().anyMatch(r -> store.entry(plan.sourceSystem(), r).isPresent());
            return new RecoveryReport(committed || anyEntry ? "RECOVERY_REVIEW_REQUIRED_NO_AUTOMATIC_WRITES" : "NOT_COMMITTED", hash, List.copyOf(issues));
        });
    }

    private ExecutionResult executeInTransaction(CommercialImportPlan plan, UUID approvalId, UUID actor, String planHash) {
        Map<String, Object> approval = store.lockApproval(approvalId);
        String actionsHash = codec.hash(codec.actions(plan));
        if (!planHash.equals(approval.get("plan_hash")) || !actionsHash.equals(approval.get("actions_hash"))) throw new IllegalStateException("APPROVAL_PAYLOAD_OR_ACTION_LIST_MISMATCH");
        if (!flag(approval.get("approval_unexpired"))) throw new IllegalStateException("EXECUTION_APPROVAL_EXPIRED");
        if (!reference((String) approval.get("commercial_decision_ref")) || !reference((String) approval.get("backup_recovery_ref"))
                || !reference((String) approval.get("downstream_verification_ref")) || !reference((String) approval.get("source_revalidation_ref"))
                || !flag(approval.get("source_preflight_fresh"))) throw new IllegalStateException("APPROVAL_EVIDENCE_INCOMPLETE_OR_SOURCE_PREFLIGHT_STALE");
        Snapshot state = store.snapshot();
        OptionalRun existing = existingRun(planHash, approvalId);
        if (existing.present()) {
            if (!state.blockers().isEmpty() || !ownedRowsIssues(plan, state).isEmpty()) throw new IllegalStateException("COMMITTED_IMPORT_CHANGED_RECOVERY_REVIEW_REQUIRED");
            return new ExecutionResult("ALREADY_COMMITTED", planHash, 0, plan.rows().size(), state.targetFingerprint());
        }
        Preflight p = preflight(plan, "EXECUTE_PREFLIGHT", state);
        requireValid(p.report());
        if (!state.targetFingerprint().equals(approval.get("target_fingerprint")) || !state.schemaFingerprint().equals(approval.get("schema_fingerprint"))) throw new IllegalStateException("LIVE_DRIFT_SINCE_APPROVAL");
        List<Row> ordered = orderedRows(plan);
        int created = 0;
        int skipped = 0;
        Instant now = Instant.now();
        for (Row row : ordered) {
            if (store.entry(plan.sourceSystem(), row).isPresent()) { skipped++; continue; }
            store.insert(row, actor, now);
            String eventId = null;
            if (row instanceof VariantInput variant) {
                ProductVariant eventVariant = new ProductVariant();
                eventVariant.setId(variant.id()); eventVariant.setSku(variant.sku()); eventVariant.setVariantName(variant.variantName());
                eventVariant.setStatus(variant.status());
                Unit unit = new Unit(); unit.setId(variant.unitId()); eventVariant.setUnit(unit);
                // Existing entity technical productionThreshold=0 / ITEM_SYNC contract is preserved.
                eventId = outbox.publishImportedVariantCreated(eventVariant, plan.sourceSystem() + ":VARIANT:" + variant.id() + ":" + codec.rowHash(row));
            }
            store.saveEntry(plan.sourceSystem(), row, codec.rowHash(row), planHash, approvalId, actor, eventId, now);
            created++;
        }
        Snapshot after = store.snapshot();
        if (!ownedRowsIssues(plan, after, false).isEmpty()) throw new IllegalStateException("INSERT_READBACK_OR_OUTBOX_MISMATCH");
        store.saveRun(planHash, plan.sourceSystem(), actionsHash, approvalId, actor, state.targetFingerprint(), after.targetFingerprint(), ordered.size(), now);
        return new ExecutionResult("COMMITTED", planHash, created, skipped, after.targetFingerprint());
    }

    private record OptionalRun(boolean present) { }
    private OptionalRun existingRun(String hash, UUID approvalId) {
        var run = store.run(hash);
        if (run.isPresent() && !approvalId.equals(uuid((byte[]) run.get().get("approval_id")))) throw new IllegalStateException("RUN_APPROVAL_ID_MISMATCH");
        return new OptionalRun(run.isPresent());
    }

    private Preflight preflight(CommercialImportPlan plan, String mode) { return preflight(plan, mode, store.snapshot()); }

    private Preflight preflight(CommercialImportPlan plan, String mode, Snapshot state) {
        if (plan == null) throw new IllegalArgumentException("IMPORT_PLAN_REQUIRED");
        List<Issue> issues = new ArrayList<>();
        if (!"RECOMMENDATION".equals(plan.sourceSystem()) || !"1.2.1".equals(plan.contractVersion())
                || !CONTRACT_SHA.equals(plan.contractSha256()) || !SOURCE_SNAPSHOT_SHA.equals(plan.sourceSnapshotSha256())) issues.add(new Issue("PLAN", null, "provenance", "PINNED_SOURCE_CONTRACT_REQUIRED"));
        if (plan.rows().isEmpty() || plan.rows().size() > 1000) throw new IllegalArgumentException("IMPORT_PLAN_SIZE_MUST_BE_1_TO_1000");
        if (!compatibleTransactionManager) issues.add(new Issue("PLAN", null, "transactionManager", "JPA_AND_JDBC_DATASOURCE_MUST_MATCH"));
        state.blockers().forEach(b -> issues.add(new Issue("PLAN", null, "schema", b)));
        Map<Kind, Map<UUID, Row>> source = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) source.put(kind, new LinkedHashMap<>());
        for (Row row : plan.rows()) {
            validator.validate(row).forEach(v -> issue(issues, row, v.getPropertyPath().toString(), "INPUT_" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
            validator.validate(row.businessRequest()).forEach(v -> issue(issues, row, v.getPropertyPath().toString(), "BUSINESS_" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
            if (row.id() == null) continue;
            if (source.get(row.kind()).putIfAbsent(row.id(), row) != null) issue(issues, row, "id", "DUPLICATE_SOURCE_ID");
            if (row.status() != Status.ACTIVE) issue(issues, row, "status", "ONLY_ACTIVE_SOURCE_CANDIDATES_ALLOWED");
            if (!reference(row.approvalReference())) issue(issues, row, "approvalReference", "MAPPING_OR_COMMERCIAL_APPROVAL_PENDING");
            if (row.decision() == null || row.decision() == IdentityDecision.PENDING_REVIEW) issue(issues, row, "decision", "IDENTITY_REVIEW_PENDING");
        }
        for (Row row : plan.rows()) {
            if (row.id() == null) continue;
            if (row instanceof CategoryInput c && c.parentId() != null) dependency(issues, row, Kind.CATEGORY, c.parentId(), source, state);
            if (row instanceof ProductInput p) dependency(issues, row, Kind.CATEGORY, p.categoryId(), source, state);
            if (row instanceof VariantInput v) {
                if (v.barcode() != null && v.barcode().isBlank()) issue(issues, row, "barcode", "BARCODE_MUST_BE_NULL_OR_NONBLANK");
                dependency(issues, row, Kind.PRODUCT, v.productId(), source, state);
                if (!CommercialImportStore.active(state.units().get(v.unitId()))) issue(issues, row, "unitId", "ACTIVE_NONDELETED_UNIT_REQUIRED");
                if (v.barcode() != null) for (UUID target : store.textMatches(Kind.VARIANT, "barcode", v.barcode())) if (!target.equals(row.id())) issue(issues, row, "barcode", "TARGET_BARCODE_COLLISION");
            }
            for (UUID target : store.textMatches(row.kind(), row.kind().codeColumn, row.code())) if (!target.equals(row.id())) issue(issues, row, "code", "TARGET_CODE_OR_SKU_COLLISION");
            for (UUID target : store.textMatches(row.kind(), row.kind().nameColumn, row.name())) {
                if (!target.equals(row.id()) && row.decision() != IdentityDecision.CREATE_SEPARATE) issue(issues, row, "name", "NAME_ONLY_REVIEW_REQUIRED_NO_AUTO_MERGE");
            }
            if (state.infrastructureReady()) {
                var entry = store.entry(plan.sourceSystem(), row);
                if (entry.isPresent()) {
                    if (!entryMatches(plan, row, state, entry.get(), true)) issue(issues, row, "identity", "OWNED_TARGET_CHANGED_OR_PARTIAL_LEDGER");
                } else if (state.rows().get(row.kind()).containsKey(row.id())) issue(issues, row, "id", "UNOWNED_TARGET_IDENTITY_EXISTS");
            } else if (state.rows().get(row.kind()).containsKey(row.id())) issue(issues, row, "id", "UNOWNED_TARGET_IDENTITY_EXISTS");
        }
        for (Kind kind : Kind.values()) {
            List<Row> rows = plan.rows().stream().filter(r -> r.kind() == kind).toList();
            for (Map<String, Object> duplicate : store.sourceCodeCollisions(kind, rows, state)) issues.add(new Issue(kind.name(), UUID.fromString(duplicate.get("left_id").toString()), "code", "SOURCE_CODE_COLLATION_COLLISION"));
        }
        for (Map<String, Object> duplicate : store.sourceBarcodeCollisions(plan.rows().stream().filter(r -> r.kind() == Kind.VARIANT).toList(), state)) {
            issues.add(new Issue("VARIANT", UUID.fromString(duplicate.get("left_id").toString()), "barcode", "SOURCE_BARCODE_COLLATION_COLLISION"));
        }
        for (CategoryInput category : plan.categories()) {
            Set<UUID> seen = new HashSet<>(); UUID current = category.id();
            while (current != null) {
                if (!seen.add(current)) { issue(issues, category, "parentId", "CATEGORY_CYCLE"); break; }
                Row planned = source.get(Kind.CATEGORY).get(current);
                if (planned instanceof CategoryInput c) current = c.parentId();
                else {
                    Map<String, Object> existing = state.rows().get(Kind.CATEGORY).get(current);
                    current = existing == null || existing.get("parent_id") == null ? null : uuid((byte[]) existing.get("parent_id"));
                }
            }
        }
        ValidationReport report = new ValidationReport(mode, codec.hash(plan), codec.hash(codec.actions(plan)), state.targetFingerprint(), state.schemaFingerprint(),
                issues.isEmpty(), !executionEnabled, List.copyOf(new LinkedHashSet<>(issues)), codec.actions(plan));
        return new Preflight(report, state);
    }

    private void dependency(List<Issue> issues, Row child, Kind parentKind, UUID parentId, Map<Kind, Map<UUID, Row>> planned, Snapshot state) {
        Row parent = planned.get(parentKind).get(parentId);
        if (parent != null) {
            if (parent.status() != Status.ACTIVE || parent.decision() == null || parent.decision() == IdentityDecision.PENDING_REVIEW
                    || !reference(parent.approvalReference())) issue(issues, child, "parent", "PARENT_MAPPING_APPROVAL_PENDING");
        } else if (parentId == null || !CommercialImportStore.active(state.rows().get(parentKind).get(parentId))) issue(issues, child, "parent", "ACTIVE_NONDELETED_PARENT_REQUIRED");
    }

    private List<Issue> ownedRowsIssues(CommercialImportPlan plan, Snapshot state) { return ownedRowsIssues(plan, state, true); }
    private List<Issue> ownedRowsIssues(CommercialImportPlan plan, Snapshot state, boolean requireRun) {
        List<Issue> issues = new ArrayList<>();
        for (Row row : plan.rows()) {
            var entry = store.entry(plan.sourceSystem(), row);
            if (entry.isEmpty() || !entryMatches(plan, row, state, entry.get(), requireRun)) issue(issues, row, "recovery", "LEDGER_TARGET_OR_OUTBOX_MISMATCH");
        }
        return issues;
    }

    private boolean entryMatches(CommercialImportPlan plan, Row row, Snapshot state, Map<String, Object> entry, boolean requireRun) {
        if (!codec.rowHash(row).equals(entry.get("payload_hash")) || !row.id().equals(uuid((byte[]) entry.get("target_id"))) || !store.matches(row, state)) return false;
        if (requireRun && store.run((String) entry.get("plan_hash")).isEmpty()) return false;
        return row.kind() != Kind.VARIANT || store.outboxExists((String) entry.get("outbox_event_id"), row.id());
    }

    private List<Row> orderedRows(CommercialImportPlan plan) {
        List<Row> result = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        while (added.size() < plan.categories().size()) {
            int before = added.size();
            for (CategoryInput row : plan.categories()) {
                if (!added.contains(row.id()) && (row.parentId() == null || added.contains(row.parentId())
                        || plan.categories().stream().noneMatch(p -> p.id().equals(row.parentId())))) { result.add(row); added.add(row.id()); }
            }
            if (added.size() == before) throw new IllegalStateException("CATEGORY_ORDER_UNRESOLVED");
        }
        result.addAll(plan.products()); result.addAll(plan.variants());
        return result;
    }

    private static void issue(List<Issue> issues, Row row, String field, String code) { issues.add(new Issue(row.kind().name(), row.id(), field, code)); }
    private static boolean reference(String ref) { return ref != null && !ref.isBlank() && ref.length() <= 500; }
    private static boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() == 1; }
    private static boolean freshSourceEvidence(Instant at) {
        Instant now = Instant.now();
        return at != null && !at.isAfter(now) && at.isAfter(now.minus(Duration.ofMinutes(15)));
    }
    private static void requireValid(ValidationReport report) {
        if (!report.validForApproval()) throw new IllegalStateException("IMPORT_VALIDATION_BLOCKED:" + report.issues().size());
    }
    private void requireExecutionEnabled() {
        if (!executionEnabled) throw new AccessDeniedException("COMMERCIAL_IMPORT_EXECUTE_DISABLED");
        if (!compatibleTransactionManager) throw new IllegalStateException("JPA_AND_JDBC_DATASOURCE_MUST_MATCH");
    }
    private static UUID actorWithPermission(String permission) {
        if (!SecurityUtils.isSuperAdmin() || !SecurityUtils.hasPermission(permission)) throw new AccessDeniedException("EXPLICIT_COMMERCIAL_IMPORT_PERMISSION_REQUIRED");
        try { return UUID.fromString(SecurityUtils.currentUserId()); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("AUTHENTICATED_UUID_ACTOR_REQUIRED"); }
    }
}
