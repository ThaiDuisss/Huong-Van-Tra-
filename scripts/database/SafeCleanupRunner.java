package scripts.database;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;

/**
 * Utility dọn dẹp dữ liệu test an toàn cho Return/QC/Payment flow.
 *
 * Chạy với:
 *   java -cp ... SafeCleanupRunner --dry-run
 *   java -cp ... SafeCleanupRunner --execute
 */
public class SafeCleanupRunner {
    // Host va password lay tu bien moi truong; default host la box 2 qua tailnet.
    // Khong hardcode password trong source.
    private static final String HOST = System.getenv().getOrDefault("DB_HOST", "100.99.2.30:3306");
    private static final String DB_PASS = System.getenv("DB_PASS");

    // Expected exact record counts
    public static final int EXPECTED_RETURNS = 23;
    public static final int EXPECTED_ORDERS = 14;
    public static final int EXPECTED_RETURN_ITEMS = 20;
    public static final int EXPECTED_RETURN_HISTORY = 26;
    public static final int EXPECTED_ORDER_ITEMS = 14;
    public static final int EXPECTED_INSPECTIONS = 14;
    public static final int EXPECTED_INSPECTION_LINES = 13;
    public static final int EXPECTED_INV_TX = 13;
    public static final int EXPECTED_INV_TX_LINES = 9;
    public static final int EXPECTED_BATCH_ALLOC = 9;
    public static final int EXPECTED_BATCHES = 1;
    public static final int EXPECTED_PAYMENTS = 9;
    public static final int EXPECTED_PAY_TX = 13;

    private static final UUID DAMAGED_LOC_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID SALEABLE_LOC_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID INVENTORY_ITEM_ID = UUID.fromString("0e151fe4-da9d-48b6-99e1-2443da3ef750");

