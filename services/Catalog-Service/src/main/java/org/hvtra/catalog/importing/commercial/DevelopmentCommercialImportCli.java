package org.hvtra.catalog.importing.commercial;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.security.auth.module.NTSystem;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validator;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.outbox.config.CatalogOutboxConfig;
import org.hvtra.catalog.outbox.factory.CatalogOutboxEventFactory;
import org.hvtra.catalog.outbox.publisher.CatalogOutboxPublisher;
import org.hvtra.catalog.outbox.repository.CatalogOutboxEventRepository;
import org.hvtra.catalog.outbox.resolver.CatalogOutboxTopicResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.bytes;
import static org.hvtra.catalog.importing.commercial.CommercialImportCodec.normalize;

/**
 * Explicit Windows CLI main only. No component/startup runner, web server, application scan,
 * Flyway initialization, storage initialization, Kafka producer or SecurityContext is created.
 * Local identity is a real Windows SID, audited as LOCAL_WINDOWS_OPERATOR, never a Keycloak user.
 */
public final class DevelopmentCommercialImportCli {
    private static final String PLAN_HASH = "ce16bd52ba6451def9ac6fe61a7a34b9485e7c9975ae63085e4abfbdf7bc9dfe";
    private static final String ACTIONS_HASH = "9898567247372d969f269a35ecab3fd00ae6c05e92ed857b5408ddb28cb385d2";
    private static final UUID BOX_ID = UUID.fromString("b939264e-e945-4a63-8a1c-6349917d753e");
    private static final Path REPORT_ROOT = Path.of("E:/Huong-Van-Tra-Backup/codex-reports");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private DevelopmentCommercialImportCli() { }

    public record Authorization(String environment, String operatorSid, Instant expiresAt,
                                String expectedHost, int expectedPort, String expectedDatabase,
                                String expectedServerUuid, String expectedDatabaseUserHash,
                                String planPath, String planFileSha256, String unitPath, String unitFileSha256,
                                String sourceEvidencePath, String sourceEvidenceSha256,
                                String expectedTargetFingerprint, String expectedSchemaFingerprint,
                                String backupManifestPath, String backupManifestSha256, String reportDirectory) { }

