package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.taste.TasteManifestV2;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TasteTaxonomyService {
    private final TasteTaxonomyRepository repository;
    private final TasteManifest manifests;
    private final TasteWriteLock writeLock;

    public Response current() {
        TasteAccess.readTaxonomy();
        TasteTaxonomy release = repository.findByCurrentTrue().orElseThrow(TasteTaxonomyService::missing);
        return response(release, List.copyOf(ancestry(published(release)).values()));
    }

    public Response detail(String version) {
        TasteAccess.readTaxonomy();
        TasteTaxonomy release = get(version);
        if (release.getState() == TasteTaxonomy.State.DRAFT && !TasteAccess.isManager()) {
            throw missing();
        }
        return response(release);
    }

    @Transactional
    public Response create(Create request) {
        UUID actor = TasteAccess.manage();
        if ("current".equalsIgnoreCase(request.versionCode())) {
            throw TasteFailure.invalid("Mã current được dành cho route release hiện hành");
        }
        TasteManifest.Release document = manifests.normalize(request.versionCode(), request.manifest(), false);
        writeLock.acquire();
        if (repository.findByVersionCode(request.versionCode()).isPresent()) {
            throw TasteFailure.conflict("Version taxonomy đã tồn tại");
        }
        TasteTaxonomy release = new TasteTaxonomy();
        release.setVersionCode(request.versionCode());
        apply(release, document, actor);
        return response(repository.saveAndFlush(release));
    }

    @Transactional
    public Response update(String version, long expectedVersion, JsonNode manifest) {
        UUID actor = TasteAccess.manage();
        writeLock.acquire();
        TasteTaxonomy release = editable(version, expectedVersion);
        apply(release, manifests.normalize(version, manifest, false), actor);
        return response(repository.saveAndFlush(release));
    }

    @Transactional
    public Response publish(String version, long expectedVersion) {
        UUID actor = TasteAccess.manage();
        writeLock.acquire();
        TasteTaxonomy release = editable(version, expectedVersion);
        TasteManifest.Release document = manifests.normalize(version, release.getManifest(), true);
        TasteTaxonomy current = repository.findByCurrentTrue().orElse(null);
        boolean dynamic = document.manifest().path("schemaVersion").intValue() == 2;
        if (dynamic) {
            ancestry(document);
        } else if (current != null) {
            throw TasteFailure.conflict("Bộ tiêu chí mới cần dùng schemaVersion 2 và kiểm tra tương thích trước khi kích hoạt");
        }
        apply(release, document, actor);
        release.setState(TasteTaxonomy.State.PUBLISHED);
        release.setCurrent(!dynamic);
        release.setPublishedBy(actor);
        release.setPublishedAt(TasteComposition.now());
        return response(repository.saveAndFlush(release));
    }

    @Transactional
    public Response activate(String version, long expectedVersion, Activate request) {
        UUID actor = TasteAccess.manage();
        writeLock.acquire();
        TasteTaxonomy target = get(version);
        TasteFailure.matchVersion(target, expectedVersion);
        TasteManifest.Release verified = published(target);
        if (!verified.digest().equals(request.verifiedManifestSha256())) {
            throw TasteFailure.conflict("Manifest đích không khớp bản đã kiểm chứng");
        }
        TasteTaxonomy current = repository.findByCurrentTrue().orElse(null);
        String currentDigest = current == null ? null : current.getManifestSha256();
        if (!Objects.equals(currentDigest, request.expectedCurrentManifestSha256())) {
            throw TasteFailure.conflict("Taxonomy hiện hành đã thay đổi");
        }
        if (target.isCurrent()) {
            return response(target);
        }

        var targetSources = ancestry(verified);
        if (current == null) {
            if (!targetSources.isEmpty()) {
                throw TasteFailure.conflict("Kích hoạt taxonomy nguồn trước khi mở rộng");
            }
        } else {
            TasteManifest.Release currentRelease = published(current);
            boolean forward = verified.compatibility() != null
                    && current.getVersionCode().equals(verified.compatibility().sourceVersionCode());
            boolean rollback = ancestry(currentRelease).containsKey(target.getVersionCode());
            if (!forward && !rollback) {
                throw TasteFailure.conflict("Chỉ có thể kích hoạt release mở rộng trực tiếp hoặc quay về nguồn còn PUBLISHED");
            }
            current.setCurrent(false);
            current.stamp(actor, TasteComposition.now());
            repository.flush();
        }
        target.setCurrent(true);
        target.stamp(actor, TasteComposition.now());
        return response(repository.saveAndFlush(target));
    }

    @Transactional
    public Response retire(String version, long expectedVersion) {
        UUID actor = TasteAccess.manage();
        writeLock.acquire();
        TasteTaxonomy release = get(version);
        TasteFailure.matchVersion(release, expectedVersion);
        if (release.getState() != TasteTaxonomy.State.PUBLISHED) {
            throw TasteFailure.conflict("Chỉ release đã publish có thể retire");
        }
        if (release.isCurrent() || repository.findByStateOrderByVersionCode(TasteTaxonomy.State.PUBLISHED, Pageable.unpaged())
                .stream().anyMatch(other -> other.getManifest().path("schemaVersion").asInt() == 2
                        && version.equals(other.getManifest().path("compatibility").path("sourceVersionCode").textValue()))) {
            throw TasteFailure.conflict("Taxonomy hiện hành hoặc đang được release khác tham chiếu chưa thể retire");
        }
        release.setCurrent(false);
        release.setState(TasteTaxonomy.State.RETIRED);
        release.stamp(actor, TasteComposition.now());
        return response(repository.saveAndFlush(release));
    }

    private Map<String, TasteManifestV2.Reference> ancestry(TasteManifest.Release target) {
        String targetVersion = target.manifest().path("versionCode").textValue();
        Map<String, TasteManifestV2.Reference> visited = new LinkedHashMap<>();
        TasteManifest.Release child = target;
        while (child.compatibility() != null) {
            var reference = child.compatibility();
            if (targetVersion.equals(reference.sourceVersionCode())
                    || visited.putIfAbsent(reference.sourceVersionCode(), reference) != null) {
                throw TasteFailure.conflict("Chuỗi tương thích taxonomy chứa vòng lặp");
            }
            TasteManifest.Release source = published(get(reference.sourceVersionCode()));
            if (!source.digest().equals(reference.sourceManifestSha256())
                    || !TasteManifestV2.isAdditive(source.manifest(), child.manifest())
                    || !keepsNotApplicable(source, child)) {
                throw TasteFailure.conflict("Thay đổi taxonomy không bảo toàn hợp đồng của release nguồn");
            }
            child = source;
        }
        return visited;
    }

    private boolean keepsNotApplicable(TasteManifest.Release source, TasteManifest.Release child) {
        return source.attributes().entrySet().stream().allMatch(entry ->
                entry.getValue().allowsNotApplicable() == child.attributes().get(entry.getKey()).allowsNotApplicable());
    }

    private TasteManifest.Release published(TasteTaxonomy release) {
        if (release.getState() != TasteTaxonomy.State.PUBLISHED) {
            throw TasteFailure.conflict("Cần dùng release taxonomy PUBLISHED");
        }
        TasteManifest.Release normalized = manifests.normalize(release.getVersionCode(), release.getManifest(), true);
        if (!normalized.digest().equals(release.getManifestSha256())) {
            throw TasteFailure.conflict("Mã kiểm tra của release taxonomy không khớp");
        }
        return normalized;
    }

    private TasteTaxonomy editable(String version, long expectedVersion) {
        TasteTaxonomy release = get(version);
        TasteFailure.matchVersion(release, expectedVersion);
        if (release.getState() != TasteTaxonomy.State.DRAFT) {
            throw TasteFailure.conflict("Chỉ được sửa manifest ở trạng thái DRAFT");
        }
        return release;
    }

    private TasteTaxonomy get(String version) {
        return repository.findByVersionCode(version).orElseThrow(TasteTaxonomyService::missing);
    }

    private static TasteFailure missing() {
        return TasteFailure.notFound("TASTE_TAXONOMY_NOT_FOUND", "Không tìm thấy phiên bản bộ tiêu chí khẩu vị");
    }

    private void apply(TasteTaxonomy release, TasteManifest.Release document, UUID actor) {
        release.setManifest(document.manifest());
        release.setManifestSha256(document.digest());
        release.stamp(actor, TasteComposition.now());
    }

    private Response response(TasteTaxonomy release) {
        return response(release, List.of());
    }

    private Response response(TasteTaxonomy release, List<TasteManifestV2.Reference> compatibleSources) {
        return new Response(release.getId(), release.getVersionCode(), release.getManifestSha256(),
                release.getState(), release.getManifest(), release.isCurrent(), release.getRowVersion(),
                release.getPublishedBy(), TasteRecord.utc(release.getPublishedAt()), compatibleSources);
    }

    record Create(@NotNull @Pattern(regexp = TasteDocuments.VERSION_CODE) String versionCode, @NotNull JsonNode manifest) {
    }

    record Update(@NotNull JsonNode manifest) {
    }

    record Activate(@Pattern(regexp = "[0-9a-f]{64}") String expectedCurrentManifestSha256,
                    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String verifiedManifestSha256) {
    }

    record Response(UUID id, String versionCode, String manifestSha256, TasteTaxonomy.State state, JsonNode manifest,
                    boolean current, long rowVersion, UUID publishedBy, Instant publishedAt,
                    List<TasteManifestV2.Reference> compatibleSources) {
    }
}
