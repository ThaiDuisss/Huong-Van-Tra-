package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TasteComposition {
    private final TasteWriteLock writeLock;
    private final TasteDocuments documents;
    private final ProductVariantRepository variants;
    private final VariantTasteProfileRepository profiles;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final AuditorAware<UUID> auditor;

    public void lockWrites() {
        writeLock.acquire();
    }

    public void changedVariant(UUID variantId) {
        variants.findByIdForUpdate(variantId).ifPresent(this::capture);
    }

    public void changedIngredient(UUID ingredientId) {
        entityManager.flush();
        List<UUID> affected = ids("""
                select product_variant_id from bom_items where ingredient_id = ? and is_deleted = 0
                """, ingredientId);
        affected.stream().distinct().sorted().forEach(this::refreshTracked);
    }

    public void changedUnits(Set<UUID> roots) {
        entityManager.flush();
        var pending = new ArrayDeque<>(roots);
        Set<UUID> units = new HashSet<>();
        Set<UUID> affected = new TreeSet<>();
        while (!pending.isEmpty()) {
            UUID unitId = pending.removeFirst();
            if (!units.add(unitId)) {
                continue;
            }
            pending.addAll(ids("select id from units where base_unit_id = ?", unitId));
            affected.addAll(ids("select id from product_variants where unit_id = ? and is_deleted = 0", unitId));
            affected.addAll(ids("""
                    select b.product_variant_id from bom_items b join ingredients i on i.id = b.ingredient_id
                    where b.unit_id = ? and b.is_deleted = 0 and i.type <> 0
                    """, unitId));
            affected.addAll(ids("""
                    select b.product_variant_id from bom_items b join ingredients i on i.id = b.ingredient_id
                    where i.unit_id = ? and b.is_deleted = 0 and i.type <> 0
                    """, unitId));
        }
        affected.forEach(this::refreshTracked);
    }

    private List<UUID> ids(String sql, UUID id) {
        // A locking read sees current references even inside an importing transaction with an older snapshot.
        return jdbc.query(sql + " for update", (rs, row) -> uuid(rs.getBytes(1)), bytes(id));
    }

    private void refreshTracked(UUID id) {
        variants.findByIdForUpdate(id).filter(variant -> variant.getCompositionRevision() > 0)
                .ifPresent(this::capture);
    }

    Basis capture(ProductVariant variant) {
        entityManager.flush();
        entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        Basis basis = read(variant, true);
        if (!basis.digest().equals(variant.getCompositionSha256())) {
            variant.setCompositionRevision(Math.incrementExact(variant.getCompositionRevision()));
            variant.setCompositionSha256(basis.digest());
            profiles.markStale(variant.getId(), VariantTasteProfile.State.APPROVED, VariantTasteProfile.State.STALE,
                    auditor.getCurrentAuditor().orElse(null), now());
        }
        return basis;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Basis inspect(ProductVariant variant) {
        return read(variant, false);
    }

    Basis inspectForApproval(ProductVariant variant) {
        return read(variant, true);
    }

    private Basis read(ProductVariant variant, boolean current) {
        ObjectNode basis = documents.object().put("ruleVersion", 1).put("variantId", variant.getId().toString())
                .put("quantity", TasteDocuments.decimal(variant.getQuantity()));
        UnitBasis output = unit(variant.getUnit().getId(), current);
        basis.set("unit", output.document());
        boolean valid = output.valid() && variant.getQuantity() != null && variant.getQuantity().signum() > 0;
        List<RecipeLine> recipe = jdbc.query("""
                select i.id, i.type, i.unit_id, i.status, i.is_deleted, b.quantity, b.waste_rate, b.unit_id
                from bom_items b join ingredients i on i.id = b.ingredient_id
                where b.product_variant_id = ? and b.is_deleted = 0 and i.type <> 0
                """ + lockClause(current), (rs, row) -> new RecipeLine(uuid(rs.getBytes(1)), rs.getInt(2),
                uuid(rs.getBytes(3)), rs.getString(4), rs.getBoolean(5), rs.getBigDecimal(6),
                rs.getBigDecimal(7), uuid(rs.getBytes(8))), bytes(variant.getId()));
        recipe.sort(Comparator.comparing(RecipeLine::ingredientId).thenComparing(RecipeLine::unitId));
        ArrayNode ingredients = basis.putArray("ingredients");
        for (RecipeLine line : recipe) {
            UnitBasis ingredientUnit = unit(line.ingredientUnitId(), current);
            UnitBasis measurement = unit(line.unitId(), current);
            ObjectNode item = ingredients.addObject().put("ingredientId", line.ingredientId().toString())
                    .put("kind", line.kind()).put("status", line.status()).put("deleted", line.deleted())
                    .put("quantity", TasteDocuments.decimal(line.quantity()))
                    .put("wasteRate", TasteDocuments.decimal(line.wasteRate()));
            item.set("ingredientUnit", ingredientUnit.document());
            item.set("unit", measurement.document());
            valid &= line.kind() == 1 && !line.deleted() && "ACTIVE".equals(line.status())
                    && line.quantity().signum() > 0 && line.wasteRate().signum() >= 0
                    && ingredientUnit.valid() && measurement.valid()
                    && ingredientUnit.rootId().equals(measurement.rootId());
        }
        JsonNode canonical = documents.canonical(basis);
        return new Basis(canonical, documents.hash(canonical), valid && !recipe.isEmpty());
    }

    private UnitBasis unit(UUID start, boolean current) {
        ArrayNode chain = documents.object().putArray("chain");
        Set<UUID> seen = new HashSet<>();
        UUID root = start;
        boolean valid = start != null;
        UUID next = start;
        while (next != null && seen.add(next)) {
            UUID id = next;
            RawUnit row = jdbc.query("""
                    select type, base_unit_id, conversion_rate, status, is_deleted from units where id = ?
                    """ + lockClause(current), (rs, index) -> new RawUnit(rs.getString(1), uuid(rs.getBytes(2)),
                    rs.getBigDecimal(3), rs.getString(4), rs.getBoolean(5)), bytes(id)).stream().findFirst().orElse(null);
            ObjectNode entry = chain.addObject().put("unitId", id.toString());
            if (row == null) {
                entry.put("missing", true);
                return new UnitBasis(chain, false, id);
            }
            entry.put("type", row.type()).put("status", row.status()).put("deleted", row.deleted());
            boolean converted = row.baseId() != null;
            if (converted) {
                entry.put("conversionRate", TasteDocuments.decimal(row.conversion()));
            }
            valid &= !row.deleted() && "ACTIVE".equals(row.status())
                    && (!"PACKAGING".equals(row.type()) || converted)
                    && (!converted || row.conversion() != null && row.conversion().signum() > 0);
            root = id;
            next = row.baseId();
        }
        if (next != null) {
            chain.addObject().put("cycle", next.toString());
        }
        return new UnitBasis(chain, valid && next == null, root);
    }

    private static String lockClause(boolean current) {
        return current ? " for update" : "";
    }

    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }

    private static UUID uuid(byte[] value) {
        if (value == null) {
            return null;
        }
        ByteBuffer binary = ByteBuffer.wrap(value);
        return new UUID(binary.getLong(), binary.getLong());
    }

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public record Basis(JsonNode document, String digest, boolean valid) {
    }

    private record UnitBasis(JsonNode document, boolean valid, UUID rootId) {
    }

    private record RawUnit(String type, UUID baseId, BigDecimal conversion, String status, boolean deleted) {
    }

    private record RecipeLine(UUID ingredientId, int kind, UUID ingredientUnitId, String status, boolean deleted,
                              BigDecimal quantity, BigDecimal wasteRate, UUID unitId) {
    }
}
