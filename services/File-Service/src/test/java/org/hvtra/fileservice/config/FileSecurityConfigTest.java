package org.hvtra.fileservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityConfigTest {
    @Test void missingWeakAndDevelopmentSecretsAreRejected() {
        FileSecurityConfig config = new FileSecurityConfig();
        for (String secret : new String[]{null, "", "short", "dev-internal-header-secret"}) {
            ReflectionTestUtils.setField(config, "configuredSecret", secret);
            assertThrows(IllegalStateException.class, config::validateSecret);
        }
        ReflectionTestUtils.setField(config, "configuredSecret", "isolated-ai-report-context-secret-32");
        assertDoesNotThrow(config::validateSecret);
    }
}
