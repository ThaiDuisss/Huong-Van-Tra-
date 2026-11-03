package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.UnitMapper;
import org.hvtra.catalog.repository.UnitRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnitServiceImpl Unit Tests")
class UnitServiceImplTest {

    @Mock
    private UnitRepository repository;

    @Mock
    private UnitMapper mapper;

    @InjectMocks
    private UnitServiceImpl unitService;

    private UUID unitId;
    private Unit unit;
    private UnitResponse unitResponse;
    private UnitRequest unitRequest;

    @BeforeEach
    void setUp() {
        unitId = UUID.randomUUID();
        unit = new Unit();
        unit.setId(unitId);
        unit.setName("Hộp");
        unit.setSymbol("BOX");
        unit.setPrecisionScale(0);
        unit.setStatus(Status.ACTIVE);
        unit.setType(UnitType.COUNT);
        unit.setConversionRate(BigDecimal.ONE);
        unit.setIsDeleted(false);

        unitResponse = new UnitResponse(
                unitId,
                "Hộp",
                "BOX",
                0,
                UnitType.COUNT,
                null,
                BigDecimal.ONE,
                Status.ACTIVE,
                null
        );

        unitRequest = new UnitRequest(
                "Hộp",
                "BOX",
                0,
                UnitType.COUNT,
                null,
                BigDecimal.ONE,
                Status.ACTIVE
        );
    }

    @Test
    @DisplayName("create - Tạo đơn vị tính hợp lệ thành công")
    void create_WhenValidRequest_ShouldReturnResponse() {
        when(repository.existsBySymbolIgnoreCaseAndIsDeletedFalse("BOX")).thenReturn(false);
        when(repository.save(any(Unit.class))).thenReturn(unit);
        when(mapper.toResponse(any(Unit.class))).thenReturn(unitResponse);

        UnitResponse result = unitService.create(unitRequest);

        assertNotNull(result);
        assertEquals("Hộp", result.name());
        assertEquals("BOX", result.symbol());
        verify(repository).save(any(Unit.class));
    }

    @Test
    @DisplayName("create - Ký hiệu đơn vị đã tồn tại thì ném BadRequestException")
    void create_WhenSymbolAlreadyExists_ShouldThrowBadRequestException() {
        when(repository.existsBySymbolIgnoreCaseAndIsDeletedFalse("BOX")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> unitService.create(unitRequest));
    }

    @Test
    @DisplayName("create - Tạo đơn vị phái sinh với đơn vị gốc và tỷ lệ hợp lệ")
    void create_WithBaseUnit_WhenValid_ShouldResolveBaseUnitAndSave() {
        UUID baseId = UUID.randomUUID();
        Unit baseUnit = new Unit();
        baseUnit.setId(baseId);

        UnitRequest req = new UnitRequest("Gói", "PACK", 0, UnitType.PACKAGING, baseId, new BigDecimal("10.0"), Status.ACTIVE);
        when(repository.existsBySymbolIgnoreCaseAndIsDeletedFalse("PACK")).thenReturn(false);
        when(repository.findById(baseId)).thenReturn(Optional.of(baseUnit));
        when(repository.save(any(Unit.class))).thenReturn(unit);
        when(mapper.toResponse(any(Unit.class))).thenReturn(unitResponse);

        UnitResponse result = unitService.create(req);
        assertNotNull(result);
    }

    @Test
    @DisplayName("update - Cập nhật đơn vị tính thành công")
    void update_WhenValid_ShouldUpdateAndReturnResponse() {
        when(repository.existsBySymbolIgnoreCaseAndIdNotAndIsDeletedFalse("BOX", unitId)).thenReturn(false);
        when(repository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.save(any(Unit.class))).thenReturn(unit);
        when(mapper.toResponse(any(Unit.class))).thenReturn(unitResponse);

        UnitResponse result = unitService.update(unitId, unitRequest);

        assertNotNull(result);
        verify(repository).save(unit);
    }

