package org.hvtra.catalog.service.bundle;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.entity.BomItem;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.CustomerBundleLink;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.ProductType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.ComponentPayload;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.OrderItemPayload;
import org.hvtra.catalog.repository.BomItemRepository;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.CustomerBundleLinkRepository;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.taste.TasteComposition;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.hvtra.commonservice.common_core.util.CodeUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lưu Product, ProductVariant (productType = CUSTOMER) và BOM tương ứng sau khi đơn CUSTOM_BUNDLE đạt COMPLETED.
 * Khử trùng toàn hệ thống theo recipe_sha256, đảm bảo idempotency theo order_item_id và an toàn đồng thời.
 */
@Slf4j
@Service
public class CustomerBundleCatalogService {

    public static final String CUSTOMER_BUNDLE_CATEGORY_CODE = "CUSTOMER-BUNDLE";
    private static final String CUSTOMER_BUNDLE_CATEGORY_NAME = "Trà khách tự chọn";
    private static final String DEFAULT_BUNDLE_NAME = "Trà khách tự chọn";
    private static final String PACKAGE_UNIT_NAME = "Gói";
    private static final String PACKAGE_UNIT_SYMBOL = "gói";
    private static final UUID PACKAGE_UNIT_FALLBACK_ID = UUID.fromString("6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f02");
    private static final UUID CUSTOMER_CATEGORY_FALLBACK_ID = UUID.fromString("6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f10");

    private final CustomerBundleLinkRepository bundleLinkRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final BomItemRepository bomItemRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitRepository unitRepository;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final ProductVariantService productVariantService;
    private final RecipeCanonicalHasher recipeCanonicalHasher;
    private final TasteComposition tasteComposition;
    private final TransactionTemplate requiresNewTransaction;

