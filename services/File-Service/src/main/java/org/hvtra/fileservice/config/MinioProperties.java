package org.hvtra.fileservice.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;
    private String publicEndpoint;

    private String accessKey;
    private String secretKey;

    private Buckets buckets = new Buckets();

    private Integer presignedUploadExpirySeconds = 300;
    private Integer presignedDownloadExpirySeconds = 300;

    @Getter
    @Setter
    public static class Buckets {
        private String publicBucket;
        private String privateBucket;
    }
}
