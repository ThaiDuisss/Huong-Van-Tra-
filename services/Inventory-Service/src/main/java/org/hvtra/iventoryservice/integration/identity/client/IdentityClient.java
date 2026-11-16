package org.hvtra.iventoryservice.integration.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Raw Feign proxy. Application code must inject plain {@code IdentityClient}: the {@code @Primary}
 * {@link ResilientIdentityClient} is what gets injected. This bean is non-primary and reachable
 * only through its qualifier.
 */
@FeignClient(name = "identity-service", primary = false, qualifiers = IdentityClient.FEIGN_QUALIFIER)
public interface IdentityClient {

    String FEIGN_QUALIFIER = "identityFeignClient";

    /** Tra cứu tên user theo Keycloak subject (giá trị createdBy/approvedBy... lưu trong DB). */
    @PostMapping("/internal/identity/users/batch")
    List<UserBriefResponse> getUsersBatch(@RequestBody List<String> keycloakSubjects);

    record UserBriefResponse(String keycloakSubject, String fullName, String username, String email) {
    }
}
