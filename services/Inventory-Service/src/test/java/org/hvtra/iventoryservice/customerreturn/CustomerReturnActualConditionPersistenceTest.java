package org.hvtra.iventoryservice.customerreturn;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:return_actual_condition;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ContextConfiguration(classes = CustomerReturnActualConditionPersistenceTest.JpaConfiguration.class)
class CustomerReturnActualConditionPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = InventoryItem.class)
    @EnableJpaRepositories(basePackageClasses = CustomerReturnInspectionRepository.class)
    static class JpaConfiguration {
    }

    @Autowired
    EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(CustomerReturnActualCondition.class)
    void actualConditionPersistsOnInspectionLine(CustomerReturnActualCondition condition) {
        CustomerReturnInspectionLine line = persistLine(condition);
        UUID lineId = line.getId();
        entityManager.clear();
        assertThat(entityManager.find(CustomerReturnInspectionLine.class, lineId).getActualCondition()).isEqualTo(condition);
        assertThat(entityManager.createNativeQuery("SELECT actual_condition FROM customer_return_inspection_lines WHERE id = :id")
                .setParameter("id", lineId).getSingleResult()).isEqualTo(condition.name());
    }

    @Test
    void historicalLineCanRetainUnknownActualCondition() {
        CustomerReturnInspectionLine line = persistLine(null);
        UUID lineId = line.getId();
        entityManager.clear();
        assertThat(entityManager.find(CustomerReturnInspectionLine.class, lineId).getActualCondition()).isNull();
    }

    @Test
    void newMigrationPreservesHistoricalRowsWithoutInventingObservations() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:qc_migration_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE customer_return_inspection_lines (id INT PRIMARY KEY, result VARCHAR(50), posted BOOLEAN)");
                statement.execute("INSERT INTO customer_return_inspection_lines VALUES (1, 'RESTOCK', TRUE)");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V261007100000__add_customer_return_actual_condition.sql"));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM customer_return_inspection_lines WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("actual_condition")).isNull();
                assertThat(rows.getString("result")).isEqualTo("RESTOCK");
                assertThat(rows.getBoolean("posted")).isTrue();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO customer_return_inspection_lines VALUES (2, 'WRITE_OFF', FALSE, 'OPENED')");
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT actual_condition FROM customer_return_inspection_lines WHERE id = 2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("OPENED");
            }
        }
    }

    private CustomerReturnInspectionLine persistLine(CustomerReturnActualCondition actualCondition) {
        InventoryItem item = new InventoryItem();
        item.setReferenceId(UUID.randomUUID());
        item.setItemType(InventoryItemType.PRODUCT_VARIANT);
        item.setReorderPoint(0);
        entityManager.persist(item);
        CustomerReturnInspection inspection = new CustomerReturnInspection();
        inspection.setInspectionCode("QC-" + UUID.randomUUID());
        inspection.setReturnId(UUID.randomUUID());
        inspection.setReturnCode("RET-TEST");
        CustomerReturnInspectionLine line = new CustomerReturnInspectionLine();
        line.setReturnItemId(UUID.randomUUID());
        line.setProductVariantId(item.getReferenceId());
        line.setInventoryItem(item);
        line.setQuantity(BigDecimal.ONE);
        line.setActualCondition(actualCondition);
        line.setResult(CustomerReturnQcResult.WRITE_OFF);
        inspection.addLine(line);
        entityManager.persist(inspection);
        entityManager.flush();
        return line;
    }
}
