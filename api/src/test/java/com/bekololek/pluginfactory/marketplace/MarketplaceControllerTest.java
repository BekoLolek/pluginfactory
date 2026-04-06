package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.auth.JwtAuthenticationFilter;
import com.bekololek.pluginfactory.auth.JwtService;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import com.bekololek.pluginfactory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketplaceController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class MarketplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private MarketplaceService marketplaceService;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private PurchaseService purchaseService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void listPlugins_isPublic() throws Exception {
        Page<MarketplaceListing> emptyPage = new PageImpl<>(List.of());
        when(marketplaceService.listPublicListings(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/marketplace/plugins"))
                .andExpect(status().isOk());
    }

    @Test
    void createPlugin_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/plugins")
                        .contentType("application/json")
                        .content("{\"title\":\"Test\",\"category\":\"UTILITY\",\"artifactId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlugin_isPublic() throws Exception {
        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(java.util.UUID.randomUUID());
        listing.setTitle("Test");
        listing.setCategory("UTILITY");
        listing.setStatus("ACTIVE");

        java.util.UUID listingId = java.util.UUID.randomUUID();
        when(marketplaceService.getListing(listingId)).thenReturn(listing);

        mockMvc.perform(get("/api/v1/marketplace/plugins/" + listingId))
                .andExpect(status().isOk());
    }

    @Test
    void getReviews_isPublic() throws Exception {
        java.util.UUID listingId = java.util.UUID.randomUUID();
        when(reviewService.getReviews(listingId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/marketplace/plugins/" + listingId + "/reviews"))
                .andExpect(status().isOk());
    }

    @Test
    void submitReview_requiresAuth() throws Exception {
        java.util.UUID listingId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/api/v1/marketplace/plugins/" + listingId + "/reviews")
                        .contentType("application/json")
                        .content("{\"rating\":5,\"comment\":\"Great!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void purchasePlugin_requiresAuth() throws Exception {
        java.util.UUID listingId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/api/v1/marketplace/plugins/" + listingId + "/purchase"))
                .andExpect(status().isUnauthorized());
    }
}
