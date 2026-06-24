package org.hvtra.fileservice.config;

import lombok.RequiredArgsConstructor;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.service.FileVisibilityPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileBucketResolver {

    private final MinioProperties minioProperties;

    public String resolveBucket(FileVisibility visibility) {
        if (visibility == FileVisibility.PUBLIC) {
            return minioProperties.getBuckets().getPublicBucket();
        }

        return minioProperties.getBuckets().getPrivateBucket();
    }

    public FileVisibility resolveVisibility(FilePurpose purpose) {
        return FileVisibilityPolicy.resolveVisibility(purpose);
    }
}



