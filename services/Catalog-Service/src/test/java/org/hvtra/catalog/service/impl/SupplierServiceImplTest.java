package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.InternalSupplierLookupResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Supplier;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.SupplierMapper;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.SupplierRepository;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierServiceImpl Unit Tests")
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository repository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private SupplierMapper mapper;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    private UUID supplierId;
    private Supplier supplier;
    private SupplierRequest supplierRequest;
    private SupplierResponse supplierResponse;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
        supplier = new Supplier();
        supplier.setId(supplierId);
        supplier.setSupplierCode("NCC-01");
        supplier.setName("Vườn Trà Thái Nguyên");
        supplier.setStatus(Status.ACTIVE);
        supplier.setIsDeleted(false);
        supplier.setIngredients(new HashSet<>());

        supplierRequest = new SupplierRequest(
                "Vườn Trà Thái Nguyên",
                "Nguyễn Văn A",
                "0987654321",
                "thainguyen@tea.vn",
                "Đại Từ, Thái Nguyên",
                "0102030405",
                "Ghi chú",
                Status.ACTIVE,
                Collections.emptyList()
        );

        supplierResponse = new SupplierResponse(
                supplierId,
                "NCC-01",
                "Vườn Trà Thái Nguyên",
                "Nguyễn Văn A",
                "0987654321",
                "thainguyen@tea.vn",
                "Đại Từ, Thái Nguyên",
                "0102030405",
                "Ghi chú",
                Status.ACTIVE,
                Collections.emptyList(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("create - Tạo nhà cung cấp với mã tự sinh thành công")
    void create_ShouldGenerateCodeAndReturnResponse() {
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(0L);

        when(businessSequenceRepository.findBySequenceKeyWithLock("SUPPLIER")).thenReturn(Optional.of(seq));
        when(repository.existsBySupplierCode(any(String.class))).thenReturn(false);
        when(repository.saveAndFlush(any(Supplier.class))).thenReturn(supplier);
        when(mapper.toResponse(any(Supplier.class))).thenReturn(supplierResponse);

        SupplierResponse result = supplierService.create(supplierRequest);

        assertNotNull(result);
        assertEquals("NCC-01", result.supplierCode());
    }

    @Test
    @DisplayName("createWithCode - Tạo nhà cung cấp với mã rõ ràng thành công")
    void createWithCode_WhenValid_ShouldReturnResponse() {
        when(repository.existsBySupplierCode("NCC-01")).thenReturn(false);
        when(repository.saveAndFlush(any(Supplier.class))).thenReturn(supplier);
        when(mapper.toResponse(any(Supplier.class))).thenReturn(supplierResponse);

        SupplierResponse result = supplierService.createWithCode(supplierRequest, "NCC-01");

        assertNotNull(result);
        assertEquals("NCC-01", result.supplierCode());
        assertEquals("Vườn Trà Thái Nguyên", result.name());
        verify(repository).saveAndFlush(any(Supplier.class));
    }

    @Test
    @DisplayName("createWithCode - Mã nhà cung cấp trùng thì ném AppException")
    void createWithCode_WhenDuplicateCode_ShouldThrowAppException() {
        when(repository.existsBySupplierCode("NCC-01")).thenReturn(true);

        assertThrows(AppException.class, () -> supplierService.createWithCode(supplierRequest, "NCC-01"));
    }

    @Test
    @DisplayName("update - Cập nhật thông tin nhà cung cấp thành công")
    void update_WhenExists_ShouldUpdateAndReturnResponse() {
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(repository.save(any(Supplier.class))).thenReturn(supplier);
        when(mapper.toResponse(any(Supplier.class))).thenReturn(supplierResponse);

        SupplierResponse result = supplierService.update(supplierId, supplierRequest);

        assertNotNull(result);
        verify(repository).save(supplier);
    }

    @Test
    @DisplayName("update - Nhà cung cấp đã bị xóa thì ném ResourceNotFoundException")
    void update_WhenDeleted_ShouldThrowResourceNotFoundException() {
        supplier.setIsDeleted(true);
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));

        assertThrows(ResourceNotFoundException.class, () -> supplierService.update(supplierId, supplierRequest));
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái nhà cung cấp thành công")
    void updateStatus_WhenExists_ShouldUpdateStatus() {
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(repository.save(any(Supplier.class))).thenReturn(supplier);
        when(mapper.toResponse(any(Supplier.class))).thenReturn(supplierResponse);

        SupplierResponse result = supplierService.updateStatus(supplierId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, supplier.getStatus());
        verify(repository).save(supplier);
    }

    @Test
    @DisplayName("findById - Tìm nhà cung cấp hợp lệ thành công")
    void findById_WhenExists_ShouldReturnResponse() {
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(mapper.toResponse(supplier)).thenReturn(supplierResponse);

        SupplierResponse result = supplierService.findById(supplierId);

        assertNotNull(result);
        assertEquals(supplierId, result.id());
    }

    @Test
    @DisplayName("findById - Nhà cung cấp đã bị xóa thì ném ResourceNotFoundException")
    void findById_WhenDeleted_ShouldThrowResourceNotFoundException() {
        supplier.setIsDeleted(true);
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));

        assertThrows(ResourceNotFoundException.class, () -> supplierService.findById(supplierId));
    }

    @Test
    @DisplayName("findByIds - Tìm danh sách nhà cung cấp theo IDs")
    void findByIds_ShouldReturnList() {
        when(repository.findAllById(List.of(supplierId))).thenReturn(List.of(supplier));
        when(mapper.toResponse(supplier)).thenReturn(supplierResponse);

        List<SupplierResponse> result = supplierService.findByIds(List.of(supplierId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("findAll - Tìm kiếm phân trang nhà cung cấp")
    void findAll_ShouldReturnPageResponse() {
        Page<Supplier> page = new PageImpl<>(List.of(supplier));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(supplier)).thenReturn(supplierResponse);

        PageResponse<SupplierResponse> result = supplierService.findAll("Thái Nguyên", Status.ACTIVE, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("findAllForLookup - Lấy danh sách lookup nội bộ")
    void findAllForLookup_ShouldReturnList() {
        when(repository.findAll()).thenReturn(List.of(supplier));

        List<InternalSupplierLookupResponse> result = supplierService.findAllForLookup();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("NCC-01", result.get(0).code());
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown nhà cung cấp")
    void lookup_ShouldReturnLookupPage() {
        when(repository.findLookup(any(), any())).thenReturn(Page.empty());

        PageResponse<LookupResponse> result = supplierService.lookup("NCC", null, PageRequest.of(0, 10));

        assertNotNull(result);
    }

    @Test
    @DisplayName("addIngredients - Gán thêm nguyên liệu cho nhà cung cấp")
    void addIngredients_ShouldAddToList() {
        UUID ingId = UUID.randomUUID();
        Ingredient ing = new Ingredient();
        ing.setId(ingId);

        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(ingredientRepository.findAllById(List.of(ingId))).thenReturn(List.of(ing));

        supplierService.addIngredients(supplierId, List.of(ingId));

        assertTrue(supplier.getIngredients().contains(ing));
        verify(repository).save(supplier);
    }

    @Test
    @DisplayName("findIngredientIds - Lấy danh sách ID nguyên liệu của nhà cung cấp")
    void findIngredientIds_ShouldReturnIds() {
        UUID ingId = UUID.randomUUID();
        when(ingredientRepository.findIdsBySupplierId(supplierId)).thenReturn(List.of(ingId));

        List<UUID> result = supplierService.findIngredientIds(supplierId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ingId, result.get(0));
    }

    @Test
    @DisplayName("delete - Xóa mềm nhà cung cấp thành công")
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(repository.findById(supplierId)).thenReturn(Optional.of(supplier));

        supplierService.delete(supplierId);

        assertTrue(supplier.getIsDeleted());
        verify(repository).save(supplier);
    }
}
