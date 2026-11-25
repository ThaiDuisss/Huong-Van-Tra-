package org.hvtra.catalog.taste;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class TasteCatalogController {
    private static final String BASE = "/api/catalog";
    private static final String TAXONOMIES = "/taste-taxonomies";
    private static final String PROFILES = "/taste-profiles";
    private static final String VARIANT_PROFILES = "/product-variants/{variantId}" + PROFILES;

    private final TasteTaxonomyService taxonomy;
    private final VariantTasteService profile;

    @GetMapping(TAXONOMIES + "/current")
    public ResponseEntity<TasteTaxonomyService.Response> currentTaxonomy() {
        var release = taxonomy.current();
        return ok(release, release.rowVersion());
    }

    @GetMapping(TAXONOMIES + "/{versionCode}")
    public ResponseEntity<TasteTaxonomyService.Response> taxonomy(@PathVariable String versionCode) {
        var release = taxonomy.detail(versionCode);
        return ok(release, release.rowVersion());
    }

    @PostMapping(TAXONOMIES)
    public ResponseEntity<TasteTaxonomyService.Response> createTaxonomy(@Valid @RequestBody TasteTaxonomyService.Create body) {
        var release = taxonomy.create(body);
        return respond(ResponseEntity.created(URI.create(BASE + TAXONOMIES + "/" + release.versionCode())),
                release, release.rowVersion());
    }

    @PutMapping(TAXONOMIES + "/{versionCode}")
    public ResponseEntity<TasteTaxonomyService.Response> updateTaxonomy(@PathVariable String versionCode,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @Valid @RequestBody TasteTaxonomyService.Update body) {
        var release = taxonomy.update(versionCode, TasteFailure.requireVersion(etag), body.manifest());
        return ok(release, release.rowVersion());
    }

    @PostMapping(TAXONOMIES + "/{versionCode}/publish")
    public ResponseEntity<TasteTaxonomyService.Response> publish(@PathVariable String versionCode,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        var release = taxonomy.publish(versionCode, TasteFailure.requireVersion(etag));
        return ok(release, release.rowVersion());
    }

    @PostMapping(TAXONOMIES + "/{versionCode}/retire")
    public ResponseEntity<TasteTaxonomyService.Response> retireTaxonomy(@PathVariable String versionCode,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        var release = taxonomy.retire(versionCode, TasteFailure.requireVersion(etag));
        return ok(release, release.rowVersion());
    }

    @PostMapping(VARIANT_PROFILES)
    public ResponseEntity<VariantTasteService.Response> createProfile(@PathVariable UUID variantId,
            @Valid @RequestBody VariantTasteService.Draft body) {
        return created(profile.create(variantId, body));
    }

    @PostMapping(PROFILES + "/bulk-draft")
    public ResponseEntity<VariantTasteService.BulkDraftResponse> bulkDraft(
            @Valid @RequestBody VariantTasteService.BulkDraft body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile.bulkDraft(body));
    }

    @GetMapping(VARIANT_PROFILES)
    public ResponseEntity<PageResponse<VariantTasteService.Response>> history(@PathVariable UUID variantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PageResponse.from(profile.history(variantId, page, size)));
    }

    @GetMapping(PROFILES)
    public ResponseEntity<PageResponse<VariantTasteService.VariantProfilesResponse>> listVariantProfiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Set<VariantTasteProfile.State> profileStatuses,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profile.listVariantProfiles(keyword, status, profileStatuses, pageable));
    }

    @GetMapping(PROFILES + "/{profileId}")
    public ResponseEntity<VariantTasteService.Response> profile(@PathVariable UUID profileId) {
        var result = profile.detail(profileId);
        return ok(result, result.rowVersion());
    }

    @PatchMapping(PROFILES + "/{profileId}")
    public ResponseEntity<VariantTasteService.Response> updateProfile(@PathVariable UUID profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @Valid @RequestBody VariantTasteService.Assessment body) {
        var result = profile.update(profileId, TasteFailure.requireVersion(etag), body);
        return ok(result, result.rowVersion());
    }

    @PostMapping(PROFILES + "/{profileId}/approve")
    public ResponseEntity<VariantTasteService.Response> approve(@PathVariable UUID profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        var result = profile.approve(profileId, TasteFailure.requireVersion(etag));
        return ok(result, result.rowVersion());
    }

    @PostMapping(PROFILES + "/{profileId}/revisions")
    public ResponseEntity<VariantTasteService.Response> revise(@PathVariable UUID profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @Valid @RequestBody VariantTasteService.Draft body) {
        return created(profile.revision(profileId, TasteFailure.requireVersion(etag), body));
    }

    @PostMapping(PROFILES + "/{profileId}/retire")
    public ResponseEntity<VariantTasteService.Response> retireProfile(@PathVariable UUID profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        var result = profile.retire(profileId, TasteFailure.requireVersion(etag));
        return ok(result, result.rowVersion());
    }

    private ResponseEntity<VariantTasteService.Response> created(VariantTasteService.Response result) {
        return respond(ResponseEntity.created(URI.create(BASE + PROFILES + "/" + result.id())),
                result, result.rowVersion());
    }

    private <T> ResponseEntity<T> ok(T body, long version) {
        return respond(ResponseEntity.ok(), body, version);
    }

    private <T> ResponseEntity<T> respond(ResponseEntity.BodyBuilder builder, T body, long version) {
        return builder.eTag("\"" + version + "\"").cacheControl(CacheControl.noStore()).body(body);
    }
}
