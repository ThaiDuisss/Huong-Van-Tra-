package org.hvtra.orderservice.service;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.service.support.OrderBackorderSupport;
import org.hvtra.orderservice.service.support.OrderCustomBundleSupport;
import org.hvtra.orderservice.service.impl.OrderBackorderServiceImpl;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.impl.OrderCancellationServiceImpl;
import org.hvtra.orderservice.service.impl.OrderCheckoutServiceImpl;
import org.hvtra.orderservice.service.impl.OrderDraftServiceImpl;
import org.hvtra.orderservice.service.impl.OrderExportServiceImpl;
import org.hvtra.orderservice.service.impl.OrderPaymentEventServiceImpl;
import org.hvtra.orderservice.service.impl.OrderPaymentServiceImpl;
import org.hvtra.orderservice.service.impl.OrderQueryServiceImpl;
import org.hvtra.orderservice.service.impl.OrderStatusManagementServiceImpl;
import org.hvtra.orderservice.service.impl.TodaySalesServiceImpl;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryReader;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.support.ProductSnapshotResolver;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(OrderSupportContextTest.Config.class)
class OrderSupportContextTest {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderValidator orderValidator;

    @Autowired
    private OrderAmountCalculator orderAmountCalculator;

    @Autowired
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Autowired
    private CustomerSnapshotResolver customerSnapshotResolver;

    @Autowired
    private ProductSnapshotResolver productSnapshotResolver;

    @Autowired
    private OrderQueryCriteriaBuilder orderQueryCriteriaBuilder;

    @Autowired
    private OrderStatusHistoryReader orderStatusHistoryReader;

    @Autowired
    private OrderResponseAssembler orderResponseAssembler;

    @Autowired
    private OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    @Autowired
    private OrderCheckoutService orderCheckoutService;

    @Autowired
    private OrderPaymentService orderPaymentService;

    @Autowired
    private OrderPaymentEventService orderPaymentEventService;

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderExportService orderExportService;

    @Autowired
    private TodaySalesService todaySalesService;

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private OrderCancellationService orderCancellationService;

    @Autowired
    private OrderStatusManagementService orderStatusManagementService;

    @Test
    void extractedSupportBeansLoadWithoutMissingOrCircularDependencies() {
        assertNotNull(orderMapper);
        assertNotNull(orderValidator);
        assertNotNull(orderAmountCalculator);
        assertNotNull(orderStatusHistoryWriter);
        assertNotNull(customerSnapshotResolver);
        assertNotNull(productSnapshotResolver);
        assertNotNull(orderQueryCriteriaBuilder);
        assertNotNull(orderStatusHistoryReader);
        assertNotNull(orderResponseAssembler);
        assertNotNull(orderCheckoutPaymentProcessor);
        assertNotNull(orderCheckoutService);
        assertNotNull(orderPaymentService);
        assertNotNull(orderPaymentEventService);
        assertNotNull(orderQueryService);
        assertNotNull(orderExportService);
        assertNotNull(todaySalesService);
        assertNotNull(orderDraftService);
        assertNotNull(orderCancellationService);
        assertNotNull(orderStatusManagementService);
        assertTrue(AopUtils.isAopProxy(orderCheckoutService));
        assertTrue(AopUtils.isAopProxy(orderPaymentService));
        assertTrue(AopUtils.isAopProxy(orderPaymentEventService));
        assertTrue(AopUtils.isAopProxy(orderQueryService));
        assertTrue(AopUtils.isAopProxy(orderExportService));
        assertTrue(AopUtils.isAopProxy(todaySalesService));
        assertTrue(AopUtils.isAopProxy(orderDraftService));
        assertTrue(AopUtils.isAopProxy(orderCancellationService));
        assertTrue(AopUtils.isAopProxy(orderStatusManagementService));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({
            OrderMapper.class,
            OrderItemMapper.class,
            OrderValidator.class,
            OrderAmountCalculator.class,
            OrderCodeGenerator.class,
            OrderDateTimeHelper.class,
            OrderStatusHistoryWriter.class,
            CustomerSnapshotResolver.class,
            ProductSnapshotResolver.class,
            OrderQueryCriteriaBuilder.class,
            OrderStatusHistoryReader.class,
            OrderResponseAssembler.class,
            OrderCheckoutPaymentProcessor.class,
            OrderBackorderSupport.class,
            OrderBackorderServiceImpl.class,
            OrderCheckoutServiceImpl.class,
            OrderPaymentServiceImpl.class,
            OrderPaymentEventServiceImpl.class,
            OrderQueryServiceImpl.class,
            OrderExportServiceImpl.class,
            TodaySalesServiceImpl.class,
            OrderDraftServiceImpl.class,
            OrderCancellationServiceImpl.class,
            OrderStatusManagementServiceImpl.class
    })
    static class Config {

        @Bean
        OrderCustomerService orderCustomerService() {
            return mock(OrderCustomerService.class);
        }

        @Bean
        CatalogClient catalogClient() {
            return mock(CatalogClient.class);
        }

        @Bean
        OrderStatusHistoryRepository orderStatusHistoryRepository() {
            return mock(OrderStatusHistoryRepository.class);
        }

        @Bean
        OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean
        OrderDiscountRepository orderDiscountRepository() {
            return mock(OrderDiscountRepository.class);
        }

        @Bean
        InventoryClient inventoryClient() {
            return mock(InventoryClient.class);
        }

        @Bean
        OrderCustomBundleSupport orderCustomBundleSupport() {
            return mock(OrderCustomBundleSupport.class);
        }

        @Bean
        PaymentClient paymentClient() {
            return mock(PaymentClient.class);
        }

        @Bean
        OrderInventoryReservationService orderInventoryReservationService() {
            return mock(OrderInventoryReservationService.class);
        }

        @Bean
        OrderPaymentSagaService orderPaymentSagaService() {
            return mock(OrderPaymentSagaService.class);
        }

        @Bean
        OrderOutboxPublisher orderOutboxPublisher() {
            return mock(OrderOutboxPublisher.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