    private static byte[] toBytes(UUID uuid) {
        if (uuid == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private static UUID toUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    public static void main(String[] args) throws Exception {
        boolean executeMode = args.length > 0 && "--execute".equalsIgnoreCase(args[0]);

        System.out.println("================================================================================");
        System.out.printf("   HUONG VAN TRA - TEST DATA CLEANUP RUNNER [%s MODE]%n",
                executeMode ? "EXECUTE (LIVE COMMIT)" : "DRY-RUN (PREVIEW ONLY)");
        System.out.println("================================================================================");

        // 1. Check order_db
        List<byte[]> returnIdBytes = new ArrayList<>();
        List<UUID> returnIds = new ArrayList<>();
        List<byte[]> orderIdBytes = new ArrayList<>();
        List<UUID> orderIds = new ArrayList<>();

        try (Connection orderConn = DriverManager.getConnection("jdbc:mysql://" + HOST + "/order_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "order_app", DB_PASS)) {
            String retSql = "SELECT id, return_code FROM order_returns " +
                            "WHERE is_deleted = 0 AND (" +
                            "   return_code LIKE 'RET-IDEM-%' " +
                            "   OR return_code LIKE 'RET-MA-%' " +
                            "   OR return_code LIKE 'RET-MB-%' " +
                            "   OR return_code LIKE 'RET-MC-%' " +
                            "   OR return_code LIKE 'RET-A-%' " +
                            "   OR return_code LIKE 'RET-B-%' " +
                            "   OR return_code LIKE 'RET-C-%')";
            try (Statement st = orderConn.createStatement(); ResultSet rs = st.executeQuery(retSql)) {
                while (rs.next()) {
                    byte[] b = rs.getBytes("id");
                    returnIdBytes.add(b);
                    returnIds.add(toUuid(b));
                }
            }

            String ordSql = "SELECT id, order_code FROM orders " +
                            "WHERE is_deleted = 0 AND (" +
                            "   order_code LIKE 'ORD-IDEM-%' " +
                            "   OR order_code LIKE 'ORD-MULTI-%' " +
                            "   OR order_code LIKE 'ORD-AUDIT-%')";
            try (Statement st = orderConn.createStatement(); ResultSet rs = st.executeQuery(ordSql)) {
                while (rs.next()) {
                    byte[] b = rs.getBytes("id");
                    orderIdBytes.add(b);
                    orderIds.add(toUuid(b));
                }
            }

            System.out.printf("[order_db] Found active test returns: %d (Expected: %d)%n", returnIds.size(), EXPECTED_RETURNS);
            System.out.printf("[order_db] Found active test orders:  %d (Expected: %d)%n", orderIds.size(), EXPECTED_ORDERS);
        }

        // 2. Check inventory_db
        List<byte[]> inspIdBytes = new ArrayList<>();
        try (Connection invConn = DriverManager.getConnection("jdbc:mysql://" + HOST + "/inventory_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "inventory_app", DB_PASS)) {
            String inRetClause = String.join(",", Collections.nCopies(Math.max(returnIds.size(), 1), "?"));
            String inspSql = "SELECT id, inspection_code FROM customer_return_inspections " +
                             "WHERE is_deleted = 0 AND (inspection_code LIKE 'CR-QC-IDEM-%' OR return_id IN (" + inRetClause + "))";
            try (PreparedStatement ps = invConn.prepareStatement(inspSql)) {
                for (int i = 0; i < returnIdBytes.size(); i++) ps.setBytes(i + 1, returnIdBytes.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) inspIdBytes.add(rs.getBytes("id"));
                }
            }
            System.out.printf("[inventory_db] Found active test inspections: %d (Expected: %d)%n", inspIdBytes.size(), EXPECTED_INSPECTIONS);
        }

        if (!executeMode) {
            System.out.println("\n>>> DRY-RUN COMPLETE. No changes were made to databases.");
            System.out.println(">>> To execute cleanup, run with '--execute' or run scripts/database/cleanup_test_return_data.sql");
            return;
        }

        // 3. EXECUTE MODE: Apply per-schema transaction
        System.out.println("\n>>> EXECUTING CLEANUP WITH TRANSACTIONS PER SCHEMA...");

        // Schema 1: order_db
        try (Connection orderConn = DriverManager.getConnection("jdbc:mysql://" + HOST + "/order_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "order_app", DB_PASS)) {
            orderConn.setAutoCommit(false);
            try {
                String inRetClause = String.join(",", Collections.nCopies(returnIds.size(), "?"));
                String inOrdClause = String.join(",", Collections.nCopies(orderIds.size(), "?"));

                // order_return_items
                int a1;
                try (PreparedStatement ps = orderConn.prepareStatement("UPDATE order_return_items SET is_deleted = 1, updated_at = NOW() WHERE return_id IN (" + inRetClause + ")")) {
                    for (int i = 0; i < returnIdBytes.size(); i++) ps.setBytes(i + 1, returnIdBytes.get(i));
                    a1 = ps.executeUpdate();
                }
                // order_return_history
                int a2;
                try (PreparedStatement ps = orderConn.prepareStatement("UPDATE order_return_history SET is_deleted = 1, updated_at = NOW() WHERE return_id IN (" + inRetClause + ")")) {
                    for (int i = 0; i < returnIdBytes.size(); i++) ps.setBytes(i + 1, returnIdBytes.get(i));
                    a2 = ps.executeUpdate();
                }
                // order_returns
                int a3;
                try (PreparedStatement ps = orderConn.prepareStatement("UPDATE order_returns SET is_deleted = 1, updated_at = NOW() WHERE id IN (" + inRetClause + ")")) {
                    for (int i = 0; i < returnIdBytes.size(); i++) ps.setBytes(i + 1, returnIdBytes.get(i));
                    a3 = ps.executeUpdate();
                }
                // order_items
                int a4;
                try (PreparedStatement ps = orderConn.prepareStatement("UPDATE order_items SET is_deleted = 1, updated_at = NOW() WHERE order_id IN (" + inOrdClause + ")")) {
                    for (int i = 0; i < orderIdBytes.size(); i++) ps.setBytes(i + 1, orderIdBytes.get(i));
                    a4 = ps.executeUpdate();
                }
                // orders
                int a5;
                try (PreparedStatement ps = orderConn.prepareStatement("UPDATE orders SET is_deleted = 1, updated_at = NOW() WHERE id IN (" + inOrdClause + ")")) {
                    for (int i = 0; i < orderIdBytes.size(); i++) ps.setBytes(i + 1, orderIdBytes.get(i));
                    a5 = ps.executeUpdate();
                }

                System.out.printf("  [order_db] Updated: return_items=%d, return_history=%d, returns=%d, order_items=%d, orders=%d%n",
                        a1, a2, a3, a4, a5);
                if (a1 != EXPECTED_RETURN_ITEMS || a2 != EXPECTED_RETURN_HISTORY || a3 != EXPECTED_RETURNS || a4 != EXPECTED_ORDER_ITEMS || a5 != EXPECTED_ORDERS) {
                    throw new IllegalStateException("order_db affected rows mismatch! Rolling back.");
                }
                orderConn.commit();
                System.out.println("  [order_db] COMMITTED successfully!");
            } catch (Exception e) {
                orderConn.rollback();
                throw e;
            }
        }

        // Schema 2: inventory_db
        try (Connection invConn = DriverManager.getConnection("jdbc:mysql://" + HOST + "/inventory_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "inventory_app", DB_PASS)) {
            invConn.setAutoCommit(false);
            try {
                String inInspClause = String.join(",", Collections.nCopies(inspIdBytes.size(), "?"));
                String inRetClause = String.join(",", Collections.nCopies(returnIds.size(), "?"));

                // inspection lines
                int b1;
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE customer_return_inspection_lines SET is_deleted = 1, updated_at = NOW() WHERE inspection_id IN (" + inInspClause + ")")) {
                    for (int i = 0; i < inspIdBytes.size(); i++) ps.setBytes(i + 1, inspIdBytes.get(i));
                    b1 = ps.executeUpdate();
                }
                // inspections
                int b2;
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE customer_return_inspections SET is_deleted = 1, updated_at = NOW() WHERE id IN (" + inInspClause + ")")) {
                    for (int i = 0; i < inspIdBytes.size(); i++) ps.setBytes(i + 1, inspIdBytes.get(i));
                    b2 = ps.executeUpdate();
                }
                // inv tx batch allocations
                int b3;
                try (Statement st = invConn.createStatement()) {
                    b3 = st.executeUpdate("UPDATE inventory_transaction_batch_allocations SET is_deleted = 1, updated_at = NOW() " +
                                          "WHERE transaction_line_id IN (SELECT tl.id FROM inventory_transaction_lines tl JOIN inventory_transactions t ON tl.transaction_id = t.id WHERE t.reference_type = 'CUSTOMER_RETURN')");
                }
                // inv tx lines
                int b4;
                try (Statement st = invConn.createStatement()) {
                    b4 = st.executeUpdate("UPDATE inventory_transaction_lines SET is_deleted = 1, updated_at = NOW() " +
                                          "WHERE transaction_id IN (SELECT id FROM inventory_transactions WHERE reference_type = 'CUSTOMER_RETURN')");
                }
                // inv transactions
                int b5;
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE inventory_transactions SET is_deleted = 1, updated_at = NOW() WHERE reference_type = 'CUSTOMER_RETURN' AND reference_id IN (" + inRetClause + ")")) {
                    for (int i = 0; i < returnIds.size(); i++) ps.setString(i + 1, returnIds.get(i).toString());
                    b5 = ps.executeUpdate();
                }
                // test batch
                int b6;
                try (Statement st = invConn.createStatement()) {
                    b6 = st.executeUpdate("UPDATE inventory_batches SET is_deleted = 1, updated_at = NOW() WHERE batch_code LIKE 'BATCH-AUDIT-%'");
                }
                // balance corrections
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE inventory_balances SET quantity_on_hand = GREATEST(0, quantity_on_hand - 5.000), updated_at = NOW() WHERE inventory_item_id = ? AND location_id = ?")) {
                    ps.setBytes(1, toBytes(INVENTORY_ITEM_ID));
                    ps.setBytes(2, toBytes(SALEABLE_LOC_ID));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE inventory_balances SET quantity_on_hand = GREATEST(0, quantity_on_hand - 4.000), quantity_blocked = GREATEST(0, quantity_blocked - 4.000), updated_at = NOW() WHERE inventory_item_id = ? AND location_id = ?")) {
                    ps.setBytes(1, toBytes(INVENTORY_ITEM_ID));
                    ps.setBytes(2, toBytes(DAMAGED_LOC_ID));
                    ps.executeUpdate();
                }
                // DAMAGED test location
                try (PreparedStatement ps = invConn.prepareStatement("UPDATE inventory_locations SET is_deleted = 1, updated_at = NOW() WHERE id = ?")) {
                    ps.setBytes(1, toBytes(DAMAGED_LOC_ID));
                    ps.executeUpdate();
                }

                System.out.printf("  [inventory_db] Updated: insp_lines=%d, inspections=%d, batch_alloc=%d, tx_lines=%d, tx=%d, batch=%d%n",
                        b1, b2, b3, b4, b5, b6);
                if (b1 != EXPECTED_INSPECTION_LINES || b2 != EXPECTED_INSPECTIONS || b3 != EXPECTED_BATCH_ALLOC || b4 != EXPECTED_INV_TX_LINES || b5 != EXPECTED_INV_TX || b6 != EXPECTED_BATCHES) {
                    throw new IllegalStateException("inventory_db affected rows mismatch! Rolling back.");
                }
                invConn.commit();
                System.out.println("  [inventory_db] COMMITTED successfully! Balances reversed.");
            } catch (Exception e) {
                invConn.rollback();
                throw e;
            }
        }

        // Schema 3: payment_db
        try (Connection payConn = DriverManager.getConnection("jdbc:mysql://" + HOST + "/payment_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "payment_app", DB_PASS)) {
            payConn.setAutoCommit(false);
            try {
                String inRetClause = String.join(",", Collections.nCopies(returnIds.size(), "?"));
                String inOrdClause = String.join(",", Collections.nCopies(orderIds.size(), "?"));

                int c1;
                try (PreparedStatement ps = payConn.prepareStatement("UPDATE payment_transactions SET status = 'IGNORED', updated_at = NOW() WHERE refund_reference IN (" + inRetClause + ")")) {
                    for (int i = 0; i < returnIds.size(); i++) ps.setString(i + 1, returnIds.get(i).toString());
                    c1 = ps.executeUpdate();
                }
                int c2;
                try (PreparedStatement ps = payConn.prepareStatement("UPDATE payments SET is_deleted = 1, updated_at = NOW() WHERE order_id IN (" + inOrdClause + ")")) {
                    for (int i = 0; i < orderIdBytes.size(); i++) ps.setBytes(i + 1, orderIdBytes.get(i));
                    c2 = ps.executeUpdate();
                }

                System.out.printf("  [payment_db] Updated: pay_transactions=%d, payments=%d%n", c1, c2);
                if (c1 != EXPECTED_PAY_TX || c2 != EXPECTED_PAYMENTS) {
                    throw new IllegalStateException("payment_db affected rows mismatch! Rolling back.");
                }
                payConn.commit();
                System.out.println("  [payment_db] COMMITTED successfully!");
            } catch (Exception e) {
                payConn.rollback();
                throw e;
            }
        }

        System.out.println("\n>>> ALL TEST RECORDS CLEANED AND COMMITTED SUCCESSFULLY!");
    }
}