    public CustomerBundleCatalogService(
            CustomerBundleLinkRepository bundleLinkRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            BomItemRepository bomItemRepository,
            CategoryRepository categoryRepository,
            IngredientRepository ingredientRepository,
            UnitRepository unitRepository,
            BusinessSequenceRepository businessSequenceRepository,
            ProductVariantService productVariantService,
            RecipeCanonicalHasher recipeCanonicalHasher,
            TasteComposition tasteComposition,
            PlatformTransactionManager transactionManager) {
        this.bundleLinkRepository = bundleLinkRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.bomItemRepository = bomItemRepository;
        this.categoryRepository = categoryRepository;
        this.ingredientRepository = ingredientRepository;
        this.unitRepository = unitRepository;
        this.businessSequenceRepository = businessSequenceRepository;
        this.productVariantService = productVariantService;
        this.recipeCanonicalHasher = recipeCanonicalHasher;
        this.tasteComposition = tasteComposition;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public boolean persistCompletedOrder(OrderCompletedCatalogEvent event) {
        if (event == null || event.getOrderId() == null || event.getItems() == null || event.getItems().isEmpty()) {
            return false;
        }
        if (event.getOrderStatus() != null && !"COMPLETED".equalsIgnoreCase(event.getOrderStatus())) {
            return false;
        }

        List<OrderItemPayload> bundleItems = event.getItems().stream()
                .filter(CustomerBundleCatalogService::isPersistableCustomBundle)
                .toList();
        if (bundleItems.isEmpty()) {
            return false;
        }

        for (OrderItemPayload item : bundleItems) {
            persistBundleItem(event, item);
        }
        return true;
    }

    public ProductVariant persistBundleItem(OrderCompletedCatalogEvent event, OrderItemPayload item) {
        Optional<CustomerBundleLink> existingLink = bundleLinkRepository.findByOrderItemId(item.getOrderItemId());
        if (existingLink.isPresent()) {
            UUID existingVariantId = existingLink.get().getProductVariantId();
            return productVariantRepository.findById(existingVariantId).orElse(null);
        }

        List<UUID> ingredientIds = item.getComponents().stream()
                .map(ComponentPayload::getIngredientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Ingredient> ingredientsById = ingredientRepository.findAllByIdWithUnit(ingredientIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        List<Unit> activeUnits = unitRepository.findByIsDeletedFalse(Sort.by("name"));

        RecipeCanonicalHasher.CanonicalRecipe recipe =
                recipeCanonicalHasher.canonicalize(item.getComponents(), ingredientsById, activeUnits);

        ProductVariant variant = resolveOrCreateCustomerVariant(item, recipe);
        recordLinkIdempotently(event, item, recipe.recipeSha256(), variant);
        return variant;
    }

    private ProductVariant resolveOrCreateCustomerVariant(
            OrderItemPayload item,
            RecipeCanonicalHasher.CanonicalRecipe recipe) {
        Optional<ProductVariant> existing = productVariantRepository
                .findFirstByRecipeSha256AndIsDeletedFalse(recipe.recipeSha256());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            ProductVariant created = requiresNewTransaction.execute(status -> createCustomerProductGraph(item, recipe));
            if (created != null) {
                return created;
            }
        } catch (DataIntegrityViolationException concurrentInsert) {
            log.debug("Concurrent insert detected for recipeSha256={}, reusing existing variant",
                    recipe.recipeSha256(), concurrentInsert);
        }

        return productVariantRepository.findFirstByRecipeSha256AndIsDeletedFalse(recipe.recipeSha256())
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to resolve CUSTOMER variant for recipeSha256=" + recipe.recipeSha256()));
    }

    private ProductVariant createCustomerProductGraph(
            OrderItemPayload item,
            RecipeCanonicalHasher.CanonicalRecipe recipe) {
        Optional<ProductVariant> lockedExisting = productVariantRepository
                .findByRecipeSha256ForUpdate(recipe.recipeSha256());
        if (lockedExisting.isPresent()) {
            return lockedExisting.get();
        }

        Category category = resolveOrCreateCustomerCategory();
        Unit packageUnit = resolveOrCreatePackageUnit();
        String bundleName = resolveBundleName(item.getVariantName(), item.getProductName());

        Product product = new Product();
        product.setCategory(category);
        product.setProductCode(nextUniqueProductCode(bundleName));
        product.setName(bundleName);
        product.setDescription("Công thức trà khách hàng được lưu từ đơn hoàn tất");
        product.setStatus(Status.ACTIVE);
        Product savedProduct = productRepository.saveAndFlush(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(savedProduct);
        variant.setSku(nextUniqueSku(savedProduct));
        variant.setVariantName(bundleName);
        variant.setQuantity(BigDecimal.ONE);
        variant.setSellingPrice(item.getUnitPrice() != null && item.getUnitPrice().signum() >= 0
                ? item.getUnitPrice()
                : BigDecimal.ZERO);
        variant.setCostPrice(BigDecimal.ZERO);
        variant.setSellableQuantity(BigDecimal.ZERO);
        variant.setProductionThreshold(BigDecimal.ZERO);
        variant.setProductionBatchQuantity(BigDecimal.ONE);
        variant.setUnit(packageUnit);
        variant.setInventoryPolicy(InventoryPolicy.DO_NOT_TRACK);
        variant.setStatus(Status.ACTIVE);
        variant.setProductType(ProductType.CUSTOMER);
        variant.setCustomBundle(false);
        variant.setBarcode(productVariantService.generateBarcode());
        variant.setRecipeSha256(recipe.recipeSha256());
        ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);

        List<BomItem> bomItems = recipe.lines().stream()
                .map(line -> {
                    BomItem bomItem = new BomItem();
                    bomItem.setProductVariantId(savedVariant.getId());
                    bomItem.setIngredient(line.ingredient());
                    bomItem.setUnit(line.storageUnit());
                    bomItem.setQuantity(line.storageQuantity());
                    bomItem.setWasteRate(BigDecimal.ZERO);
                    return bomItem;
                })
                .toList();
        bomItemRepository.saveAll(bomItems);
        bomItemRepository.flush();

        if (tasteComposition != null) {
            tasteComposition.changedVariant(savedVariant.getId());
        }

        // Không phát PRODUCT_VARIANT_CREATED sang Inventory-Service để tránh sinh InventoryItem/tồn kho thành phẩm giả,
        // và tuyệt đối không tạo hay approve VariantTasteProfile cho sản phẩm CUSTOMER.
        return savedVariant;
    }

    private void recordLinkIdempotently(
            OrderCompletedCatalogEvent event,
            OrderItemPayload item,
            String recipeSha256,
            ProductVariant variant) {
        if (bundleLinkRepository.existsByOrderItemId(item.getOrderItemId())) {
            return;
        }
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                if (bundleLinkRepository.existsByOrderItemId(item.getOrderItemId())) {
                    return;
                }
                CustomerBundleLink link = new CustomerBundleLink();
                link.setOrderId(event.getOrderId());
                link.setOrderItemId(item.getOrderItemId());
                link.setCustomerId(event.getCustomerId());
                link.setRecipeSha256(recipeSha256);
                link.setProductId(variant.getProduct().getId());
                link.setProductVariantId(variant.getId());
                bundleLinkRepository.saveAndFlush(link);
            });
        } catch (DataIntegrityViolationException duplicateLink) {
            log.debug("Order item {} already linked to CUSTOMER variant {}", item.getOrderItemId(), variant.getId(), duplicateLink);
        }
    }

    private Category resolveOrCreateCustomerCategory() {
        return categoryRepository.findByCategoryCode(CUSTOMER_BUNDLE_CATEGORY_CODE)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setCategoryCode(CUSTOMER_BUNDLE_CATEGORY_CODE);
                    category.setName(CUSTOMER_BUNDLE_CATEGORY_NAME);
                    category.setStatus(Status.ACTIVE);
                    try {
                        return categoryRepository.saveAndFlush(category);
                    } catch (DataIntegrityViolationException race) {
                        return categoryRepository.findByCategoryCode(CUSTOMER_BUNDLE_CATEGORY_CODE)
                                .orElseThrow(() -> race);
                    }
                });
    }

    private Unit resolveOrCreatePackageUnit() {
        return unitRepository.findBySymbolIgnoreCaseAndIsDeletedFalse(PACKAGE_UNIT_SYMBOL)
                .or(() -> unitRepository.findByNameIgnoreCaseAndIsDeletedFalse(PACKAGE_UNIT_NAME))
                .or(() -> unitRepository.findById(PACKAGE_UNIT_FALLBACK_ID))
                .orElseGet(() -> {
                    Unit unit = new Unit();
                    unit.setName(PACKAGE_UNIT_NAME);
                    unit.setSymbol(PACKAGE_UNIT_SYMBOL);
                    unit.setPrecisionScale(0);
                    unit.setType(UnitType.COUNT);
                    unit.setConversionRate(BigDecimal.ONE);
                    unit.setStatus(Status.ACTIVE);
                    return unitRepository.saveAndFlush(unit);
                });
    }

    private String nextUniqueProductCode(String bundleName) {
        String prefix = CodeUtils.getInitials(bundleName, "CB");
        BusinessSequence seq = lockSequence("PRODUCT");
        String code;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            code = String.format("%s-%02d", prefix, nextVal);
            if (code.length() > 50) {
                code = "CB-" + nextVal;
            }
        } while (productRepository.existsByProductCode(code));
        businessSequenceRepository.saveAndFlush(seq);
        return code;
    }

    private String nextUniqueSku(Product product) {
        String prefix = CodeUtils.getInitials(product.getName(), "CB", 1, PACKAGE_UNIT_SYMBOL);
        BusinessSequence seq = lockSequence("SKU");
        String sku;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            sku = String.format("%s-%05d", prefix, nextVal);
            if (sku.length() > 100) {
                sku = "CB-" + nextVal;
            }
        } while (productVariantRepository.existsBySku(sku));
        businessSequenceRepository.saveAndFlush(seq);
        return sku;
    }

    private BusinessSequence lockSequence(String key) {
        return businessSequenceRepository.findBySequenceKeyWithLock(key)
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey(key);
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });
    }

    private static boolean isPersistableCustomBundle(OrderItemPayload item) {
        if (item == null || item.getOrderItemId() == null
                || item.getComponents() == null || item.getComponents().isEmpty()) {
            return false;
        }
        String type = item.getPurchaseType() != null ? item.getPurchaseType() : item.getItemType();
        return "CUSTOM_BUNDLE".equalsIgnoreCase(type);
    }

    private static String resolveBundleName(String variantName, String productName) {
        String candidate = (variantName != null && !variantName.isBlank()) ? variantName.trim() : productName;
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT_BUNDLE_NAME;
        }
        String trimmed = candidate.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }
}
