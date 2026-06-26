package org.hvtra.fileservice.model.request;

import java.util.UUID;

public record FileAttach(  UUID fieldId,
        UUID refId) {

}
