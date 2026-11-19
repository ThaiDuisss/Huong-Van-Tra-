package org.hvtra.catalog.taste;

import jakarta.servlet.http.HttpServletRequest;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;

@Component
class TasteSnapshotAuth {
    static final String PATH = "/internal/taste-catalog/snapshot";
    static final String SIGNATURE = "X-Taste-Snapshot-Signature";
    private static final String ACTOR = "service:recommendation-service";
    private static final String DOMAIN = "hvtra-catalog-taste-v1";
    private final byte[] secret;
    private final boolean enabled;

    TasteSnapshotAuth(@Value("${TASTE_CATALOG_SYNC_SECRET:${taste.catalog.sync-secret:}}") String configured,
                      @Value("${security.internal.header-secret}") String commonSecret) {
        secret = configured.getBytes(StandardCharsets.UTF_8);
        enabled = !configured.isBlank() && secret.length >= 32 && !configured.equals(commonSecret);
    }

    String authenticate(HttpServletRequest request) {
        var principal = SecurityUtils.currentUser();
        if (!ACTOR.equals(principal.userId()) || !principal.roles().contains("SYSTEM")) {
            throw denied();
        }
        if (!enabled) {
            throw TasteSnapshotFailure.unavailable();
        }
        String timestamp = singleHeader(request, "X-Taste-Snapshot-Timestamp");
        String nonce = singleHeader(request, "X-Taste-Snapshot-Nonce");
        String signature = singleHeader(request, SIGNATURE);
        if (!"GET".equals(request.getMethod()) || !PATH.equals(request.getRequestURI())
                || request.getQueryString() != null || !timestamp.matches("[0-9]{1,12}")
                || !nonce.matches("[0-9a-f]{32}") || !signature.matches("[0-9a-f]{64}")) {
            throw denied();
        }
        long delta = Instant.now().getEpochSecond() - Long.parseLong(timestamp);
        if (delta < -30 || delta > 30 || !MessageDigest.isEqual(HexFormat.of().parseHex(signature),
                sign("request", ACTOR, "GET", PATH, timestamp, nonce))) {
            throw denied();
        }
        return nonce;
    }

    String signResponse(String nonce, TasteSnapshotService.Snapshot snapshot) {
        return HexFormat.of().formatHex(sign("response", nonce, snapshot.snapshotId(),
                snapshot.sourceReadAt(), snapshot.payloadSha256()));
    }

    private byte[] sign(String... fields) {
        String input = DOMAIN + "\n" + String.join("\n", fields);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Taste snapshot authentication unavailable", failure);
        }
    }

    private String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1) {
            throw denied();
        }
        return values.getFirst();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Taste snapshot caller not authorized");
    }
}
