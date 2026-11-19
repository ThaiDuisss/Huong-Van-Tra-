package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        return response(repository.findByCurrentTrue().orElseThrow(TasteTaxonomyService::missing));
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
        repository.findByCurrentTrue().ifPresent(current -> {
            current.setCurrent(false);
            current.stamp(actor, TasteComposition.now());
            repository.flush();
        });
        apply(release, document, actor);
        release.setState(TasteTaxonomy.State.PUBLISHED);
        release.setCurrent(true);
        release.setPublishedBy(actor);
        release.setPublishedAt(TasteComposition.now());
        return response(repository.saveAndFlush(release));
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
        release.setCurrent(false);
        release.setState(TasteTaxonomy.State.RETIRED);
        release.stamp(actor, TasteComposition.now());
        return response(repository.saveAndFlush(release));
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
        return new Response(release.getId(), release.getVersionCode(), release.getManifestSha256(),
                release.getState(), release.getManifest(), release.isCurrent(), release.getRowVersion(),
                release.getPublishedBy(), TasteRecord.utc(release.getPublishedAt()));
    }

    record Create(@NotNull @Pattern(regexp = TasteDocuments.VERSION_CODE) String versionCode, @NotNull JsonNode manifest) {
    }

    record Update(@NotNull JsonNode manifest) {
    }

    record Response(UUID id, String versionCode, String manifestSha256, TasteTaxonomy.State state, JsonNode manifest,
                    boolean current, long rowVersion, UUID publishedBy, Instant publishedAt) {
    }
}
