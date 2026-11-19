package org.hvtra.customer.taste;

import java.util.Map;
import java.util.Set;

interface TasteTaxonomyAuthority {
    Map<String, Attribute> requirePublishedRelease(String version, String sha256);

    record Attribute(int maxSelections, Set<String> values) {
    }
}
