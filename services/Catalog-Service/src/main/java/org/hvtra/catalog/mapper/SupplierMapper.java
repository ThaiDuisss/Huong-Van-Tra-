package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.IngredientLookupResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.entity.Supplier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        List<IngredientLookupResponse> ingredients = supplier.getIngredients() == null
                ? Collections.emptyList()
                : supplier.getIngredients().stream()
                .filter(ing -> !Boolean.TRUE.equals(ing.getIsDeleted()))
                .map(ing -> new IngredientLookupResponse(
                        ing.getId(),
                        ing.getIngredientCode(),
                        ing.getName(),
                        ing.getStatus()
                ))
                .collect(Collectors.toList());

        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getTaxCode(),
                supplier.getNotes(),
                supplier.getStatus(),
                ingredients,
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }

    public void update(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setContactName(request.contactName() != null ? request.contactName().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setTaxCode(request.taxCode() != null ? request.taxCode().trim() : null);
        supplier.setNotes(request.notes() != null ? request.notes().trim() : null);
        if (request.status() != null) {
            supplier.setStatus(request.status());
        }
    }
}
