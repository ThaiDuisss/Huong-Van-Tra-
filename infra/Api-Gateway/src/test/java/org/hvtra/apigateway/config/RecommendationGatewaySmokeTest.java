package org.hvtra.apigateway.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.hvtra.apigateway.filter.InternalUserContextRelayFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "recommendation.smoke.python", matches = ".+")
@SpringJUnitConfig(RecommendationGatewaySmokeTest.TestConfig.class)
@TestPropertySource(properties = {"security.internal.header-secret=isolated-recommendation-context-secret-32",
        "security.recommendation.customer-id-claim=customer_id"})
class RecommendationGatewaySmokeTest {
    private static final String SECRET = "isolated-recommendation-context-secret-32";
    private static final String USER = "11111111-1111-4111-8111-111111111111";
    private static final String CUSTOMER = "33333333-3333-4333-8333-333333333333";
    private static final String OTHER = "44444444-4444-4444-8444-444444444444";
    private static final String ISSUER = "https://isolated.invalid";
    private static RSAKey key;
    private static Process python;
    private static int port;
    @Autowired ApplicationContext context;

    static RSAKey key() {
        if (key == null) {
            try {
                var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
                key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                        .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("fixture").build();
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        return key;
    }

    @BeforeAll static void startPythonFixture() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path service = Path.of(System.getProperty("recommendation.smoke.service"));
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("recommendation.smoke.python"), "-B",
                "tests/gateway_smoke_server.py", Integer.toString(port));
        builder.directory(service.toFile());
        builder.environment().putAll(Map.of("MYSQL_HOST", "127.0.0.1", "MYSQL_PORT", "9", "MYSQL_USER", "fixture",
                "MYSQL_PASSWORD", "fixture", "MYSQL_DATABASE", "fixture", "INTERNAL_HEADER_SECRET", SECRET,
                "APP_ENVIRONMENT", "test", "INTERNAL_REPLAY_BACKEND", "memory", "KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9"));
        builder.environment().put("CATALOG_SERVICE_URL", "http://127.0.0.1:9");
        builder.environment().put("FILE_SERVICE_URL", "http://127.0.0.1:9");
        builder.redirectErrorStream(true);
        builder.redirectOutput(Path.of(System.getProperty("recommendation.smoke.log")).toFile());
        python = builder.start();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        for (int i = 0; i < 100; i++) {
            try {
                if (http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health"))
                        .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
            } catch (Exception ignored) { }
            if (!python.isAlive()) fail("Isolated Python fixture did not start; see fixture log");
            Thread.sleep(100);
        }
        python.destroy();
        fail("Isolated Python fixture timeout");
    }

    @AfterAll static void stopPythonFixture() throws Exception {
        if (python != null) { python.destroy(); if (!python.waitFor(5, TimeUnit.SECONDS)) python.destroyForcibly(); }
    }

    String jwt(Instant expiry, String customer) {
        return jwt(expiry, customer, "CUSTOMER");
    }

    String jwt(Instant expiry, String customer, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key())));
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(USER).audience(List.of("fixture-pos"))
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry)
                .claim("realm_access", Map.of("roles", List.of(role))).claim("customer_id", customer).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    WebTestClient client() { return WebTestClient.bindToApplicationContext(context).build(); }

    @Test void validJwtToPythonDiscoveryAndRepeatSmoke() {
        String token = jwt(Instant.now().plusSeconds(300), CUSTOMER);
        for (String mode : List.of("DISCOVERY", "REPEAT")) {
            client().get().uri("/api/recommendation/recommendations/"+CUSTOMER+"?mode="+mode+"&limit=5")
                    .headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.mode").isEqualTo(mode).jsonPath("$.source").isEqualTo("HYBRID")
                    .jsonPath("$.customerId").isEqualTo(CUSTOMER);
        }
    }

    @Test void missingInvalidAndExpiredJwtRejected() {
        String path = "/api/recommendation/recommendations/"+CUSTOMER;
        client().get().uri(path).exchange().expectStatus().isUnauthorized();
        client().get().uri(path).headers(h -> h.setBearerAuth("not-a-jwt")).exchange().expectStatus().isUnauthorized();
        String valid = jwt(Instant.now().plusSeconds(300), CUSTOMER);
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        client().get().uri(path).headers(h -> h.setBearerAuth(String.join(".", parts)))
                .exchange().expectStatus().isUnauthorized();
        client().get().uri(path).headers(h -> h.setBearerAuth(jwt(Instant.now().minusSeconds(120), CUSTOMER)))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test void forgedHeadersCannotOverrideMappedIdentityOrCrossCustomerScope() {
        String token = jwt(Instant.now().plusSeconds(300), CUSTOMER);
        client().get().uri("/api/recommendation/recommendations/"+OTHER+"?mode=REPEAT")
                .headers(h -> { h.setBearerAuth(token); h.set("X-Customer-ID", OTHER); h.set("X-User_Roles", "ADMIN");
                    h.set("X-Context-Signature", "forged"); h.set("X-Internal-Timestamp", "1"); })
                .exchange().expectStatus().isForbidden();
    }

    @Test void syncIsDeniedAtGatewayEvenWithValidJwt() {
        String token = jwt(Instant.now().plusSeconds(300), CUSTOMER);
        for (String path : List.of("/internal/catalog/sync", "/api/recommendation/internal/catalog/sync")) {
            client().post().uri(path).headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isForbidden();
        }
    }

    @Test void missingIdentityMapperIsFailClosedAndConflictingParametersPreserve422() {
        String token = jwt(Instant.now().plusSeconds(300), "");
        client().get().uri("/api/recommendation/recommendations/"+CUSTOMER)
                .headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isEqualTo(503);
        client().get().uri("/api/recommendation/recommendations/"+CUSTOMER+"?mode=REPEAT&exclude_purchased=false")
                .headers(h -> h.setBearerAuth(jwt(Instant.now().plusSeconds(300), CUSTOMER)))
                .exchange().expectStatus().isEqualTo(422);
    }

    @Test void aiReportUsesLegacySignedContextWithStaffAuthorization() {
        String path = "/api/recommendation/ai-reports";
        client().get().uri(path).headers(h -> h.setBearerAuth(jwt(Instant.now().plusSeconds(300), "", "SALE_STAFF")))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.content").isArray();
        client().get().uri(path).headers(h -> {
            h.setBearerAuth(jwt(Instant.now().plusSeconds(300), CUSTOMER));
            h.set("X-User_Roles", "ADMIN"); h.set("X-User-ID", OTHER); h.set("X-Signature", "forged");
        }).exchange().expectStatus().isForbidden();
    }

    @Test void aiReportRejectsMissingExpiredAndForgedJwtAndHidesFileInternalApis() {
        String path = "/api/recommendation/ai-reports";
        client().get().uri(path).exchange().expectStatus().isUnauthorized();
        client().get().uri(path).headers(h -> h.setBearerAuth("invalid-jwt")).exchange().expectStatus().isUnauthorized();
        client().get().uri(path).headers(h -> h.setBearerAuth(jwt(Instant.now().minusSeconds(120), "", "SALE_STAFF")))
                .exchange().expectStatus().isUnauthorized();
        client().post().uri("/internal/file/ai-reports/metadata")
                .headers(h -> h.setBearerAuth(jwt(Instant.now().plusSeconds(300), "", "SALE_STAFF")))
                .exchange().expectStatus().isForbidden();
    }

    @Configuration @EnableWebFlux @Import(GatewaySecurityConfig.class)
    static class TestConfig {
        @Bean ReactiveJwtDecoder decoder() throws Exception {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(key().toRSAPublicKey()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                    jwt -> jwt.getAudience().contains("fixture-pos") ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
            return decoder;
        }
        @Bean InternalUserContextRelayFilter relay() {
            var filter = new InternalUserContextRelayFilter();
            ReflectionTestUtils.setField(filter, "headerSecret", SECRET);
            ReflectionTestUtils.setField(filter, "customerIdClaim", "customer_id");
            return filter;
        }
        @Bean RouterFunction<ServerResponse> routes(InternalUserContextRelayFilter relay) {
            return RouterFunctions.route().GET("/api/recommendation/recommendations/{id}", request -> {
                AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
                return relay.filter(request.exchange(), next -> { forwarded.set(next); return Mono.empty(); })
                        .then(Mono.defer(() -> WebClient.create().get()
                                .uri(URI.create("http://127.0.0.1:"+port+"/recommendations/"+request.pathVariable("id")
                                        +(request.uri().getRawQuery()==null ? "" : "?"+request.uri().getRawQuery())))
                                .headers(h -> h.addAll(forwarded.get().getRequest().getHeaders()))
                                .exchangeToMono(response -> response.bodyToMono(String.class).flatMap(body ->
                                        ServerResponse.status(response.statusCode()).contentType(MediaType.APPLICATION_JSON).bodyValue(body)))));
            }).GET("/api/recommendation/ai-reports", request -> {
                AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
                return relay.filter(request.exchange(), next -> { forwarded.set(next); return Mono.empty(); })
                        .then(Mono.defer(() -> WebClient.create().get()
                                .uri(URI.create("http://127.0.0.1:"+port+request.path()))
                                .headers(h -> h.addAll(forwarded.get().getRequest().getHeaders()))
                                .exchangeToMono(response -> response.bodyToMono(String.class).flatMap(body ->
                                        ServerResponse.status(response.statusCode()).contentType(MediaType.APPLICATION_JSON).bodyValue(body)))));
            }).build();
        }
    }
}
