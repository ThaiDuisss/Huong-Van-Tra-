package org.hvtra.iventoryservice.customerreturn;

import org.hvtra.iventoryservice.customerreturn.controller.CustomerReturnInspectionController;
import org.hvtra.iventoryservice.customerreturn.dto.request.PostCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionLineResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerReturnActualConditionContractTest {
    @Mock
    CustomerReturnInspectionService service;
    MockMvc mockMvc;
    final UUID inspectionId = UUID.randomUUID();
    final UUID lineId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerReturnInspectionController(service))
                .setValidator(validator).build();
    }

    @Test
    void postReceivesQcObservationAndResponseSeparatesDeclarationFromActual() throws Exception {
        CustomerReturnInspectionLineResponse line = new CustomerReturnInspectionLineResponse(
                lineId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SKU-001", "Tra sen", BigDecimal.ONE, CustomerReturnQcResult.WRITE_OFF, null, null,
                "Seal opened", true, UUID.randomUUID(), null, null, null, "SEALED", "CHANGE_OF_MIND",
                CustomerReturnActualCondition.OPENED);
        when(service.postInspection(eq(inspectionId), any())).thenReturn(new CustomerReturnInspectionResponse(
                inspectionId, "QC-001", UUID.randomUUID(), "RET-001", null, null, "ORD-001",
                CustomerReturnInspectionStatus.POSTED, null, null, null, List.of(line)));
        mockMvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body("OPENED", "Seal opened")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].declaredCondition").value("SEALED"))
                .andExpect(jsonPath("$.lines[0].actualCondition").value("OPENED"))
                .andExpect(jsonPath("$.lines[0].condition").value("SEALED"))
                .andExpect(jsonPath("$.lines[0].result").value("WRITE_OFF"));
        ArgumentCaptor<PostCustomerReturnInspectionRequest> request = ArgumentCaptor.forClass(PostCustomerReturnInspectionRequest.class);
        verify(service).postInspection(eq(inspectionId), request.capture());
        assertThat(request.getValue().lines().getFirst().actualCondition()).isEqualTo(CustomerReturnActualCondition.OPENED);
        assertThat(request.getValue().lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
    }

    @Test
    void dispositionValueCannotBeUsedAsActualCondition() throws Exception {
        mockMvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body("WRITE_OFF", "QC")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void correctedNoteCannotExceedPersistedFieldLength() throws Exception {
        mockMvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body("OPENED", "x".repeat(501))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String path() {
        return "/api/inventory/customer-return-inspections/" + inspectionId + "/post";
    }

    private String body(String actual, String note) {
        return "{\"lines\":[{\"lineId\":\"" + lineId + "\",\"actualCondition\":\"" + actual
                + "\",\"result\":\"WRITE_OFF\",\"note\":\"" + note + "\"}]}";
    }
}
