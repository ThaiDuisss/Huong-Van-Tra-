package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.Barcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface BarcodeRepository extends JpaRepository<Barcode, Long>, JpaSpecificationExecutor<Barcode> {
    boolean existsByBarcodeValue(String barcodeValue);
    boolean existsByBarcodeValueAndIdNot(String barcodeValue, Long id);
    Optional<Barcode> findByBarcodeValue(String barcodeValue);
    Optional<Barcode> findByProductVariantIdAndPrimaryTrue(Long variantId);
}
