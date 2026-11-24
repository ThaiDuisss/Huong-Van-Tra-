package org.hvtra.customer.taste;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class TasteSurveyController {
    private static final String BASE = "/taste-surveys";
    private static final String SESSION = "X-Survey-Session-Id";
    private static final String KEY = "Idempotency-Key";
    private static final Pattern VERSION = Pattern.compile("\"(?:0|[1-9][0-9]{0,18})\"");

    private final TasteSurveyService service;

    @PostMapping(BASE)
    public ResponseEntity<TasteSurveyDto.Response> create(
            @Valid @RequestBody TasteSurveyDto.Create request,
            @RequestHeader(value = KEY, required = false) String key) {
        return created(service.create(request, requiredKey(key)));
    }

    @GetMapping(BASE + "/{surveyId}")
    public ResponseEntity<TasteSurveyDto.Response> detail(
            @PathVariable UUID surveyId,
            @RequestParam(required = false) UUID customerId,
            @RequestHeader(value = SESSION, required = false) UUID sessionId) {
        return ok(service.detail(surveyId, customerId, sessionId));
    }

    @PatchMapping(BASE + "/{surveyId}")
    public ResponseEntity<TasteSurveyDto.Response> update(
            @PathVariable UUID surveyId,
            @RequestHeader(value = SESSION, required = false) UUID sessionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody TasteSurveyDto.UpdateDraft request) {
        return ok(service.update(surveyId, sessionId, version(ifMatch), request));
    }

    @PostMapping(BASE + "/{surveyId}/submit")
    public ResponseEntity<TasteSurveyDto.Response> submit(
            @PathVariable UUID surveyId,
            @RequestHeader(value = SESSION, required = false) UUID sessionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = KEY, required = false) String key) {
        return ok(service.submit(surveyId, sessionId, version(ifMatch), requiredKey(key)));
    }

    @PostMapping(BASE + "/{surveyId}/copy")
    public ResponseEntity<TasteSurveyDto.Response> copy(
            @PathVariable UUID surveyId,
            @Valid @RequestBody TasteSurveyDto.Copy request,
            @RequestHeader(value = KEY, required = false) String key) {
        return created(service.copy(surveyId, request, requiredKey(key)));
    }

    @PostMapping(BASE + "/{surveyId}/link-customer")
    public ResponseEntity<TasteSurveyDto.Response> link(
            @PathVariable UUID surveyId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody TasteSurveyDto.Link request) {
        return ok(service.link(surveyId, version(ifMatch), request));
    }

    @GetMapping("/{customerId:[0-9a-fA-F-]+}/taste-surveys")
    public ResponseEntity<PageResponse<TasteSurveyDto.Response>> history(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.history(customerId, page, size));
    }

    @GetMapping("/{customerId:[0-9a-fA-F-]+}/taste-surveys/latest")
    public ResponseEntity<TasteSurveyDto.Response> latest(@PathVariable UUID customerId) {
        return ok(service.latest(customerId));
    }

    private ResponseEntity<TasteSurveyDto.Response> created(TasteSurveyDto.Response response) {
        String context = response.customerId() == null ? "" : "?customerId=" + response.customerId();
        return result(ResponseEntity.created(URI.create("/api/customer" + BASE + "/" + response.id() + context)), response);
    }

    private ResponseEntity<TasteSurveyDto.Response> ok(TasteSurveyDto.Response response) {
        return result(ResponseEntity.ok(), response);
    }

    private ResponseEntity<TasteSurveyDto.Response> result(
            ResponseEntity.BodyBuilder builder, TasteSurveyDto.Response response) {
        return builder.eTag("\"" + response.rowVersion() + "\"").cacheControl(CacheControl.noStore().cachePrivate())
                .body(response);
    }

    private UUID requiredKey(String key) {
        try {
            UUID id = UUID.fromString(key == null ? "" : key);
            if (id.toString().equalsIgnoreCase(key)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // Report a validation error without echoing the header.
        }
        throw TasteSurveyErrors.invalid("Idempotency-Key phải là UUID hợp lệ");
    }

    private long version(String ifMatch) {
        if (ifMatch == null) {
            throw new CustomerServiceException("TASTE_SURVEY_VERSION_REQUIRED", "Thiếu header If-Match",
                    HttpStatus.PRECONDITION_REQUIRED);
        }
        if (VERSION.matcher(ifMatch).matches()) {
            try {
                return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
            } catch (NumberFormatException ignored) {
                // Reject versions outside the supported signed range.
            }
        }
        throw TasteSurveyErrors.invalid("If-Match phải là ETag phiên bản của khảo sát");
    }
}
