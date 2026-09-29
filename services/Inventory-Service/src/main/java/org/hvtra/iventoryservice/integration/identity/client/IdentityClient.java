package org.hvtra.iventoryservice.integration.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "identity-service")
public interface IdentityClient {

    /** Tra cứu tên user theo Keycloak subject (giá trị createdBy/approvedBy... lưu trong DB). */
    @PostMapping("/internal/identity/users/batch")
    List<UserBriefResponse> getUsersBatch(@RequestBody List<String> keycloakSubjects);

    record UserBriefResponse(String keycloakSubject, String fullName, String username, String email) {
    }
}
