package org.hvtra.catalog.client.gateway;

import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration;
import org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.loadbalancer.FeignLoadBalancerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registers the real OpenFeign proxies (no request is sent) to prove the bean wiring the decorator
 * relies on: {@code FileClient} is declared {@code primary = false} in Common-Service, the Feign proxy
 * is reachable through the {@code file-serviceFeignClient} qualifier, and the decorator wins every
 * plain {@code FileClient} injection.
 */
class FileClientWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    LoadBalancerAutoConfiguration.class,
                    BlockingLoadBalancerClientAutoConfiguration.class,
                    FeignAutoConfiguration.class,
                    FeignLoadBalancerAutoConfiguration.class
            ))
            .withPropertyValues("spring.cloud.discovery.client.simple.instances.file-service[0].uri=http://localhost:1")
            .withUserConfiguration(FeignWiringConfig.class);

    @Test
    void decoratorIsPrimaryAndWrapsTheFeignProxy() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(FileClient.class)).hasSize(2);

            FileClient injected = context.getBean(FileClient.class);
            FileClient feignProxy = context.getBean(ResilientFileClient.FEIGN_DELEGATE_QUALIFIER, FileClient.class);

            assertThat(AopUtils.getTargetClass(injected)).isEqualTo(ResilientFileClient.class);
            assertThat(Proxy.isProxyClass(feignProxy.getClass())).isTrue();
            assertThat(injected).hasFieldOrPropertyWithValue("delegate", feignProxy);

            ObjectProvider<FileClient> provider = context.getBeanProvider(FileClient.class);
            assertThat(provider.getObject()).isSameAs(injected);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = {FileClient.class, InventoryClient.class})
    @Import(ResilientFileClient.class)
    static class FeignWiringConfig {
    }
}
