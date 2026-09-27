package org.hvtra.catalog.repository.projection;

import java.util.UUID;

/** Projection id + name cho các API lookup (dropdown). */
public interface IdNameView {
    UUID getId();
    String getName();
}
