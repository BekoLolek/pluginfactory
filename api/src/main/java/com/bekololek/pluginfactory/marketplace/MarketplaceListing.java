package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "marketplace_listings")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceListing extends BaseEntity {

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "minecraft_version", length = 20)
    private String minecraftVersion;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "average_rating", nullable = false, columnDefinition = "DECIMAL(3,2)")
    private double averageRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
