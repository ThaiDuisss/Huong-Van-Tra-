package org.hvtra.iventoryservice.location.repository.projection;

import java.util.UUID;

/** Projection id + name cho API lookup (dropdown). */
public interface IdNameView {
    UUID getId();
    String getName();
}
