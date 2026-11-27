package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
class TasteSnapshotService {
    private static final int PAGE_SIZE = 100;
    private final TasteDocuments documents;
    private final TasteManifest manifests;
    private final TasteTaxonomyRepository taxonomies;
    private final ProductVariantRepository variants;
    private final VariantTasteProfileRepository profiles;
    private final TasteComposition composition;
    private final VariantTasteService profileService;
    private final int maxVariants;
    private final int maxBytes;

    TasteSnapshotService(TasteDocuments documents, TasteManifest manifests, TasteTaxonomyRepository taxonomies,
                         ProductVariantRepository variants, VariantTasteProfileRepository profiles,
                         TasteComposition composition, VariantTasteService profileService,
                         @Value("${taste.catalog.max-variants:2000}") int maxVariants,
                         @Value("${taste.catalog.max-bytes:4194304}") int maxBytes) {
        if (maxVariants < 1 || maxVariants > 10000 || maxBytes < 1024 || maxBytes > 16777216) {
            throw new IllegalArgumentException("Invalid taste snapshot capacity");
        }
        this.documents = documents;
        this.manifests = manifests;
        this.taxonomies = taxonomies;
        this.variants = variants;
        this.profiles = profiles;
        this.composition = composition;
        this.profileService = profileService;
        this.maxVariants = maxVariants;
        this.maxBytes = maxBytes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ,
            timeoutString = "${taste.catalog.snapshot-transaction-timeout-seconds:55}")
    public Snapshot read() {
        String readAt = Instant.now().truncatedTo(ChronoUnit.MICROS).toString();
        var payload = documents.object().put("schemaVersion", 1);
        Budget budget = new Budget();
        Map<String, TasteManifest.Release> releases = appendTaxonomies(payload, budget);
        payload.put("catalogState", releases.isEmpty() ? "NO_TAXONOMY" : "PUBLISHED");
        List<ProductVariant> selected = variants.findTasteSnapshotVariants(Status.ACTIVE, PageRequest.of(0, maxVariants + 1));
        if (selected.size() > maxVariants) {
            throw TasteSnapshotFailure.capacity();
        }
        var productArray = payload.putArray("products");
        var variantArray = payload.putArray("variants");
        Map<UUID, ProductVariant> sellable = new LinkedHashMap<>();
        Set<UUID> products = new TreeSet<>();
        for (ProductVariant variant : selected) {
            Product product = variant.getProduct();
            if (products.add(product.getId())) {
                budget.append(productArray, product(product));
            }
            boolean canSell = sellable(variant);
            if (canSell && variant.getCompositionRevision() > 0) {
                sellable.put(variant.getId(), variant);
            }
            budget.append(variantArray, variant(variant, canSell));
        }

        var profileArray = payload.putArray("profiles");
        var preparationCodes = new TreeSet<String>();
        if (!sellable.isEmpty() && !releases.isEmpty()) {
            appendProfiles(profileArray, preparationCodes, sellable, releases, budget);
        }
        ArrayNode preparation = payload.putArray("preparationCodes");
        preparationCodes.forEach(preparation::add);
        payload.putObject("counts").put("products", products.size()).put("variants", selected.size())
                .put("approvedProfiles", profileArray.size());
        JsonNode canonical = documents.canonical(payload);
        if (documents.size(canonical) > maxBytes) {
            throw TasteSnapshotFailure.capacity();
        }
        String digest = documents.hash(canonical);
        return new Snapshot("taste-v1-" + digest, readAt, digest, canonical);
    }

    private Map<String, TasteManifest.Release> appendTaxonomies(ObjectNode payload, Budget budget) {
        var array = payload.putArray("taxonomies");
        var supported = payload.putArray("supportedTaxonomyVersions");
        Map<String, TasteManifest.Release> releases = new HashMap<>();
        for (int page = 0; ; page++) {
            var batch = taxonomies.findByStateOrderByVersionCode(TasteTaxonomy.State.PUBLISHED, PageRequest.of(page, PAGE_SIZE));
            for (TasteTaxonomy row : batch) {
                String version = row.getVersionCode();
                TasteManifest.Release release;
                try {
                    release = manifests.normalize(version, row.getManifest(), true);
                } catch (TasteFailure failure) {
                    throw TasteSnapshotFailure.invalid();
                }
                if (!release.digest().equals(row.getManifestSha256())) {
                    throw TasteSnapshotFailure.invalid();
                }
                var item = documents.object().put("versionCode", version)
                        .put("manifestSha256", release.digest()).put("state", row.getState().name());
                item.set("manifest", release.manifest());
                budget.append(array, item);
                releases.put(version, release);
                supported.add(version);
            }
            if (batch.size() < PAGE_SIZE) {
                return releases;
            }
        }
    }

