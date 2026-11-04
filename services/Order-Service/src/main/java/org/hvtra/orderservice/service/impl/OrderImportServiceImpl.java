package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderImportStatus;
import org.hvtra.orderservice.dto.request.CreateOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.OrderImportRequest;
import org.hvtra.orderservice.dto.request.OrderImportRowRequest;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderImportOrderResult;
import org.hvtra.orderservice.dto.response.OrderImportResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderImportService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Nhập đơn bán hàng hàng loạt từ dữ liệu client đã đọc ra từ file Excel.
 *
 * <p>Mỗi đơn được tạo qua {@link PosOfflineCheckoutService} giống một đơn bán tại quầy đồng bộ muộn:
 * thanh toán tiền mặt đủ, giữ đơn giá trong file, ghi nhận giờ bán thật. {@code clientOrderId} được
 * sinh cố định từ mã đơn tham chiếu nên nhập lại cùng một file không tạo trùng đơn.</p>
 *
 * <p>Cố ý KHÔNG đặt @Transactional ở đây: mỗi đơn chạy trong transaction riêng của
 * {@code OrderService#createOrder}, một đơn lỗi không làm hỏng các đơn khác.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderImportServiceImpl implements OrderImportService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CLIENT_ORDER_ID_NAMESPACE = "hvt-order-import:";
    private static final String PAYMENT_METHOD = "CASH";
    private static final String ORDER_TYPE = "POS";
    private static final int MAX_REFERENCE_LENGTH = 64;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MAX_QUANTITY_SCALE = 3;
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{8,15}$");
    private static final Pattern THOUSANDS_GROUPED = Pattern.compile("^\\d{1,3}([.,]\\d{3})+$");
    private static final Pattern NUMBER_NOISE = Pattern.compile("(?i)\\s|đ|₫|vnd");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[:ss]").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm[:ss]").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd H:mm[:ss]").withResolverStyle(ResolverStyle.STRICT)
    );
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    );

    private final PosOfflineCheckoutService posOfflineCheckoutService;
    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final CustomerClient customerClient;

    @Override
    public OrderImportResponse importOrders(OrderImportRequest request) {
        List<OrderImportRowRequest> rows = request == null ? null : request.rows();
        if (rows == null || rows.isEmpty()) {
            throw new InvalidOrderStatusException("Danh sách dòng đơn hàng cần nhập đang trống");
        }
        if (rows.size() > OrderImportRequest.MAX_ROWS) {
            throw new InvalidOrderStatusException(
                    "Mỗi lần chỉ được nhập tối đa " + OrderImportRequest.MAX_ROWS + " dòng hàng"
            );
        }
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());

        List<OrderImportOrderResult> results = new ArrayList<>();
        Map<String, List<IndexedRow>> groups = new LinkedHashMap<>();
        Map<String, String> displayReferences = new HashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            OrderImportRowRequest row = rows.get(index);
            int rowNumber = row == null || row.rowNumber() == null ? index + 2 : row.rowNumber();
            String reference = row == null ? null : trimToNull(row.orderReference());
            if (reference == null) {
                results.add(invalidRow(rowNumber, "Thiếu mã đơn tham chiếu"));
                continue;
            }
            String key = reference.toUpperCase(Locale.ROOT);
            displayReferences.putIfAbsent(key, reference);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new IndexedRow(rowNumber, row));
        }
        if (groups.size() > OrderImportRequest.MAX_ORDERS) {
            throw new InvalidOrderStatusException(
                    "File có " + groups.size() + " đơn, vượt giới hạn " + OrderImportRequest.MAX_ORDERS
                            + " đơn mỗi lần nhập. Vui lòng tách file."
            );
        }

        LookupCache cache = new LookupCache();
        for (Map.Entry<String, List<IndexedRow>> group : groups.entrySet()) {
            PreparedOrder prepared = prepare(displayReferences.get(group.getKey()), group.getKey(), group.getValue(), cache);
            results.add(process(prepared, dryRun));
        }
        results.sort((left, right) -> Integer.compare(firstRow(left), firstRow(right)));

        int success = 0;
        int duplicate = 0;
        int failed = 0;
        for (OrderImportOrderResult result : results) {
            switch (result.status()) {
                case VALID, IMPORTED -> success++;
                case DUPLICATE -> duplicate++;
                default -> failed++;
            }
        }
        return new OrderImportResponse(dryRun, rows.size(), results.size(), success, duplicate, failed, results);
    }

    private OrderImportOrderResult process(PreparedOrder prepared, boolean dryRun) {
        if (!prepared.errors.isEmpty()) {
            return prepared.result(OrderImportStatus.INVALID, prepared.errors, null);
        }

        Optional<Order> existing = orderRepository.findByClientOrderIdAndIsDeletedFalse(
                prepared.request.getClientOrderId()
        );
        if (existing.isPresent()) {
            Order order = existing.get();
            return prepared.result(
                    OrderImportStatus.DUPLICATE,
                    List.of("Mã đơn tham chiếu đã được nhập trước đó (đơn " + order.getOrderCode() + "), sẽ bỏ qua"),
                    new CreatedOrder(order.getId(), order.getOrderCode(), order.getFinalAmount())
            );
        }

        if (dryRun) {
            return prepared.result(OrderImportStatus.VALID, prepared.warnings, null);
        }

        try {
            OrderResponse order = posOfflineCheckoutService.checkout(prepared.request);
            return prepared.result(
                    OrderImportStatus.IMPORTED,
                    prepared.warnings,
                    new CreatedOrder(order.getId(), order.getOrderCode(), order.getFinalAmount())
            );
        } catch (RuntimeException exception) {
            log.warn("Nhập đơn tham chiếu {} thất bại", prepared.reference, exception);
            String message = trimToNull(exception.getMessage());
            return prepared.result(
                    OrderImportStatus.FAILED,
                    List.of(message == null ? "Không tạo được đơn hàng, vui lòng thử lại" : message),
                    null
            );
        }
    }

    private PreparedOrder prepare(String reference, String referenceKey, List<IndexedRow> rows, LookupCache cache) {
        PreparedOrder prepared = new PreparedOrder(reference, rows.stream().map(IndexedRow::rowNumber).toList());
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            prepared.errors.add("Mã đơn tham chiếu tối đa " + MAX_REFERENCE_LENGTH + " ký tự");
        }

        String soldAtText = sameValueAcrossRows(rows, OrderImportRowRequest::soldAt, "ngày bán", prepared);
        String phoneText = sameValueAcrossRows(rows, OrderImportRowRequest::customerPhone, "số điện thoại khách hàng", prepared);
        String customerName = sameValueAcrossRows(rows, OrderImportRowRequest::customerName, "tên khách hàng", prepared);

        Instant soldAt = null;
        if (soldAtText != null) {
            soldAt = parseSoldAt(soldAtText);
            if (soldAt == null) {
                prepared.errors.add("Ngày bán \"" + soldAtText + "\" không hợp lệ (định dạng dd/MM/yyyy HH:mm)");
            } else if (soldAt.isAfter(Instant.now())) {
                prepared.errors.add("Ngày bán không được ở tương lai");
            }
        }
        prepared.soldAt = soldAt;

        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientOrderId(clientOrderIdOf(referenceKey));
        request.setOfflineSale(Boolean.TRUE);
        request.setSoldAt(soldAt);
        request.setOrderType(ORDER_TYPE);
        request.setPaymentMethod(PAYMENT_METHOD);
        request.setShippingFee(BigDecimal.ZERO);

        String phone = phoneText == null ? null : PHONE_SEPARATORS.matcher(phoneText).replaceAll("");
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            prepared.errors.add("Số điện thoại khách hàng \"" + phoneText + "\" không hợp lệ");
            phone = null;
        }
        if (phone != null) {
            Optional<CustomerResponse> customer = cache.customerByPhone(phone);
            if (customer.isPresent()) {
                request.setCustomerId(customer.get().getId());
                customerName = firstNonBlank(customer.get().getFullName(), customerName);
            } else {
                prepared.warnings.add("Không tìm thấy khách hàng có SĐT " + phone + ", đơn chỉ lưu tên và SĐT");
            }
        }
        request.setCustomerPhoneSnapshot(phone);
        request.setCustomerNameSnapshot(customerName);
        prepared.customerName = customerName;
        prepared.customerPhone = phone;

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (IndexedRow indexedRow : rows) {
            OrderImportRowRequest row = indexedRow.row();
            String prefix = "Dòng " + indexedRow.rowNumber() + ": ";
            int errorsBefore = prepared.errors.size();

            String sku = trimToNull(row.sku());
            CatalogVariantResponse variant = null;
            if (sku == null) {
                prepared.errors.add(prefix + "thiếu mã SKU");
            } else {
                Optional<CatalogVariantResponse> lookup = cache.variantBySku(sku);
                if (lookup == null) {
                    prepared.errors.add(prefix + "không tra cứu được sản phẩm \"" + sku + "\", vui lòng thử lại sau");
                } else if (lookup.isEmpty()) {
                    prepared.errors.add(prefix + "không tìm thấy sản phẩm có SKU \"" + sku + "\"");
                } else {
                    variant = lookup.get();
                }
            }

            BigDecimal quantity = parseNumber(row.quantity());
            if (trimToNull(row.quantity()) == null) {
                prepared.errors.add(prefix + "thiếu số lượng");
            } else if (quantity == null || quantity.signum() <= 0) {
                prepared.errors.add(prefix + "số lượng \"" + row.quantity() + "\" phải là số lớn hơn 0");
            } else if (quantity.stripTrailingZeros().scale() > MAX_QUANTITY_SCALE) {
                prepared.errors.add(prefix + "số lượng tối đa " + MAX_QUANTITY_SCALE + " chữ số thập phân");
            }

            BigDecimal unitPrice = null;
            if (trimToNull(row.unitPrice()) != null) {
                unitPrice = parseNumber(row.unitPrice());
                if (unitPrice == null || unitPrice.signum() < 0) {
                    prepared.errors.add(prefix + "đơn giá \"" + row.unitPrice() + "\" phải là số không âm");
                    unitPrice = null;
                }
            } else if (variant != null && variant.sellingPrice() == null) {
                prepared.errors.add(prefix + "sản phẩm \"" + sku + "\" chưa có giá bán, vui lòng nhập đơn giá");
            }

            String note = trimToNull(row.note());
            if (note != null && note.length() > MAX_NOTE_LENGTH) {
                prepared.errors.add(prefix + "ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự");
            }

            if (prepared.errors.size() > errorsBefore || variant == null || quantity == null) {
                continue;
            }

            CreateOrderItemRequest item = new CreateOrderItemRequest();
            item.setProductVariantId(variant.id());
            item.setQuantity(quantity);
            item.setUnitPriceSnapshot(unitPrice == null ? null : unitPrice.setScale(2, RoundingMode.HALF_UP));
            item.setNote(note);
            request.getItems().add(item);

            BigDecimal price = unitPrice != null ? unitPrice : variant.sellingPrice();
            totalQuantity = totalQuantity.add(quantity);
            totalAmount = totalAmount.add(price.multiply(quantity));
        }

        prepared.request = request;
        prepared.itemCount = rows.size();
        prepared.totalQuantity = totalQuantity;
        prepared.totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        return prepared;
    }

    /** Các trường cấp đơn có thể chỉ điền ở dòng đầu; nếu điền ở nhiều dòng thì phải giống nhau. */
    private String sameValueAcrossRows(
            List<IndexedRow> rows,
            Function<OrderImportRowRequest, String> getter,
            String label,
            PreparedOrder prepared
    ) {
        String value = null;
        for (IndexedRow row : rows) {
            String current = trimToNull(getter.apply(row.row()));
            if (current == null) {
                continue;
            }
            if (value == null) {
                value = current;
            } else if (!value.equalsIgnoreCase(current)) {
                prepared.errors.add("Các dòng của cùng một đơn có " + label + " khác nhau (\""
                        + value + "\" và \"" + current + "\")");
                return value;
            }
        }
        return value;
    }

    private Instant parseSoldAt(String text) {
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, format).atZone(VN_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // thử định dạng tiếp theo
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format).atStartOfDay(VN_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // thử định dạng tiếp theo
            }
        }
        return null;
    }

    /** Hiểu được "12000", "12.000", "12,000", "1,5", "12.000đ". */
    private BigDecimal parseNumber(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        value = NUMBER_NOISE.matcher(value).replaceAll("");
        if (THOUSANDS_GROUPED.matcher(value).matches()) {
            value = value.replace(".", "").replace(",", "");
        } else if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private UUID clientOrderIdOf(String referenceKey) {
        return UUID.nameUUIDFromBytes((CLIENT_ORDER_ID_NAMESPACE + referenceKey).getBytes(StandardCharsets.UTF_8));
    }

    private OrderImportOrderResult invalidRow(int rowNumber, String message) {
        return new OrderImportOrderResult(
                null, List.of(rowNumber), OrderImportStatus.INVALID, List.of(message),
                null, null, null, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, null
        );
    }

    private static int firstRow(OrderImportOrderResult result) {
        return result.rowNumbers().isEmpty() ? Integer.MAX_VALUE : result.rowNumbers().get(0);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private record IndexedRow(int rowNumber, OrderImportRowRequest row) {
    }

    private record CreatedOrder(UUID id, String orderCode, BigDecimal finalAmount) {
    }

    private static final class PreparedOrder {
        private final String reference;
        private final List<Integer> rowNumbers;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private CreateOrderRequest request;
        private Instant soldAt;
        private String customerName;
        private String customerPhone;
        private int itemCount;
        private BigDecimal totalQuantity = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private PreparedOrder(String reference, List<Integer> rowNumbers) {
            this.reference = reference;
            this.rowNumbers = rowNumbers;
        }

        private OrderImportOrderResult result(OrderImportStatus status, List<String> messages, CreatedOrder created) {
            BigDecimal amount = created != null && created.finalAmount() != null ? created.finalAmount() : totalAmount;
            return new OrderImportOrderResult(
                    reference,
                    rowNumbers,
                    status,
                    List.copyOf(messages),
                    soldAt,
                    customerName,
                    customerPhone,
                    itemCount,
                    totalQuantity,
                    amount,
                    created == null ? null : created.id(),
                    created == null ? null : created.orderCode()
            );
        }
    }

    /** Tra cứu SKU / khách hàng một lần cho cả file. */
    private final class LookupCache {
        private final Map<String, Optional<CatalogVariantResponse>> variants = new HashMap<>();
        private final Map<String, Optional<CustomerResponse>> customers = new HashMap<>();

        /** @return Optional rỗng nếu không có SKU; null nếu Catalog-Service lỗi. */
        private Optional<CatalogVariantResponse> variantBySku(String sku) {
            return variants.computeIfAbsent(sku.toUpperCase(Locale.ROOT), ignored -> {
                try {
                    PageResponse<CatalogVariantResponse> page =
                            catalogClient.findAll(null, sku, null, null, null, null, null, 0, 20);
                    if (page == null || page.content() == null) {
                        return Optional.empty();
                    }
                    return page.content().stream()
                            .filter(Objects::nonNull)
                            .filter(variant -> sku.equalsIgnoreCase(trimToNull(variant.sku())))
                            .findFirst();
                } catch (RuntimeException exception) {
                    // Không cache lỗi tạm thời; null = không tra cứu được (khác với không tồn tại).
                    log.warn("Tra cứu SKU {} khi nhập đơn thất bại", sku, exception);
                    return null;
                }
            });
        }

        private Optional<CustomerResponse> customerByPhone(String phone) {
            return customers.computeIfAbsent(phone, ignored -> {
                try {
                    List<CustomerResponse> found = customerClient.getCustomers(phone);
                    if (found == null) {
                        return Optional.empty();
                    }
                    return found.stream()
                            .filter(Objects::nonNull)
                            .filter(customer -> customer.getPhone() != null
                                    && phone.equals(PHONE_SEPARATORS.matcher(customer.getPhone()).replaceAll("")))
                            .findFirst();
                } catch (RuntimeException exception) {
                    log.warn("Tra cứu khách hàng SĐT {} khi nhập đơn thất bại", phone, exception);
                    return Optional.empty();
                }
            });
        }
    }
}
