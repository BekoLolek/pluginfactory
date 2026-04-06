# BekoLolek Plugin Factory — Remaining Fixes

> **For Claude Code**: Work through each section below. Every item is a concrete gap found during audit against `IMPLEMENTATION_PLAN.md`. No stubs — implement fully.

---

## 1. Backend Logic Gaps

### 1.1 StripeService: Replace full-table scan

**File**: `api/src/main/java/com/bekololek/pluginfactory/subscription/StripeService.java`

The method `findUserIdByStripeSubscriptionId()` calls `findAll().stream().filter()` — O(n) scan on every webhook.

**Fix**: Add a query method to `SubscriptionRepository`:

```java
Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
```

Then replace the `findUserIdByStripeSubscriptionId` method body to use it.

### 1.2 BuildSessionService: Add workspaceId support (Phase 18 requirement)

**File**: `api/src/main/java/com/bekololek/pluginfactory/build/BuildSessionService.java`  
**File**: `api/src/main/java/com/bekololek/pluginfactory/build/BuildSession.java`

Builds cannot be linked to team shared workspaces. The plan says:

> "Allow optional workspaceId when creating builds. Workspace builds visible to all team members."

**Fix**:
- Add `workspaceId` (UUID, nullable) column to `BuildSession` entity
- Add a Flyway migration (V9) with: `ALTER TABLE build_sessions ADD COLUMN workspace_id UUID REFERENCES shared_workspaces(id);`
- Update the create-build logic to accept optional `workspaceId`
- Add a query: `List<BuildSession> findByWorkspaceId(UUID workspaceId)` to `BuildSessionRepository`
- In `BuildSessionService`, when fetching builds, also allow access if the user is a member of the workspace's team

---

## 2. Missing Frontend Pages (Phase 14 + 19)

All pages below should follow the existing patterns: dark slate theme, loading skeletons, error states, empty states. Use existing hooks and API functions where available.

### 2.1 PluginDetailPage

**File to create**: `web/src/pages/PluginDetailPage.tsx`  
**Route**: `/dashboard/marketplace/:id`

- Fetch listing by ID
- Show title, description, MC version, category, seller, download count, average rating
- Download/purchase button (free: direct, paid: disabled with "coming soon")
- Reviews section: list existing reviews, submit form (1-5 star rating + comment) if user has purchased
- Back link to marketplace

### 2.2 PublishPluginPage

**File to create**: `web/src/pages/PublishPluginPage.tsx`  
**Route**: `/dashboard/marketplace/publish`

