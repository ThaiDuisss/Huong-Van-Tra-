package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.AddDraftBundleItemRequest;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftBundleItemRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderBackorderService;
import org.hvtra.orderservice.service.OrderCustomBundleService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCustomBundleSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCustomBundleServiceImpl implements OrderCustomBundleService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderDateTimeHelper orderDateTimeHelper;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final CustomerSnapshotResolver customerSnapshotResolver;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderCustomBundleSupport bundleSupport;
    private final OrderBackorderService orderBackorderService;

    @Override
    @Transactional
    public OrderResponse addDraftBundleItem(AddDraftBundleItemRequest request) {
        Instant expiresAt = orderDateTimeHelper.nextDraftExpiresAt();
        Order order = request.getOrderId() == null
                ? createDraftOrder(request, expiresAt)
                : getEditableDraftOrder(request.getOrderId());
        order.setExpiresAt(expiresAt);

        OrderCustomBundleSupport.CatalogVariant variant = bundleSupport.customBundleVariant();
        OrderCustomBundleSupport.PricedBundle priced = bundleSupport.price(request.getLines());
        OrderItem item = OrderItem.builder()
                .productVariantId(variant.id())
                .skuSnapshot(variant.sku())
                .productNameSnapshot(variant.productName())
                .variantNameSnapshot(bundleName(request.getBundleName(), variant))
                .quantity(BigDecimal.ONE)
                .originalUnitPrice(priced.unitPrice())
                .actualUnitPrice(priced.unitPrice())
                .discountAmount(BigDecimal.ZERO)
                .note(normalize(request.getNote()))
                .itemType(OrderItemType.CUSTOM_BUNDLE)
                .build();
        item.replaceComponents(priced.components());
        orderAmountCalculator.recalculateLine(item);
        order.addItem(item);

        orderAmountCalculator.recalculateOrderTotals(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateDraftBundleItem(UUID orderId, UUID itemId, UpdateDraftBundleItemRequest request) {
        Order order = getEditableDraftOrder(orderId);
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());
        OrderItem item = order.getItems().stream()
                .filter(line -> line.getId().equals(itemId) && line.isCustomBundle())
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy bundle trong đơn: " + itemId));

        OrderCustomBundleSupport.PricedBundle priced = bundleSupport.price(request.getLines());
        item.replaceComponents(priced.components());
        item.setOriginalUnitPrice(priced.unitPrice());
        item.setActualUnitPrice(priced.unitPrice());
        if (request.getBundleName() != null) {
            item.setVariantNameSnapshot(bundleName(request.getBundleName(), bundleSupport.customBundleVariant()));
        }
        if (request.getNote() != null) {
            item.setNote(normalize(request.getNote()));
        }
        orderAmountCalculator.recalculateLine(item);
        orderAmountCalculator.recalculateOrderTotals(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAwaitingHandover(String keyword, BundlePackingStatus packingStatus, int page, int size) {
        String search = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<Order> awaiting = (root, query, cb) -> {
            query.distinct(true);
            Join<Order, OrderItem> items = root.join("items");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.equal(root.get("status"), OrderStatus.PAYMENT_CONFIRMED));
            predicates.add(cb.equal(items.get("itemType"), OrderItemType.CUSTOM_BUNDLE));
            if (packingStatus != null) {
                predicates.add(cb.equal(items.get("packingStatus"), packingStatus));
            }
            if (search != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), search),
                        cb.like(cb.lower(root.get("customerNameSnapshot")), search),
                        cb.like(root.get("customerPhoneSnapshot"), search),
                        cb.like(cb.lower(items.get("packingCode")), search)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("createdAt")));
        return orderRepository.findAll(awaiting, pageRequest)
                .map(order -> orderMapper.toResponse(order, null, BigDecimal.ZERO, order.getPaymentMethod()));
    }

    @Override
    @Transactional
    public OrderResponse refreshPacking(UUID orderId) {
        Order order = getOrderForUpdate(orderId);
        if (!bundleSupport.hasBundle(order)) {
            throw new InvalidOrderStatusException("Đơn không có trà đóng theo yêu cầu");
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID && order.getPaymentStatus() != PaymentStatus.PARTIALLY_PAID) {
            throw new InvalidOrderStatusException("Đơn chưa thanh toán nên chưa gửi yêu cầu đóng gói");
        }
        bundleSupport.refreshPacking(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse handover(UUID orderId, FulfillBackorderRequest request) {
        Order order = getOrderForUpdate(orderId);
        if (!bundleSupport.hasBundle(order)) {
            throw new InvalidOrderStatusException("Đơn không có trà đóng theo yêu cầu");
        }
        if (order.isBackorder()
                && order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.WAITING_STOCK) {
            // Đơn chờ hàng: trừ tồn hàng thường, thu phần còn lại và kiểm tra bundle đã đóng gói trong fulfill.
            return orderBackorderService.fulfill(orderId, request);
        }
        if (order.getStatus() != OrderStatus.PAYMENT_CONFIRMED || order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidOrderStatusException("Đơn không ở trạng thái chờ giao trà đóng theo yêu cầu");
        }
        if (order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.CONSUMED) {
            throw new InvalidOrderStatusException("Đơn chưa trừ xong tồn kho hàng có sẵn, vui lòng thử lại sau");
        }
        bundleSupport.refreshPacking(order);
        bundleSupport.requireAllPacked(order);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED);
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                "Đã giao trà đóng theo yêu cầu cho khách",
                request == null ? null : request.getChangedBy());
        return orderResponseAssembler.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public boolean markPacked(UUID orderId, UUID orderItemId, String packingCode) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        OrderItem item = order.getItems().stream()
                .filter(line -> line.getId().equals(orderItemId))
                .findFirst()
                .orElse(null);
        if (item == null || !bundleSupport.markPacked(item, packingCode)) {
            return false;
        }
        orderRepository.save(order);
        return true;
    }

    private Order createDraftOrder(AddDraftBundleItemRequest request, Instant expiresAt) {
        CustomerResponse customerSnapshot = customerSnapshotResolver.resolve(request.getCustomerId());
        Order order = orderMapper.toDraftOrder(
                toDraftHeader(request),
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                expiresAt
        );
        Order savedOrder = orderRepository.saveAndFlush(order);
        orderStatusHistoryWriter.write(savedOrder, null, OrderStatus.DRAFT, "Tạo hóa đơn nháp POS", request.getCashierId());
        return savedOrder;
    }

    /** Thông tin quầy/khách để tạo đơn nháp, cùng cách với khi thêm mặt hàng thường. */
    private AddDraftOrderItemRequest toDraftHeader(AddDraftBundleItemRequest request) {
        AddDraftOrderItemRequest header = new AddDraftOrderItemRequest();
        header.setTenantId(request.getTenantId());
        header.setBranchId(request.getBranchId());
        header.setTerminalId(request.getTerminalId());
        header.setShiftId(request.getShiftId());
        header.setCashierId(request.getCashierId());
        header.setCashierNameSnapshot(request.getCashierNameSnapshot());
        header.setBranchNameSnapshot(request.getBranchNameSnapshot());
        header.setTerminalNameSnapshot(request.getTerminalNameSnapshot());
        header.setShiftCodeSnapshot(request.getShiftCodeSnapshot());
        header.setCustomerId(request.getCustomerId());
        header.setCustomerNameSnapshot(request.getCustomerNameSnapshot());
        header.setCustomerPhoneSnapshot(request.getCustomerPhoneSnapshot());
        return header;
    }

    private Order getEditableDraftOrder(UUID orderId) {
        Order order = orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
        orderValidator.validateEditableDraft(order, Instant.now());
        return order;
    }

    private Order getOrderForUpdate(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private static String bundleName(String requested, OrderCustomBundleSupport.CatalogVariant variant) {
        String name = normalize(requested);
        return name == null ? variant.variantName() : name;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
