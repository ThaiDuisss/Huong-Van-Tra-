package org.hvtra.commonservice.common_core.config;

import feign.RequestTemplate;
import jakarta.servlet.FilterChain;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeignConfigInternalAuthenticationTest {

    @Test
    void kafkaThreadSignsInternalFeignRequestAcceptedByOrderFilter() throws Exception {
        FeignConfig config = new FeignConfig();
        ReflectionTestUtils.setField(config, "headerSecret", "test-internal-secret");
        ReflectionTestUtils.setField(config, "applicationName", "Inventory-Service");
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate template = new RequestTemplate();
        config.requestInterceptor().apply(template);

        assertEquals("service:Inventory-Service", header(template, HeaderConstants.HEADER_USER_ID));
        assertEquals("SYSTEM", header(template, HeaderConstants.HEADER_USER_ROLES));
        assertEquals("Bearer service:Inventory-Service", header(template, HeaderConstants.HEADER_AUTHOR));
        assertNotNull(header(template, HeaderConstants.HEADER_CORRELATION_ID));
        assertNotNull(header(template, HeaderConstants.HEADER_SIGNATURE));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/returns/123");
        template.headers().forEach((name, values) -> request.addHeader(name, values.iterator().next()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new InternalHeaderAuthenticationFilter("test-internal-secret", "order-service")
                .doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    private String header(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }
}