    @Test
    @DisplayName("update - Ký hiệu đơn vị mới trùng với đơn vị khác thì ném BadRequestException")
    void update_WhenSymbolDuplicate_ShouldThrowBadRequestException() {
        when(repository.existsBySymbolIgnoreCaseAndIdNotAndIsDeletedFalse("BOX", unitId)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> unitService.update(unitId, unitRequest));
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái thành công")
    void updateStatus_WhenIdExists_ShouldUpdateAndReturnResponse() {
        when(repository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.save(any(Unit.class))).thenReturn(unit);
        when(mapper.toResponse(any(Unit.class))).thenReturn(unitResponse);

        UnitResponse result = unitService.updateStatus(unitId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, unit.getStatus());
        verify(repository).save(unit);
    }

    @Test
    @DisplayName("updateStatus - Không tìm thấy ID thì ném ResourceNotFoundException")
    void updateStatus_WhenIdNotFound_ShouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> unitService.updateStatus(nonExistentId, Status.INACTIVE));
    }

    @Test
    @DisplayName("findAll(filter) - Tìm kiếm phân trang đơn vị tính thành công")
    void findAllPaged_ShouldReturnPageResponse() {
        Page<Unit> page = new PageImpl<>(List.of(unit));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponseWithBaseUnitName(unit)).thenReturn(unitResponse);

        PageResponse<UnitResponse> result = unitService.findAll("Hộp", Status.ACTIVE, UnitType.COUNT, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("findAll() - Lấy tất cả danh sách đơn vị tính sắp xếp theo tên")
    void findAllList_ShouldReturnAllUnits() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(unit));
        when(mapper.toResponse(unit)).thenReturn(unitResponse);

        List<UnitResponse> result = unitService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("findBases - Lấy danh sách đơn vị gốc hoạt động thành công")
    void findBases_WhenActiveBasesExist_ShouldReturnList() {
        when(repository.findByBaseUnitIsNullAndIsDeletedFalseAndStatus(Status.ACTIVE)).thenReturn(List.of(unit));
        when(mapper.toResponse(unit)).thenReturn(unitResponse);

        List<UnitResponse> result = unitService.findBases();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hộp", result.get(0).name());
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown theo danh sách IDs thành công")
    void lookup_WithIds_ShouldReturnByIds() {
        when(repository.findAllById(List.of(unitId))).thenReturn(List.of(unit));

        PageResponse<LookupResponse> result = unitService.lookup(null, false, List.of(unitId), PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Hộp", result.content().get(0).name());
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown theo keyword phân trang")
    void lookup_WithKeyword_ShouldReturnPagedResult() {
        Page<Unit> page = new PageImpl<>(List.of(unit));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<LookupResponse> result = unitService.lookup("Hộp", false, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("findAllByIds - Lấy danh sách đơn vị tính theo IDs")
    void findAllByIds_ShouldReturnList() {
        when(repository.findAllById(List.of(unitId))).thenReturn(List.of(unit));
        when(mapper.toResponse(unit)).thenReturn(unitResponse);

        List<UnitResponse> result = unitService.findAllByIds(List.of(unitId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("delete - Xóa mềm đơn vị tính thành công")
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(repository.findById(unitId)).thenReturn(Optional.of(unit));

        unitService.delete(unitId);

        assertTrue(unit.getIsDeleted());
        verify(repository).save(unit);
    }

    @Test
    @DisplayName("delete - Đơn vị tính đã xóa mềm rồi thì ném ResourceNotFoundException")
    void delete_WhenAlreadyDeleted_ShouldThrowResourceNotFoundException() {
        unit.setIsDeleted(true);
        when(repository.findById(unitId)).thenReturn(Optional.of(unit));

        assertThrows(ResourceNotFoundException.class, () -> unitService.delete(unitId));
    }

    @Test
    @DisplayName("exportToExcel - Xuất danh sách đơn vị tính ra file Excel dạng byte[]")
    void exportToExcel_ShouldReturnByteArray() {
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(unit));

        byte[] result = unitService.exportToExcel("Hộp", Status.ACTIVE, UnitType.COUNT);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("getImportTemplate - Lấy file Excel mẫu import đơn vị tính")
    void getImportTemplate_ShouldReturnByteArray() {
        byte[] result = unitService.getImportTemplate();

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("importFromExcel - Import file Excel chỉ có header thì kết thúc sớm")
    void importFromExcel_WhenOnlyHeader_ShouldReturnEarly() throws Exception {
        Workbook wb = new XSSFWorkbook();
        wb.createSheet("Nhập đơn vị");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        unitService.importFromExcel(file);

        verify(repository, never()).save(any());
    }
}
