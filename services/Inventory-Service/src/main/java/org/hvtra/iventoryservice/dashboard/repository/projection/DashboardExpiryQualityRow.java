package org.hvtra.iventoryservice.dashboard.repository.projection;

public interface DashboardExpiryQualityRow {
    Long getExpiry7Days();
    Long getExpiry30Days();
    Long getExpiredUnhandled();
    Long getQuarantineOverSla();
    Long getPendingDisposalApprovals();
}
