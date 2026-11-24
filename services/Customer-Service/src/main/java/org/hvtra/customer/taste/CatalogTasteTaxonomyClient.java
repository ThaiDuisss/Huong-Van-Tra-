package org.hvtra.customer.taste;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Request;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.TimeUnit;

@FeignClient(name = "catalog-service", contextId = "customerTasteTaxonomyClient",
        path = "/api/catalog/taste-taxonomies", configuration = CatalogTasteTaxonomyClient.Configuration.class)
public interface CatalogTasteTaxonomyClient {
    @GetMapping("/{versionCode}")
    Release getRelease(@PathVariable("versionCode") String version);

    record Release(String versionCode, String manifestSha256, String state, JsonNode manifest) {
    }

    class Configuration {
        @Bean
        Request.Options tasteTaxonomyRequestOptions() {
            return new Request.Options(2, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, false);
        }
    }
}