    private void appendProfiles(ArrayNode array, Set<String> preparation, Map<UUID, ProductVariant> sellable,
                                Map<String, TasteManifest.Release> releases, Budget budget) {
        Map<UUID, Boolean> currentBasis = new HashMap<>();
        for (int page = 0; ; page++) {
            var batch = profiles.findByVariantIdInAndStatusAndTaxonomyStateOrderById(sellable.keySet(),
                    VariantTasteProfile.State.APPROVED, TasteTaxonomy.State.PUBLISHED, PageRequest.of(page, PAGE_SIZE));
            for (VariantTasteProfile profile : batch) {
                ProductVariant variant = sellable.get(profile.getVariantId());
                if (profile.getCompositionRevision() != variant.getCompositionRevision()
                        || !profile.getCompositionSha256().equals(variant.getCompositionSha256())) {
                    continue;
                }
                boolean matches = currentBasis.computeIfAbsent(variant.getId(), id -> {
                    TasteComposition.Basis basis = composition.inspect(variant);
                    return basis.valid() && basis.digest().equals(variant.getCompositionSha256());
                });
                if (!matches) {
                    continue;
                }
                var release = releases.get(profile.getTaxonomy().getVersionCode());
                try {
                    budget.append(array, approvedProfile(profile, release));
                } catch (TasteFailure failure) {
                    throw TasteSnapshotFailure.invalid();
                }
                preparation.add(profile.getPreparationCode());
            }
            if (batch.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private JsonNode approvedProfile(VariantTasteProfile row, TasteManifest.Release release) {
        if (release == null || row.getApprovedAt() == null || row.getApprovedBy() == null
                || !row.getPreparationCode().matches("[A-Z][A-Z0-9_]{0,79}")) {
            throw TasteSnapshotFailure.invalid();
        }
        JsonNode values = manifests.sensory(row.getSensoryValues(), release);
        JsonNode protocol = documents.canonical(row.getPreparationProtocol());
        if (values.isEmpty() || !documents.hash(protocol).equals(row.getPreparationSha256())
                || !profileService.profileDigest(row, release.digest(), values).equals(row.getProfileSha256())) {
            throw TasteSnapshotFailure.invalid();
        }
        ObjectNode profile = documents.object().put("id", row.getId().toString())
                .put("variantId", row.getVariantId().toString()).put("profileRevision", row.getProfileRevision())
                .put("taxonomyVersion", row.getTaxonomy().getVersionCode()).put("taxonomySha256", release.digest())
                .put("preparationCode", row.getPreparationCode()).put("preparationSha256", row.getPreparationSha256())
                .put("compositionRevision", row.getCompositionRevision()).put("compositionSha256", row.getCompositionSha256())
                .put("profileSha256", row.getProfileSha256()).put("sourceMethod", row.getSourceMethod())
                .put("status", row.getStatus().name()).put("approvedAt", TasteRecord.utc(row.getApprovedAt()).toString());
        profile.set("preparationProtocol", protocol);
        profile.set("sensoryValues", values);
        return profile;
    }

    private ObjectNode product(Product product) {
        return documents.object().put("id", product.getId().toString()).put("name", product.getName())
                .put("status", product.getStatus().name()).put("isDeleted", product.getIsDeleted());
    }

    private ObjectNode variant(ProductVariant variant, boolean sellable) {
        return documents.object().put("id", variant.getId().toString())
                .put("productId", variant.getProduct().getId().toString()).put("name", variant.getVariantName())
                .put("sku", variant.getSku()).put("status", variant.getStatus().name()).put("isDeleted", variant.getIsDeleted())
                .put("sellable", sellable).put("customBundle", variant.isCustomBundle())
                .put("compositionRevision", variant.getCompositionRevision()).put("compositionSha256", variant.getCompositionSha256());
    }

    private boolean sellable(ProductVariant variant) {
        return variant.getInventoryPolicy() == InventoryPolicy.DO_NOT_TRACK
                || variant.getInventoryPolicy() == InventoryPolicy.TRACK_STOCK
                && variant.getSellableQuantity().signum() > 0;
    }

    private final class Budget {
        private long bytes;

        void append(ArrayNode array, JsonNode node) {
            bytes += documents.size(documents.canonical(node));
            if (bytes > maxBytes) {
                throw TasteSnapshotFailure.capacity();
            }
            array.add(node);
        }
    }

    record Snapshot(String snapshotId, String sourceReadAt, String payloadSha256, JsonNode payload) {
    }
}
