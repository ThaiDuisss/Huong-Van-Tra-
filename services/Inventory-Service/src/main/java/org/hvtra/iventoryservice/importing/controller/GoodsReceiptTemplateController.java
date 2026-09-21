package org.hvtra.iventoryservice.importing.controller;

import org.hvtra.iventoryservice.importing.handler.GoodsReceiptImportHandler;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** File mẫu Excel riêng cho import Phiếu nhập kho — tách khỏi {@code ImportBatchController} (dùng chung mọi loại import). */
@RestController
@RequestMapping("/api/inventory/goods-receipts")
public class GoodsReceiptTemplateController {

    private final GoodsReceiptImportHandler handler;

    public GoodsReceiptTemplateController(GoodsReceiptImportHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/import/template")
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] excelBytes = handler.buildImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_phieu_nhap_kho.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
