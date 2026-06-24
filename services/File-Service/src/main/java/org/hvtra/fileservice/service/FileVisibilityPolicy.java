package org.hvtra.fileservice.service;

import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileVisibility;

import java.util.Set;

public class FileVisibilityPolicy {

    private static final Set<FilePurpose> PUBLIC_PURPOSES = Set.of(
            FilePurpose.PRODUCT_IMAGE,
            FilePurpose.ITEM_IMAGE,
            FilePurpose.CATEGORY_IMAGE,
            FilePurpose.PROMOTION_IMAGE,
            FilePurpose.BANNER_IMAGE
    );

    public static FileVisibility resolveVisibility(FilePurpose purpose) {
        return PUBLIC_PURPOSES.contains(purpose)
                ? FileVisibility.PUBLIC
                : FileVisibility.PRIVATE;
    }
}