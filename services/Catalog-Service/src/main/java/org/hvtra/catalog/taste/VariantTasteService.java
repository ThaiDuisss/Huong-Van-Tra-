package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class VariantTasteService {
    private final VariantTasteProfileRepository profiles;
    private final TasteTaxonomyRepository taxonomies;
    private final ProductVariantRepository variants;
    private final ProductVariantService productVariants;
    private final TasteComposition composition;
    private final TasteManifest manifests;
    private final TasteDocuments documents;

    private static final Set<VariantTasteProfile.State> DEFAULT_LIST_STATUSES =
            Set.of(VariantTasteProfile.State.APPROVED, VariantTasteProfile.State.DRAFT);

    public Page<Response> history(UUID variantId, int page, int size) {
        TasteAccess.readTaxonomy();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw TasteFailure.invalid("Phân trang không hợp lệ");
        }
        ProductVariant variant = variants.findById(variantId).orElseThrow(VariantTasteService::variantMissing);
        Eligibility eligibility = eligibility(variant);
        return profiles.findByVariantIdOrderByProfileRevisionDesc(variantId, PageRequest.of(page, size))
                .map(profile -> response(profile, eligibility));
    }

    public PageResponse<VariantProfilesResponse> listVariantProfiles(
            String keyword,
            Status status,
            Set<VariantTasteProfile.State> profileStatuses,
            Pageable pageable) {
        TasteAccess.readTaxonomy();
        PageResponse<ProductResponse.VariantSummaryResponse> page =
                productVariants.findAll(keyword, null, null, null, null, null, status, false, pageable);
        if (page.content().isEmpty()) {
            return new PageResponse<>(
                    List.of(), page.page(), page.size(), page.totalElements(), page.totalPages(), page.first(), page.last());
        }

        Set<UUID> variantIds = page.content().stream()
                .map(ProductResponse.VariantSummaryResponse::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<VariantTasteProfile.State> effectiveStatuses =
                profileStatuses == null || profileStatuses.isEmpty() ? DEFAULT_LIST_STATUSES : profileStatuses;

        List<VariantTasteProfile> found =
                profiles.findByVariantIdInAndStatusInOrderByVariantIdAscProfileRevisionDesc(variantIds, effectiveStatuses);

        Set<UUID> approvedVariantIds = found.stream()
                .filter(p -> p.getStatus() == VariantTasteProfile.State.APPROVED)
                .map(VariantTasteProfile::getVariantId)
                .collect(Collectors.toSet());
        Map<UUID, Eligibility> eligibilityByVariant = new HashMap<>();
        if (!approvedVariantIds.isEmpty()) {
            for (ProductVariant variant : variants.findAllByIdWithDetails(approvedVariantIds)) {
                Eligibility eligibility = eligibility(variant);
                if (eligibility != null) {
                    eligibilityByVariant.put(variant.getId(), eligibility);
                }
            }
        }

        Map<UUID, List<Response>> profilesByVariant = found.stream()
                .collect(Collectors.groupingBy(
                        VariantTasteProfile::getVariantId,
                        Collectors.mapping(
                                p -> response(p, eligibilityByVariant.get(p.getVariantId())),
                                Collectors.toList())));

        List<VariantProfilesResponse> items = page.content().stream()
                .map(v -> new VariantProfilesResponse(v.id(), v, profilesByVariant.getOrDefault(v.id(), List.of())))
                .toList();

        return new PageResponse<>(
                items, page.page(), page.size(), page.totalElements(), page.totalPages(), page.first(), page.last());
    }

    public Response detail(UUID id) {
        TasteAccess.readTaxonomy();
        VariantTasteProfile profile = get(id);
        ProductVariant variant = variants.findById(profile.getVariantId()).orElseThrow(VariantTasteService::variantMissing);
        return response(profile, eligibility(variant));
    }

    @Transactional
    public Response create(UUID variantId, Draft request) {
        UUID actor = TasteAccess.assess();
        composition.lockWrites();
        return create(availableVariant(variantId), request, actor);
    }

    @Transactional
    public Response revision(UUID id, long version, Draft request) {
        UUID actor = TasteAccess.assess();
        composition.lockWrites();
        VariantTasteProfile previous = get(id);
        TasteFailure.matchVersion(previous, version);
        return create(availableVariant(previous.getVariantId()), request, actor);
    }

    private Response create(ProductVariant variant, Draft request, UUID actor) {
        TasteTaxonomy taxonomy = taxonomies.findByVersionCode(request.taxonomyVersion())
                .orElseThrow(() -> TasteFailure.notFound("TASTE_TAXONOMY_NOT_FOUND", "Không tìm thấy taxonomy"));
        TasteManifest.Release release = published(taxonomy);
        TasteComposition.Basis basis = composition.capture(variant);
        VariantTasteProfile profile = new VariantTasteProfile();
        profile.setVariantId(variant.getId());
        profile.setTaxonomy(taxonomy);
        int previousRevision = profiles.findFirstByVariantIdOrderByProfileRevisionDesc(variant.getId())
                .map(VariantTasteProfile::getProfileRevision).orElse(0);
        profile.setProfileRevision(Math.incrementExact(previousRevision));
        profile.setCompositionRevision(variant.getCompositionRevision());
        profile.setCompositionSha256(basis.digest());
        profile.setBasisSnapshot(basis.document());
        assess(profile, request.assessment(), release, actor);
        return response(profiles.saveAndFlush(profile), null);
    }

    @Transactional
    public Response update(UUID id, long version, Assessment request) {
        UUID actor = TasteAccess.assess();
        composition.lockWrites();
        VariantTasteProfile profile = editable(id, version);
        ProductVariant variant = availableVariant(profile.getVariantId());
        requireCurrentBasis(profile, variant, false);
        assess(profile, request, published(profile.getTaxonomy()), actor);
        return response(profiles.saveAndFlush(profile), null);
    }

    @Transactional
    public Response approve(UUID id, long version) {
        UUID actor = TasteAccess.manage();
        composition.lockWrites();
        VariantTasteProfile profile = editable(id, version);
        ProductVariant variant = availableVariant(profile.getVariantId());
        TasteComposition.Basis basis = requireCurrentBasis(profile, variant, true);
        TasteManifest.Release release = published(profile.getTaxonomy());
        JsonNode values = manifests.sensory(profile.getSensoryValues(), release);
        if (values.isEmpty() || profile.getAssessedBy() == null || profile.getAssessedAt() == null) {
            throw TasteFailure.invalid("Cần có ít nhất một giá trị đã đánh giá trước khi duyệt");
        }
        JsonNode protocol = preparation(profile.getPreparationProtocol());
        if (!documents.hash(protocol).equals(profile.getPreparationSha256())) {
            throw TasteFailure.conflict("Quy trình pha đã thay đổi");
        }

        LocalDateTime now = TasteComposition.now();
        profiles.findByVariantIdAndTaxonomyIdAndPreparationCodeAndStatus(profile.getVariantId(),
                profile.getTaxonomy().getId(), profile.getPreparationCode(), VariantTasteProfile.State.APPROVED)
                .ifPresent(previous -> {
                    previous.setStatus(VariantTasteProfile.State.RETIRED);
                    previous.stamp(actor, now);
                    profiles.flush();
                });
        profile.setProfileSha256(profileDigest(profile, release.digest(), values));
        profile.setApprovedAt(now);
        profile.setApprovedBy(actor);
        profile.setStatus(VariantTasteProfile.State.APPROVED);
        profile.stamp(actor, now);
        return response(profiles.saveAndFlush(profile), new Eligibility(variant.getCompositionRevision(), basis.digest()));
    }

    @Transactional
    public Response retire(UUID id, long version) {
        UUID actor = TasteAccess.manage();
        composition.lockWrites();
        VariantTasteProfile profile = get(id);
        TasteFailure.matchVersion(profile, version);
        if (profile.getStatus() != VariantTasteProfile.State.APPROVED && profile.getStatus() != VariantTasteProfile.State.STALE) {
            throw TasteFailure.conflict("Chỉ profile đã được duyệt hoặc STALE có thể retire");
        }
        profile.setStatus(VariantTasteProfile.State.RETIRED);
        profile.stamp(actor, TasteComposition.now());
        return response(profiles.saveAndFlush(profile), null);
    }

    private TasteComposition.Basis requireCurrentBasis(VariantTasteProfile profile, ProductVariant variant,
                                                        boolean requireValid) {
        TasteComposition.Basis basis = composition.inspectForApproval(variant);
        if (profile.getCompositionRevision() != variant.getCompositionRevision()
                || !profile.getCompositionSha256().equals(variant.getCompositionSha256())
                || !profile.getCompositionSha256().equals(basis.digest())) {
            throw TasteFailure.conflict("Công thức đã thay đổi. Cần tạo revision đánh giá mới");
        }
        if (requireValid && !basis.valid()) {
            throw TasteFailure.invalid("Công thức thiếu nguyên liệu hoặc có đơn vị/nguyên liệu không hợp lệ");
        }
        return basis;
    }

    private TasteManifest.Release published(TasteTaxonomy taxonomy) {
        if (taxonomy.getState() != TasteTaxonomy.State.PUBLISHED) {
            throw TasteFailure.conflict("Cần dùng phiên bản taxonomy PUBLISHED");
        }
        TasteManifest.Release release = manifests.normalize(taxonomy.getVersionCode(), taxonomy.getManifest(), true);
        if (!release.digest().equals(taxonomy.getManifestSha256())) {
            throw TasteFailure.conflict("Không xác minh được mã kiểm tra của taxonomy");
        }
        return release;
    }

    private void assess(VariantTasteProfile profile, Assessment request, TasteManifest.Release release, UUID actor) {
        JsonNode protocol = preparation(request.preparationProtocol());
        JsonNode sensory = manifests.sensory(request.sensoryValues(), release);
        LocalDateTime now = TasteComposition.now();
        profile.setPreparationCode(request.preparationCode());
        profile.setPreparationProtocol(protocol);
        profile.setPreparationSha256(documents.hash(protocol));
        profile.setSensoryValues(sensory);
        profile.setSourceMethod(request.sourceMethod());
        profile.setAssessedBy(sensory.isEmpty() ? null : actor);
        profile.setAssessedAt(sensory.isEmpty() ? null : now);
        profile.stamp(actor, now);
    }

    private JsonNode preparation(JsonNode value) {
        if (!value.isObject() || documents.size(value) > 16 * 1024
                || value.path("instructions").asText("").isBlank()
                || !value.path("instructions").isTextual()
                || value.path("servingBasis").asText("").isBlank()
                || !value.path("servingBasis").isTextual()) {
            throw TasteFailure.invalid("Quy trình pha cần instructions và servingBasis dạng chuỗi, tối đa 16 KiB");
        }
        return documents.canonical(value);
    }

    String profileDigest(VariantTasteProfile profile, String taxonomyDigest, JsonNode values) {
        ObjectNode data = documents.object().put("variantId", profile.getVariantId().toString())
                .put("profileRevision", profile.getProfileRevision()).put("compositionRevision", profile.getCompositionRevision())
                .put("compositionSha256", profile.getCompositionSha256()).put("taxonomySha256", taxonomyDigest)
                .put("preparationCode", profile.getPreparationCode()).put("preparationSha256", profile.getPreparationSha256())
                .put("sourceMethod", profile.getSourceMethod());
        data.set("sensoryValues", values);
        return documents.hash(documents.canonical(data));
    }

    private ProductVariant availableVariant(UUID id) {
        ProductVariant variant = variants.findByIdForUpdate(id).orElseThrow(VariantTasteService::variantMissing);
        if (!sellableVariant(variant)) {
            throw TasteFailure.conflict("Biến thể hoặc sản phẩm hiện không hợp lệ để đánh giá khẩu vị");
        }
        return variant;
    }

    private boolean sellableVariant(ProductVariant variant) {
        return !Boolean.TRUE.equals(variant.getIsDeleted()) && variant.getStatus() == Status.ACTIVE && !variant.isCustomBundle()
                && !Boolean.TRUE.equals(variant.getProduct().getIsDeleted()) && variant.getProduct().getStatus() == Status.ACTIVE;
    }

    private Eligibility eligibility(ProductVariant variant) {
        if (!sellableVariant(variant) || variant.getCompositionRevision() == 0) {
            return null;
        }
        TasteComposition.Basis basis = composition.inspect(variant);
        return basis.valid() && basis.digest().equals(variant.getCompositionSha256())
                ? new Eligibility(variant.getCompositionRevision(), basis.digest()) : null;
    }

    private VariantTasteProfile editable(UUID id, long version) {
        VariantTasteProfile profile = get(id);
        TasteFailure.matchVersion(profile, version);
        if (profile.getStatus() != VariantTasteProfile.State.DRAFT) {
            throw TasteFailure.conflict("Chỉ profile DRAFT được chỉnh sửa hoặc duyệt");
        }
        return profile;
    }

    private VariantTasteProfile get(UUID id) {
        return profiles.findById(id).orElseThrow(
                () -> TasteFailure.notFound("TASTE_PROFILE_NOT_FOUND", "Không tìm thấy profile khẩu vị"));
    }

    private static TasteFailure variantMissing() {
        return TasteFailure.notFound("TASTE_VARIANT_NOT_FOUND", "Không tìm thấy biến thể");
    }

    private Response response(VariantTasteProfile profile, Eligibility current) {
        TasteTaxonomy taxonomy = profile.getTaxonomy();
        boolean rankable = current != null && profile.getStatus() == VariantTasteProfile.State.APPROVED
                && taxonomy.getState() == TasteTaxonomy.State.PUBLISHED
                && profile.getCompositionRevision() == current.revision()
                && profile.getCompositionSha256().equals(current.digest());
        return new Response(profile.getId(), profile.getVariantId(), taxonomy.getId(), taxonomy.getVersionCode(),
                taxonomy.getManifestSha256(), profile.getProfileRevision(), profile.getCompositionRevision(),
                profile.getCompositionSha256(), profile.getBasisSnapshot(), profile.getPreparationCode(),
                profile.getPreparationProtocol(), profile.getPreparationSha256(), profile.getSensoryValues(),
                profile.getSourceMethod(), profile.getAssessedBy(), TasteRecord.utc(profile.getAssessedAt()),
                profile.getApprovedBy(), TasteRecord.utc(profile.getApprovedAt()), profile.getProfileSha256(),
                profile.getStatus(), rankable, profile.getRowVersion(),
                TasteRecord.utc(profile.getCreatedAt()), TasteRecord.utc(profile.getUpdatedAt()));
    }

    record Draft(@NotNull @Pattern(regexp = TasteDocuments.VERSION_CODE) String taxonomyVersion,
                 @NotNull @Valid Assessment assessment) {
    }

    record Assessment(@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String preparationCode,
                      @NotNull JsonNode preparationProtocol,
                      @NotNull JsonNode sensoryValues,
                      @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String sourceMethod) {
    }

    record Response(UUID id, UUID variantId, UUID taxonomyVersionId, String taxonomyVersion, String taxonomySha256,
                    int profileRevision, long compositionRevision, String compositionSha256, JsonNode basisSnapshot,
                    String preparationCode, JsonNode preparationProtocol, String preparationSha256,
                    JsonNode sensoryValues, String sourceMethod, UUID assessedBy, Instant assessedAt,
                    UUID approvedBy, Instant approvedAt, String profileSha256, VariantTasteProfile.State status,
                    boolean rankable, long rowVersion, Instant createdAt, Instant updatedAt) {
    }

    record VariantProfilesResponse(UUID id, ProductResponse.VariantSummaryResponse variant, List<Response> profiles) {
    }

    private record Eligibility(long revision, String digest) {
    }
}
