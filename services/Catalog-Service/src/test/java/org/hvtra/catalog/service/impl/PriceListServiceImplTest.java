package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.entity.PriceList;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.PriceListMapper;
import org.hvtra.catalog.repository.PriceListRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.PriceChangeHistoryService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceListServiceImpl Unit Tests")
class PriceListServiceImplTest {

    @Mock
    private PriceListRepository repository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private PriceListMapper mapper;

    @Mock
    private PriceChangeHistoryService priceChangeHistoryService;

    @InjectMocks
    private PriceListServiceImpl priceListService;

    private UUID priceListId;
    private UUID branchId;
    private UUID variantId;
    private PriceList priceList;
    private ProductVariant variant;
    private PriceListRequest priceListRequest;
    private PriceListResponse priceListResponse;

    @BeforeEach
    void setUp() {
        priceListId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        variant = new ProductVariant();
        variant.setId(variantId);

        priceList = new PriceList();
        priceList.setId(priceListId);
        priceList.setBranchId(branchId);
        priceList.setName("Bảng giá Tết 2026");
        priceList.setStatus(Status.ACTIVE);
        priceList.setItems(new ArrayList<>());

        PriceListRequest.ItemRequest itemRequest = new PriceListRequest.ItemRequest(
                variantId,
                new BigDecimal("250000.00")
        );

        priceListRequest = new PriceListRequest(
                branchId,
                "Bảng giá Tết 2026",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59),
                Status.ACTIVE,
                List.of(itemRequest)
        );

        priceListResponse = new PriceListResponse(
                priceListId,
                branchId,
                "Bảng giá Tết 2026",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59),
                Status.ACTIVE,
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("create - Tạo bảng giá hợp lệ thành công")
    void create_WhenValidRequest_ShouldReturnResponse() {
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(repository.save(any(PriceList.class))).thenReturn(priceList);
        when(mapper.toResponse(any(PriceList.class))).thenReturn(priceListResponse);

        PriceListResponse result = priceListService.create(priceListRequest);

        assertNotNull(result);
        assertEquals("Bảng giá Tết 2026", result.name());
        verify(repository).save(any(PriceList.class));
    }

    @Test
    @DisplayName("create - effectiveTo trước effectiveFrom thì ném BadRequestException")
    void create_WhenEffectiveToBeforeEffectiveFrom_ShouldThrowBadRequestException() {
        PriceListRequest invalidDateRequest = new PriceListRequest(
                branchId,
                "Bảng giá lỗi ngày",
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                Status.ACTIVE,
                List.of(new PriceListRequest.ItemRequest(variantId, new BigDecimal("100000.00")))
        );

        assertThrows(BadRequestException.class, () -> priceListService.create(invalidDateRequest));
    }

    @Test
    @DisplayName("create - Trùng biến thể sản phẩm trong danh sách thì ném BadRequestException")
    void create_WhenDuplicateVariantInItems_ShouldThrowBadRequestException() {
        PriceListRequest duplicateItemRequest = new PriceListRequest(
                branchId,
                "Bảng giá trùng sản phẩm",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59),
                Status.ACTIVE,
                List.of(
                        new PriceListRequest.ItemRequest(variantId, new BigDecimal("100000.00")),
                        new PriceListRequest.ItemRequest(variantId, new BigDecimal("120000.00"))
                )
        );

        assertThrows(BadRequestException.class, () -> priceListService.create(duplicateItemRequest));
    }

    @Test
    @DisplayName("update - Cập nhật bảng giá thành công")
    void update_WhenValid_ShouldUpdateAndRecordChanges() {
        when(repository.findById(priceListId)).thenReturn(Optional.of(priceList));
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(priceChangeHistoryService.snapshot(priceList)).thenReturn(Collections.emptyList());
        when(repository.save(any(PriceList.class))).thenReturn(priceList);
        when(mapper.toResponse(any(PriceList.class))).thenReturn(priceListResponse);

        PriceListResponse result = priceListService.update(priceListId, priceListRequest);

        assertNotNull(result);
        verify(priceChangeHistoryService).recordItemChanges(any(), any());
        verify(priceChangeHistoryService).recordStatusChange(any(), any());
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái bảng giá thành công")
    void updateStatus_WhenValid_ShouldUpdateAndRecordStatusChange() {
        when(repository.findById(priceListId)).thenReturn(Optional.of(priceList));
        when(repository.save(any(PriceList.class))).thenReturn(priceList);
        when(mapper.toResponse(any(PriceList.class))).thenReturn(priceListResponse);

        PriceListResponse result = priceListService.updateStatus(priceListId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, priceList.getStatus());
        verify(priceChangeHistoryService).recordStatusChange(any(), any());
    }

    @Test
    @DisplayName("findById - Tìm bảng giá theo ID thành công")
    void findById_WhenExists_ShouldReturnResponse() {
        when(repository.findAllWithItemsByIdIn(List.of(priceListId))).thenReturn(List.of(priceList));
        when(mapper.toResponse(priceList)).thenReturn(priceListResponse);

        PriceListResponse result = priceListService.findById(priceListId);

        assertNotNull(result);
        assertEquals(priceListId, result.id());
    }

    @Test
    @DisplayName("findById - Không tìm thấy bảng giá thì ném ResourceNotFoundException")
    void findById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(repository.findAllWithItemsByIdIn(List.of(nonExistentId))).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () -> priceListService.findById(nonExistentId));
    }

    @Test
    @DisplayName("findAll - Tìm kiếm phân trang danh sách bảng giá")
    void findAll_ShouldReturnPageResponse() {
        Page<PriceList> page = new PageImpl<>(List.of(priceList));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(repository.findAllWithItemsByIdIn(any())).thenReturn(List.of(priceList));
        when(mapper.toResponse(any(PriceList.class))).thenReturn(priceListResponse);

        PageResponse<PriceListResponse> result = priceListService.findAll(branchId, Status.ACTIVE, LocalDateTime.now(), PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }
}
