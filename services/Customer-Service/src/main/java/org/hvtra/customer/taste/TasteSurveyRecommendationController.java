package org.hvtra.customer.taste;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Hidden
@RestController
class TasteSurveyRecommendationController {
    private static final String PATH_PREFIX = "/internal/taste-surveys/";
    private static final String PATH_SUFFIX = "/latest";
    private static final String ACTOR = "service:recommendation-service";
    private static final String SIGNATURE = "X-Taste-Read-Signature";
    private static final String TIMESTAMP = "X-Taste-Read-Timestamp";
    private static final String DOMAIN = "hvtra-customer-taste-read-v1";

    private final TasteSurveyService surveys;
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final boolean enabled;

    TasteSurveyRecommendationController(
            TasteSurveyService surveys, ObjectMapper mapper,
            @Value("${CUSTOMER_TASTE_READ_SECRET:}") String configured,
            @Value("${security.internal.header-secret}") String commonSecret) {
        this.surveys = surveys;
        this.mapper = mapper;
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.enabled = secret.length >= 32 && !configured.isBlank() && !configured.equals(commonSecret);
    }

    @GetMapping(PATH_PREFIX + "{customerId}" + PATH_SUFFIX)
    public ResponseEntity<byte[]> latest(@PathVariable UUID customerId, HttpServletRequest request)
            throws JsonProcessingException {
        var principal = SecurityUtils.currentUser();
        if (!ACTOR.equals(principal.userId()) || !Set.of("SYSTEM").equals(principal.roles())) {
            return failure(HttpStatus.FORBIDDEN);
        }
        if (!enabled) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE);
        }
        String path = PATH_PREFIX + customerId + PATH_SUFFIX;
        String nonce = singleHeader(request, HeaderConstants.HEADER_CORRELATION_ID);
        if (!authenticated(request, path, nonce)) {
            return failure(HttpStatus.FORBIDDEN);
        }
        var survey = surveys.latestForRecommendation(customerId);
        HttpStatus status = survey.isPresent() ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        byte[] body = survey.isPresent() ? mapper.writeValueAsBytes(survey.get()) : new byte[0];
        String signature = HexFormat.of().formatHex(sign("response", path, nonce,
                Integer.toString(status.value()), Base64.getEncoder().encodeToString(body)));
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore().cachePrivate())
                .header(SIGNATURE, signature);
        return survey.isPresent() ? response.contentType(MediaType.APPLICATION_JSON).body(body) : response.build();
    }

    private boolean authenticated(HttpServletRequest request, String path, String nonce) {
        String timestamp = singleHeader(request, TIMESTAMP);
        String signature = singleHeader(request, SIGNATURE);
        if (!"GET".equals(request.getMethod()) || !path.equals(request.getRequestURI())
                || request.getQueryString() != null || !timestamp.matches("[0-9]{1,12}")
                || !nonce.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !signature.matches("[0-9a-f]{64}")) {
            return false;
        }
        long delta = Instant.now().getEpochSecond() - Long.parseLong(timestamp);
        return delta >= -30 && delta <= 30 && MessageDigest.isEqual(HexFormat.of().parseHex(signature),
                sign("request", ACTOR, "GET", path, timestamp, nonce));
    }

    private byte[] sign(String... fields) {
        String input = DOMAIN + "\n" + String.join("\n", fields);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Customer taste authentication unavailable", failure);
        }
    }

    private String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : "";
    }

    private ResponseEntity<byte[]> failure(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
