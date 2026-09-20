package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {
    boolean existsBySupplierCode(String supplierCode);
    boolean existsBySupplierCodeAndIdNot(String supplierCode, UUID id);

    @Query("""
            select distinct s from Supplier s
            left join fetch s.ingredients i
            left join fetch i.unit
            where s.id in :supplierIds
            """)
    List<Supplier> fetchIngredientsForSuppliers(@Param("supplierIds") Collection<UUID> supplierIds);
}
