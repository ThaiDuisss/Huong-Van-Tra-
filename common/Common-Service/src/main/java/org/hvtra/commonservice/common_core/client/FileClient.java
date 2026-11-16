package org.hvtra.commonservice.common_core.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * {@code primary = false} lets a consuming service register its own {@code @Primary} decorator of this
 * interface (for example a Resilience4j-protected one) without a "more than one primary bean" conflict.
 * A service that declares no such decorator still gets this Feign proxy: it is the only candidate.
 */
@FeignClient(name = "file-service", primary = false)
public interface FileClient {

    @PutMapping("/api/file/attach")
    void attachFile(@RequestBody FileAttach fileAttach);

    @Builder
    record FileAttach(
            UUID fieldId,
            UUID refId
    ) {
    }
}
