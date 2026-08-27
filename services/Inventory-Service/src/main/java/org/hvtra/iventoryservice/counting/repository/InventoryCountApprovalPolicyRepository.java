package org.hvtra.iventoryservice.counting.repository;

import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountApprovalPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryCountApprovalPolicyRepository
        extends JpaRepository<InventoryCountApprovalPolicy, UUID> {

    @Query("""
            select policy from InventoryCountApprovalPolicy policy
            where policy.active = true
              and policy.isDeleted = false
              and (policy.branchId = :branchId or policy.branchId is null)
            order by case when policy.branchId = :branchId then 0 else 1 end
            """)
    List<InventoryCountApprovalPolicy> findEffectivePolicies(@Param("branchId") UUID branchId);
}