- Step 1: Select a completed build (dropdown of user's COMPLETED builds with artifacts)
- Step 2: Fill metadata (title, short description, full description, category dropdown, MC version, price in cents — 0 for free)
- Step 3: Review & publish button
- Call `POST /api/v1/marketplace/listings`

### 2.3 MyListingsPage

**File to create**: `web/src/pages/MyListingsPage.tsx`  
**Route**: `/dashboard/marketplace/my-listings`

- Fetch `GET /api/v1/marketplace/listings/mine`
- Card grid showing title, downloads, rating, status
- Edit button → inline edit or modal for title/description/price
- Remove button with confirmation

### 2.4 MyPurchasesPage

**File to create**: `web/src/pages/MyPurchasesPage.tsx`  
**Route**: `/dashboard/marketplace/my-purchases`

- Fetch `GET /api/v1/marketplace/purchases/mine`
- List of purchased plugins with download link and date
- Link to leave a review if not yet reviewed

### 2.5 SharedWorkspacePage

**File to create**: `web/src/pages/SharedWorkspacePage.tsx`  
**Route**: `/dashboard/teams/:teamId/workspaces/:workspaceId`

- Show workspace name, description, created by
- List builds in this workspace (requires workspaceId query from fix 1.2)
- "New Build in Workspace" button

---

## 3. Missing Routes in App.tsx

**File**: `web/src/App.tsx`

Add these routes inside the `/dashboard` layout:

```tsx
<Route path="marketplace/:id" element={<PluginDetailPage />} />
<Route path="marketplace/publish" element={<PublishPluginPage />} />
<Route path="marketplace/my-listings" element={<MyListingsPage />} />
<Route path="marketplace/my-purchases" element={<MyPurchasesPage />} />
<Route path="teams" element={<TeamDashboardPage />} />
<Route path="teams/:teamId" element={<TeamDetailPage />} />
<Route path="teams/:teamId/workspaces/:workspaceId" element={<SharedWorkspacePage />} />
```

Also add the imports for all new page components.

---

## 4. Missing API Client Functions

### 4.1 `web/src/api/marketplace.ts`

Ensure these functions exist (some may already be there, add what's missing):

- `getListingById(id: string)` → `GET /api/v1/marketplace/plugins/{id}`
- `createListing(data)` → `POST /api/v1/marketplace/listings`
- `updateListing(id, data)` → `PUT /api/v1/marketplace/listings/{id}`
- `deleteListing(id)` → `DELETE /api/v1/marketplace/listings/{id}`
- `getMyListings()` → `GET /api/v1/marketplace/listings/mine`
- `getMyPurchases()` → `GET /api/v1/marketplace/purchases/mine`
- `purchaseFree(listingId)` → `POST /api/v1/marketplace/purchases/{listingId}/free`
- `getReviews(listingId)` → `GET /api/v1/marketplace/reviews/{listingId}`
- `submitReview(listingId, rating, comment)` → `POST /api/v1/marketplace/reviews/{listingId}`

### 4.2 `web/src/hooks/useMarketplace.ts`

Add TanStack Query hooks for: `useListing(id)`, `useMyListings()`, `useMyPurchases()`, `useReviews(listingId)`, `useCreateListing()`, `useSubmitReview()`, `usePurchaseFree()`, `useDeleteListing()`.

---

## 5. Missing Sidebar Navigation

**File**: `web/src/components/Sidebar.tsx`

Add nav links for:
- Teams (`/dashboard/teams`)

Verify the Marketplace link points to `/dashboard/marketplace`.

---

## 6. Missing Config Files

### 6.1 web/vercel.json

**File to create**: `web/vercel.json`

```json
{
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "framework": "vite",
  "rewrites": [
    { "source": "/(.*)", "destination": "/" }
  ]
}
```

### 6.2 Root Makefile

**File to create**: `Makefile` (project root)

```makefile
.PHONY: dev test build deploy-staging deploy-prod

dev:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

test:
	cd api && ./mvnw clean verify
	cd web && npm run lint && npm run build

build:
	docker compose build

deploy-staging:
	./infra/scripts/deploy.sh $(STAGING_HOST) develop

deploy-prod:
	./infra/scripts/deploy.sh $(PRODUCTION_HOST) main
```

---

## 7. CI/CD Gaps

### 7.1 deploy-production.yml — Make it real

**File**: `.github/workflows/deploy-production.yml`

Replace the echo placeholder with actual SSH deploy:

```yaml
jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4
      - name: Setup SSH
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.PRODUCTION_SSH_KEY }}" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key
          ssh-keyscan -H ${{ secrets.PRODUCTION_HOST }} >> ~/.ssh/known_hosts
      - name: Deploy
        run: |
          ssh -i ~/.ssh/deploy_key ${{ secrets.PRODUCTION_HOST }} "cd /opt/pluginfactory && git pull origin main && docker compose build --no-cache && docker compose up -d --remove-orphans"
      - name: Health check
        run: |
          for i in $(seq 1 24); do
            STATUS=$(ssh -i ~/.ssh/deploy_key ${{ secrets.PRODUCTION_HOST }} "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health" 2>/dev/null || echo "000")
            [ "$STATUS" = "200" ] && echo "Healthy" && exit 0
            sleep 5
          done
          echo "Health check failed" && exit 1
      - name: Discord notification
        if: always()
        run: |
          STATUS=${{ job.status }}
          curl -H "Content-Type: application/json" \
            -d "{\"content\": \"Production deploy: $STATUS\"}" \
            ${{ secrets.DISCORD_WEBHOOK_URL }} || true
```

### 7.2 deploy-staging.yml — Same pattern

**File**: `.github/workflows/deploy-staging.yml`

Same structure as production but triggered on push to `develop`, targeting `${{ secrets.STAGING_HOST }}`.

### 7.3 backend-ci.yml — Add JaCoCo coverage gate

Add to `api/pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Start with 60% minimum (not 80%) — can increase later once test coverage improves.

---

## 8. Accessibility Pass (Phase 19)

Across all interactive components, add:

- `aria-label` on icon-only buttons (back arrows, close buttons, edit icons)
- `role="alert"` on error messages and toast notifications
- `aria-live="polite"` on the chat message container and build progress panel
- `aria-busy="true"` on loading skeletons
- Keyboard nav: all interactive elements must be focusable and usable via Enter/Space
- Skip-to-content link at top of `DashboardLayout.tsx`

Priority files: `Sidebar.tsx`, `ChatInput.tsx`, `BuildProgressPanel.tsx`, `PlanReviewPanel.tsx`, `NotificationToast.tsx`, `DashboardLayout.tsx`.

---

## Checklist

- [ ] 1.1 StripeService query fix
- [ ] 1.2 BuildSession workspaceId + migration V9
- [ ] 2.1 PluginDetailPage
- [ ] 2.2 PublishPluginPage
- [ ] 2.3 MyListingsPage
- [ ] 2.4 MyPurchasesPage
- [ ] 2.5 SharedWorkspacePage
- [ ] 3 App.tsx routes
- [ ] 4.1 Marketplace API functions
- [ ] 4.2 Marketplace hooks
- [ ] 5 Sidebar nav (Teams link)
- [ ] 6.1 vercel.json
- [ ] 6.2 Makefile
- [ ] 7.1 deploy-production.yml (real)
- [ ] 7.2 deploy-staging.yml (real)
- [ ] 7.3 JaCoCo plugin in pom.xml
- [ ] 8 Accessibility pass
