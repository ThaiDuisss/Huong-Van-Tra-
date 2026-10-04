package org.hvtra.notificationservice.invoice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InvoiceEmailProperties.class)
public class InvoiceEmailConfig {
}
