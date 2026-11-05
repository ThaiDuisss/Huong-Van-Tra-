package org.hvtra.catalog.repository;

import org.hvtra.catalog.repository.projection.IdNameView;
import org.hvtra.catalog.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            where s.id in :supplierIds and s.isDeleted = false
            """)
    List<Supplier> fetchIngredientsForSuppliers(@Param("supplierIds") Collection<UUID> supplierIds);

    /** Dropdown chọn NCC — chỉ trả id + name, tìm theo tên hoặc mã. */
    @Query("""
            select s.id as id, s.name as name
            from Supplier s
            where s.isDeleted = false
              and (:search is null
                   or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(s.supplierCode) like lower(concat('%', :search, '%')))
            """)
    Page<IdNameView> findLookup(@Param("search") String search, Pageable pageable);

    /** Lấy tên theo id cho các giá trị đã chọn sẵn trong dropdown. */
    @Query("select s.id as id, s.name as name from Supplier s where s.id in :ids and s.isDeleted = false")
    List<IdNameView> findLookupByIds(@Param("ids") Collection<UUID> ids);
}
