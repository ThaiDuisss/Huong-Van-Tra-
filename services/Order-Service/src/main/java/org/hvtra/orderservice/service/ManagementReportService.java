package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.ManagementReportResponse.BusinessReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.CustomerReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.IngredientAnalysisReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductFormulaReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductReport;

import java.time.LocalDate;
import java.util.UUID;

public interface ManagementReportService {

    BusinessReport getBusinessReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId
    );

    ProductReport getProductReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId,
            int limit
    );

    CustomerReport getCustomerReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId,
            int limit
    );

    ProductFormulaReport getProductFormulaReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            UUID productId,
            int page,
            int size
    );

    IngredientAnalysisReport getIngredientAnalysisReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            UUID productId,
            int page,
            int size
    );
}
