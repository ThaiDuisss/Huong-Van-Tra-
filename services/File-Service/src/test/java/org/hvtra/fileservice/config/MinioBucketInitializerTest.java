package org.hvtra.fileservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioBucketInitializerTest {
    @Mock
    private MinioClient minio;

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.getBuckets().setPublicBucket("test-public");
        properties.getBuckets().setPrivateBucket("test-private");
        contextRunner = new ApplicationContextRunner()
                .withBean(MinioClient.class, () -> minio)
                .withBean(MinioProperties.class, () -> properties)
                .withUserConfiguration(MinioBucketInitializer.class);
    }

    @Test
    void disabledModeDoesNotInstantiateInitializerOrContactStorage() {
        contextRunner.withPropertyValues("file.storage.initialize-buckets=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(MinioBucketInitializer.class);
            verifyNoInteractions(minio);
        });
    }

    @Test
    void defaultModePreservesPublicPolicyAndLeavesPrivatePolicyUntouched() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MinioBucketInitializer.class);
            verify(minio, times(2)).bucketExists(any(BucketExistsArgs.class));
            verify(minio, never()).makeBucket(any(MakeBucketArgs.class));
            verify(minio).setBucketPolicy(argThat(args -> "test-public".equals(args.bucket())));
            verify(minio, never()).setBucketPolicy(argThat(args -> "test-private".equals(args.bucket())));
        });
    }

    @Test
    void enabledModeCreatesMissingBuckets() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        contextRunner.withPropertyValues("file.storage.initialize-buckets=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MinioBucketInitializer.class);
            verify(minio).makeBucket(argThat(args -> "test-public".equals(args.bucket())));
            verify(minio).makeBucket(argThat(args -> "test-private".equals(args.bucket())));
            verify(minio).setBucketPolicy(argThat(args -> "test-public".equals(args.bucket())));
        });
    }

    @Test
    void storageReadFailureFailsStartupBeforeAnyStorageWrite() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenThrow(new IllegalStateException("Storage unavailable"));
        contextRunner.withPropertyValues("file.storage.initialize-buckets=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
            verify(minio, never()).makeBucket(any(MakeBucketArgs.class));
            verify(minio, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
        });
    }
}
