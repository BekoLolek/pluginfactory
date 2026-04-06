package com.bekololek.pluginfactory.marketplace;

import org.springframework.data.jpa.domain.Specification;

public final class MarketplaceListingSpecification {

    private MarketplaceListingSpecification() {
        // utility class
    }

    public static Specification<MarketplaceListing> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
    }

    public static Specification<MarketplaceListing> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<MarketplaceListing> hasMinecraftVersion(String minecraftVersion) {
        return (root, query, cb) -> cb.equal(root.get("minecraftVersion"), minecraftVersion);
    }

    public static Specification<MarketplaceListing> searchTitleOrDescription(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}
