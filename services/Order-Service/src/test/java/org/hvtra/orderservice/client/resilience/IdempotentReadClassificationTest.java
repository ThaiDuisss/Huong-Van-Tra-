package org.hvtra.orderservice.client.resilience;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.CustomBundleCatalogClient;
import org.hvtra.orderservice.client.CustomBundleInventoryClient;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which Feign methods may be retried. Adding a method leaves it non-retryable; making one
 * retryable means changing this list on purpose, after checking the downstream endpoint is read-only.
 */
class IdempotentReadClassificationTest {

    private static final List<Class<?>> CLIENTS = List.of(
            CatalogClient.class,
            CustomBundleCatalogClient.class,
            CustomBundleInventoryClient.class,
            CustomerClient.class,
            InventoryClient.class,
            PaymentClient.class
    );

    @Test
    void onlyReadOnlyLookupsAreRetryable() {
        assertThat(methods(true)).containsExactlyInAnyOrder(
                "CatalogClient#findAll",
                "CatalogClient#findById",
                "CatalogClient#getProductVariantPosView",
                "CatalogClient#getBomItemsByVariantId",
                "CatalogClient#getVariantStockConfig",
                "CustomBundleCatalogClient#getCustomBundleVariant",
                "CustomBundleCatalogClient#getIngredientsByIds",
                "CustomBundleInventoryClient#checkAvailability",
                "CustomBundleInventoryClient#findPackingsByOrder",
                "CustomerClient#lookupCustomers",
                "CustomerClient#getCustomerById",
                "InventoryClient#getStocksByVariantIds",
                "InventoryClient#estimateAvailability",
                "InventoryClient#getCustomerReturnQcSummary",
                "InventoryClient#getCustomerReturnQcSummaries",
                "InventoryClient#getCustomerReturnQcDispositionReport",
                "InventoryClient#getReturnDispositionStatus",
                "PaymentClient#getPaymentByOrderId",
                "PaymentClient#lookupRefundSummaries"
        );
    }

    @Test
    void writesAreNeverRetryable() {
        assertThat(methods(false)).containsExactlyInAnyOrder(
                "CatalogClient#decrementSellableQuantity",
                "CatalogClient#restoreSellableQuantity",
                "CustomBundleInventoryClient#createPacking",
                "CustomBundleInventoryClient#cancelPackingsByOrder",
                "InventoryClient#applyReservation",
                "InventoryClient#releaseItem",
                "InventoryClient#releaseOrder",
                "InventoryClient#reserveMaterials",
                "InventoryClient#consumeMaterials",
                "InventoryClient#releaseMaterials",
                "InventoryClient#fulfillSale",
                "InventoryClient#prepareBackorder",
                "InventoryClient#createProductionRequiredAlert",
                "PaymentClient#checkout",
                "PaymentClient#cancelPendingPayment",
                "PaymentClient#confirmManualPayment",
                "PaymentClient#refundOrderReturn"
        );
    }

    @Test
    void everyGetIsRetryableAndNoPutPatchOrDeleteIs() {
        for (Class<?> client : CLIENTS) {
            for (Method method : client.getDeclaredMethods()) {
                RequestMethod verb = AnnotatedElementUtils
                        .findMergedAnnotation(method, RequestMapping.class).method()[0];
                boolean retryable = method.isAnnotationPresent(IdempotentRead.class);
                if (verb == RequestMethod.GET) {
                    assertThat(retryable).as("%s is a GET", method).isTrue();
                }
                if (verb != RequestMethod.GET && verb != RequestMethod.POST) {
                    assertThat(retryable).as("%s is a %s", method, verb).isFalse();
                }
            }
        }
    }

    @Test
    void everyClientBelongsToAConfiguredDownstreamService() {
        for (Class<?> client : CLIENTS) {
            String feignName = client.getAnnotation(FeignClient.class).name();
            assertThat(DownstreamService.fromFeignName(feignName)).as(client.getSimpleName()).isPresent();
        }
    }

    private static Set<String> methods(boolean retryable) {
        Set<String> names = new TreeSet<>();
        for (Class<?> client : CLIENTS) {
            Arrays.stream(client.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(IdempotentRead.class) == retryable)
                    .forEach(method -> names.add(client.getSimpleName() + "#" + method.getName()));
        }
        return names;
    }
}