    public static void main(String[] args) {
        if (args.length != 2 || !("--inspect".equals(args[0]) || "--execute-once".equals(args[0]))) {
            System.err.println("DISABLED: explicit --inspect|--execute-once and protected authorization file required");
            System.exit(2);
        }
        try {
            run(Path.of(args[1]), "--execute-once".equals(args[0]));
        } catch (Exception exception) {
            // Never echo JDBC URLs, credentials, arbitrary exception messages or source excerpts.
            String code = exception.getMessage();
            System.err.println("IMPORT_STOPPED:" + exception.getClass().getSimpleName() + ":"
                    + (code != null && code.matches("[A-Z_0-9:]+") ? code : "SEE_LOCAL_REVIEW_EVIDENCE"));
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) System.err.println("SQL_STATE:" + sql.getSQLState() + ":CODE:" + sql.getErrorCode());
            }
            System.exit(2);
        }
    }

    private static void run(Path authorizationPath, boolean execute) throws Exception {
        Authorization authorization = JSON.readValue(authorizationPath.toFile(), Authorization.class);
        NTSystem windows = new NTSystem();
        require(windows.getUserSID() != null && windows.getUserSID().equals(authorization.operatorSid()), "LOCAL_OPERATOR_SID_MISMATCH");
        verifyAuthorizationAcl(authorizationPath, windows);
        require(Set.of("development", "demo").contains(authorization.environment()), "DEVELOPMENT_DEMO_ONLY");
        require(authorization.expiresAt() != null && authorization.expiresAt().isAfter(Instant.now())
                && authorization.expiresAt().isBefore(Instant.now().plus(Duration.ofHours(2))), "LOCAL_AUTHORIZATION_EXPIRED_OR_TOO_LONG");
        require("catalog_db".equals(authorization.expectedDatabase()), "CATALOG_DATABASE_REQUIRED");
        Path report = Path.of(authorization.reportDirectory()).toRealPath();
        require(report.startsWith(REPORT_ROOT.toRealPath()), "EXTERNAL_REPORT_DIRECTORY_REQUIRED");
        verifyFile(authorization.planPath(), authorization.planFileSha256());
        verifyFile(authorization.unitPath(), authorization.unitFileSha256());
        verifySource(authorization);
        CommercialImportCodec codec = new CommercialImportCodec();
        CommercialImportPlan plan = codec.parse(Files.readString(Path.of(authorization.planPath())));
        require(PLAN_HASH.equals(codec.hash(plan)) && ACTIONS_HASH.equals(codec.hash(codec.actions(plan))), "APPROVED_CANONICAL_HASH_MISMATCH");
        require(plan.categories().size() == 7 && plan.products().size() == 64 && plan.variants().size() == 124, "FIXED_IMPORT_SCOPE_REQUIRED");
        UnitRequest unit = JSON.readValue(Path.of(authorization.unitPath()).toFile(), UnitRequest.class);
        require("Hộp".equals(unit.name()) && "hộp".equals(unit.symbol()) && unit.type() == UnitType.COUNT
                && unit.baseUnitId() == null && BigDecimal.ONE.compareTo(unit.conversionRate()) == 0
                && Integer.valueOf(0).equals(unit.precisionScale()) && unit.status() == Status.ACTIVE, "APPROVED_BOX_SEMANTICS_REQUIRED");
        UUID actor = UUID.nameUUIDFromBytes(("LOCAL_WINDOWS_OPERATOR:" + windows.getUserSID()).getBytes(StandardCharsets.UTF_8));
        String url = requiredEnv("SPRING_DATASOURCE_URL");
        URI target = URI.create(url.replaceFirst("^jdbc:", ""));
        require("mysql".equals(target.getScheme()) && authorization.expectedHost().equals(target.getHost())
                && authorization.expectedPort() == (target.getPort() < 0 ? 3306 : target.getPort())
                && ("/" + authorization.expectedDatabase()).equals(target.getPath()), "DATASOURCE_TARGET_MISMATCH");
        DataSource source = new DriverManagerDataSource(url, requiredEnv("SPRING_DATASOURCE_USERNAME"), requiredEnv("SPRING_DATASOURCE_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Map<String, Object> identity = jdbc.queryForMap("SELECT DATABASE() AS db_name,@@server_uuid AS server_uuid,SHA2(CURRENT_USER(),256) AS db_user_hash");
        require(authorization.expectedDatabase().equals(identity.get("db_name"))
                && authorization.expectedServerUuid().equals(identity.get("server_uuid"))
                && authorization.expectedDatabaseUserHash().equals(identity.get("db_user_hash")), "LIVE_DATABASE_IDENTITY_MISMATCH");
        // Only this deliberately selected, isolated context is registered. No Boot auto-configuration.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSource.class, () -> source);
            context.register(Wiring.class);
            context.refresh();
            Validator validator = context.getBean(Validator.class);
            require(validator.validate(unit).isEmpty(), "UNIT_DTO_INVALID");
            CommercialImportStore store = new CommercialImportStore(jdbc, codec);
            JpaTransactionManager manager = context.getBean(JpaTransactionManager.class);
            CommercialImportAdapter adapter = new CommercialImportAdapter(store, codec, validator,
                    context.getBean(CatalogOutboxPublisher.class), manager, execute);
            adapter.allowLocalOperatorNetworkLatency();
            var preflight = adapter.dryRun(plan);
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("operatorType", "LOCAL_WINDOWS_OPERATOR"); audit.put("operatorSid", windows.getUserSID());
            audit.put("operatorName", windows.getDomain() + "\\" + windows.getName()); audit.put("operatorAuditId", actor);
            audit.put("databaseIdentity", identity); audit.put("environmentAttestation", authorization.environment());
            audit.put("planHash", PLAN_HASH); audit.put("actionsHash", ACTIONS_HASH); audit.put("preflight", preflight);
            writeNew(report.resolve(execute ? "execute-preflight.json" : "cli-inspection.json"), audit);
            requireExpectedIssues(preflight, plan, store.snapshot());
            if (!execute) { System.out.println("READ_ONLY_INSPECTION_COMPLETE"); return; }
            require(preflight.targetFingerprint().equals(authorization.expectedTargetFingerprint())
                    && preflight.schemaFingerprint().equals(authorization.expectedSchemaFingerprint()), "DRIFT_SINCE_REVIEWED_PREFLIGHT");
            verifyBackup(authorization, preflight);
            verifySource(authorization); // Source evidence must still be fresh immediately before writes.
            writeNew(report.resolve("execution-attempt.json"), Map.of("state", "STARTED_NO_AUTOMATIC_RETRY", "actor", actor,
                    "planHash", PLAN_HASH, "startedAt", Instant.now()));
            TransactionTemplate transaction = new TransactionTemplate(manager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
            transaction.setTimeout(900);
            try {
                var result = transaction.execute(status -> {
                    Integer lock = jdbc.queryForObject("SELECT GET_LOCK(?,0)", Integer.class, "commercial-import:" + PLAN_HASH.substring(0, 32));
                    require(Integer.valueOf(1).equals(lock), "ANOTHER_IMPORT_OPERATOR_IS_RUNNING");
                    try {
                        var before = store.snapshot();
                        require(preflight.targetFingerprint().equals(before.targetFingerprint())
                                && preflight.schemaFingerprint().equals(before.schemaFingerprint()), "LIVE_DRIFT_BEFORE_WRITES");
                        boolean unitCreated = createBoxIfAbsent(jdbc, store, before, actor);
                        var prepared = store.snapshot();
                        var evidence = new CommercialImportAdapter.ApprovalEvidence(PLAN_HASH, ACTIONS_HASH,
                                prepared.targetFingerprint(), prepared.schemaFingerprint(),
                                "USER_TASK04B_ONE_SHOT_DEVELOPMENT:LOCAL_WINDOWS_OPERATOR:" + actor,
                                authorization.backupManifestPath(), report.resolve("downstream-verification.json").toString(),
                                authorization.sourceEvidencePath(), sourceObservedAt(authorization), Instant.now().plus(Duration.ofMinutes(30)));
                        UUID approval = adapter.recordApprovalInTransaction(plan, evidence, actor);
                        var imported = adapter.executeInTransaction(plan, approval, actor, PLAN_HASH);
                        require("COMMITTED".equals(imported.state()) && imported.created() == 195 && imported.skipped() == 0, "UNEXPECTED_IMPORT_OUTCOME");
                        var after = store.snapshot();
                        assertPreserved(codec, before, after);
                        require(after.units().size() == before.units().size() + (unitCreated ? 1 : 0), "UNEXPECTED_UNIT_COUNT");
                        for (var kind : CommercialImportPlan.Kind.values()) {
                            long expected = plan.rows().stream().filter(r -> r.kind() == kind).count();
                            require(after.rows().get(kind).size() == before.rows().get(kind).size() + expected, "UNEXPECTED_ENTITY_COUNT");
                        }
                        return Map.of("state", "COMMITTED", "result", imported, "approvalId", approval, "unitCreated", unitCreated,
                                "unitId", BOX_ID, "operatorType", "LOCAL_WINDOWS_OPERATOR", "operatorAuditId", actor,
                                "completedAt", Instant.now(), "originalRowsPreserved", true);
                    } finally { jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "commercial-import:" + PLAN_HASH.substring(0, 32)); }
                });
                writeNew(report.resolve("execution-result.json"), result);
                System.out.println("COMMITTED:7_CATEGORIES:64_PRODUCTS:124_VARIANTS:124_OUTBOX_EVENTS");
            } catch (Exception exception) {
                writeNew(report.resolve("execution-failure.json"), Map.of("state", "TRANSACTION_FAILED_RECONCILE_BEFORE_RETRY",
                        "exceptionType", exception.getClass().getSimpleName(), "at", Instant.now(), "automaticRetry", false));
                throw exception;
            }
        }
    }

    private static void requireExpectedIssues(CommercialImportAdapter.ValidationReport report, CommercialImportPlan plan, CommercialImportStore.Snapshot state) {
        boolean boxExists = state.units().containsKey(BOX_ID);
        require(report.issues().stream().allMatch(i -> "ACTIVE_NONDELETED_UNIT_REQUIRED".equals(i.code())
                && plan.variants().stream().anyMatch(v -> v.id().equals(i.sourceId()) && BOX_ID.equals(v.unitId()))), "PREFLIGHT_VALIDATION_BLOCKED");
        require(report.issues().size() == (boxExists ? 0 : 13), "UNEXPECTED_PENDING_UNIT_REFERENCES");
        require(plan.variants().stream().filter(v -> BOX_ID.equals(v.unitId())).allMatch(v -> BigDecimal.ONE.compareTo(v.quantity()) == 0), "BOX_QUANTITY_MUST_BE_ONE");
    }

    private static boolean createBoxIfAbsent(JdbcTemplate jdbc, CommercialImportStore store, CommercialImportStore.Snapshot state, UUID actor) {
        Map<String, Object> existing = state.units().get(BOX_ID);
        if (existing != null) {
            require(CommercialImportStore.active(existing) && "Hộp".equals(existing.get("name")) && "hộp".equals(existing.get("symbol"))
                    && "COUNT".equals(existing.get("type")) && existing.get("base_unit_id") == null
                    && new BigDecimal(existing.get("conversion_rate").toString()).compareTo(BigDecimal.ONE) == 0
                    && ((Number) existing.get("precision_scale")).intValue() == 0, "EXISTING_RESERVED_UNIT_MISMATCH");
            return false;
        }
        require(Integer.valueOf(0).equals(jdbc.queryForObject("SELECT COUNT(*) FROM units WHERE LOWER(name)=LOWER(?) OR LOWER(symbol)=LOWER(?)", Integer.class, "Hộp", "hộp")), "BOX_NAME_OR_SYMBOL_COLLISION");
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO units(id,name,symbol,precision_scale,type,base_unit_id,conversion_rate,status,created_by,updated_by,created_at,updated_at,version,is_deleted) VALUES (?,?,?,0,'COUNT',NULL,1.0000,'ACTIVE',?,?,?,?,0,0)",
                bytes(BOX_ID), "Hộp", "hộp", bytes(actor), bytes(actor), now, now);
        require(CommercialImportStore.active(store.snapshot().units().get(BOX_ID)), "BOX_INSERT_READBACK_FAILED");
        return true;
    }

    private static void assertPreserved(CommercialImportCodec codec, CommercialImportStore.Snapshot before, CommercialImportStore.Snapshot after) {
        for (var kind : CommercialImportPlan.Kind.values()) before.rows().get(kind).forEach((id, row) ->
                require(codec.hash(normalize(row)).equals(codec.hash(normalize(after.rows().get(kind).get(id)))), "EXISTING_CATALOG_ROW_CHANGED"));
        before.units().forEach((id, row) -> require(codec.hash(normalize(row)).equals(codec.hash(normalize(after.units().get(id)))), "EXISTING_UNIT_CHANGED"));
    }

    private static Instant sourceObservedAt(Authorization authorization) {
        try { return Instant.parse(JSON.readTree(Path.of(authorization.sourceEvidencePath()).toFile()).get("started_at_utc").asText()); }
        catch (Exception exception) { throw new IllegalStateException("SOURCE_EVIDENCE_TIMESTAMP_INVALID"); }
    }

    private static void verifySource(Authorization authorization) throws Exception {
        verifyFile(authorization.sourceEvidencePath(), authorization.sourceEvidenceSha256());
        JsonNode source = JSON.readTree(Path.of(authorization.sourceEvidencePath()).toFile());
        JsonNode drift = source.path("datasources").path("Recommendation").path("drift");
        require(drift.size() == 3 && drift.findValues("match").stream().allMatch(JsonNode::asBoolean), "SOURCE_DRIFT_OR_EVIDENCE_INCOMPLETE");
        Instant at = sourceObservedAt(authorization);
        require(at.isBefore(Instant.now().plusSeconds(5)) && at.isAfter(Instant.now().minus(Duration.ofMinutes(15))), "SOURCE_REVALIDATION_STALE");
    }

    private static void verifyBackup(Authorization authorization, CommercialImportAdapter.ValidationReport preflight) throws Exception {
        verifyFile(authorization.backupManifestPath(), authorization.backupManifestSha256());
        JsonNode backup = JSON.readTree(Path.of(authorization.backupManifestPath()).toFile());
        require(backup.path("verified").asBoolean() && authorization.expectedServerUuid().equals(backup.path("server_uuid").asText())
                && preflight.targetFingerprint().equals(backup.path("target_fingerprint").asText())
                && preflight.schemaFingerprint().equals(backup.path("schema_fingerprint").asText()), "VERIFIED_MATCHING_BACKUP_REQUIRED");
        verifyFile(backup.path("backup_path").asText(), backup.path("backup_sha256").asText());
        verifyFile(backup.path("restore_path").asText(), backup.path("restore_sha256").asText());
    }

    private static void verifyAuthorizationAcl(Path path, NTSystem windows) throws Exception {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        require(acl != null, "WINDOWS_ACL_REQUIRED");
        var operator = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(windows.getDomain() + "\\" + windows.getName());
        var system = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("NT AUTHORITY\\SYSTEM");
        require(acl.getOwner().equals(operator), "AUTHORIZATION_MUST_BE_OWNED_BY_LOCAL_OPERATOR");
        Set<AclEntryPermission> writes = Set.of(AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
                AclEntryPermission.WRITE_ACL, AclEntryPermission.WRITE_OWNER, AclEntryPermission.DELETE);
        require(acl.getAcl().stream().filter(a -> a.type() == AclEntryType.ALLOW && a.permissions().stream().anyMatch(writes::contains))
                .allMatch(a -> a.principal().equals(operator) || a.principal().equals(system)), "AUTHORIZATION_ACL_TOO_BROAD");
    }

    private static void verifyFile(String name, String expected) throws Exception {
        require(name != null && expected != null && expected.matches("[a-f0-9]{64}")
                && expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(name))))), "REQUIRED_ARTIFACT_HASH_MISMATCH");
    }
    private static String requiredEnv(String name) { String value = System.getenv(name); require(value != null && !value.isBlank(), "DATABASE_CONFIGURATION_REQUIRED"); return value; }
    private static void writeNew(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n", StandardOpenOption.CREATE_NEW);
    }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }

    // Deliberately NOT @Configuration/@Component: normal Catalog startup never discovers this wiring.
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CatalogOutboxEventRepository.class)
    @Import({CatalogOutboxConfig.class, CatalogOutboxEventFactory.class, CatalogOutboxTopicResolver.class, CatalogOutboxPublisher.class})
    static class Wiring {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("org.hvtra.catalog.outbox.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false",
                    "hibernate.jdbc.time_zone", "UTC", "hibernate.jdbc.batch_size", "50"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory, DataSource dataSource) {
            var manager = new JpaTransactionManager(factory); manager.setDataSource(dataSource); return manager;
        }
    }
}
