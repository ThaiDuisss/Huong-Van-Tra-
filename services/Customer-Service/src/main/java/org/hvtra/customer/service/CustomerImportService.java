package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.CustomerImportRequest;
import org.hvtra.customer.dto.response.CustomerImportResponse;

public interface CustomerImportService {
    CustomerImportResponse importCustomers(CustomerImportRequest request);
}
