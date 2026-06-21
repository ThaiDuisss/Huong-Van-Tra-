package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Barcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface BarcodeRepository extends JpaRepository<Barcode, UUID>, JpaSpecificationExecutor<Barcode> {
    boolean existsByBarcodeValue(String barcodeValue);
    boolean existsByBarcodeValueAndIdNot(String barcodeValue, UUID id);
    Optional<Barcode> findByBarcodeValue(String barcodeValue);
    Optional<Barcode> findByProductVariantIdAndPrimaryTrue(UUID variantId);
}
