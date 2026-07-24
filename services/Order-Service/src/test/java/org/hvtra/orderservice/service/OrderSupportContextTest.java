package org.hvtra.orderservice.service;

import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(OrderSupportContextTest.Config.class)
class OrderSupportContextTest {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderValidator orderValidator;

    @Autowired
    private OrderAmountCalculator orderAmountCalculator;

    @Test
    void extractedSupportBeansLoadWithoutMissingOrCircularDependencies() {
        assertNotNull(orderMapper);
        assertNotNull(orderValidator);
        assertNotNull(orderAmountCalculator);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            OrderMapper.class,
            OrderItemMapper.class,
            OrderValidator.class,
            OrderAmountCalculator.class,
            OrderCodeGenerator.class,
            OrderDateTimeHelper.class
    })
    static class Config {
    }
}
