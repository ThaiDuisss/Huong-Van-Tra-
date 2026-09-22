package org.hvtra.commonservice.common_core.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "file-service")
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
