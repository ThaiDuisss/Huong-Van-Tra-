# Recommendation API and security contract

## Modes and frontend requests

Gateway endpoints (port/address from the frontend's existing API client):

```text
GET /api/recommendation/recommendations/{customer_uuid}?mode=DISCOVERY&limit=5
GET /api/recommendation/recommendations/{customer_uuid}?mode=REPEAT&limit=5
Authorization: Bearer <existing Keycloak access token>
```

Gateway strips `/api/recommendation` and forwards to port 8090 using
`RECOMMENDATION_SERVICE_URL`. Only GET in this namespace is routed. Internal catalog
sync is never publicly routed and Gateway explicitly denies internal namespaces.
The frontend uses the existing `@hvt/api-client`/Keycloak flow. Do not send identity,
role, HMAC or customer-ownership headers from browser code. No UI pages changed.

```typescript
// Use the feature's relative import of src/api/api-client and existing token handling.
// That client already has /api in its base URL and uses `query`, not Axios `params`.
const discovery = await apiClient.get<RecommendationResponse>(
  `/recommendation/recommendations/${encodeURIComponent(customerId)}`,
  { query: { mode: 'DISCOVERY', limit: 5 } },
);
const repeat = await apiClient.get<RecommendationResponse>(
  `/recommendation/recommendations/${encodeURIComponent(customerId)}`,
  { query: { mode: 'REPEAT', limit: 5 } },
);
```

DISCOVERY candidates = active/nondeleted product catalog minus every product with a
PURCHASE event for that customer. REPEAT candidates = that catalog intersect those
purchased product IDs. RETURN does not change membership; PURCHASEs with missing
order IDs still retain product membership. There is no stock/branch availability
projection added here. Both modes cap at five unique products, never pad from the
opposite candidate set, and can return fewer than five or zero.

Routing remains 0–1 distinct valid PURCHASE orders → global Popularity; ≥2 → existing
Hybrid60. Duplicate lines count once per real order. REPEAT Popularity uses existing
global purchase counts, not personal purchase frequency. All Hybrid/fallback paths
apply the same candidate set before ranking and component normalization. An eligible
singleton may normalize both components to flat and use the existing safe fallback.

The response keeps the existing DTO fields and adds nullable `mode`. Example shape
from the actual DTO (fixture values, not production customer data):

```json
{
  "customerId": "33333333-3333-4333-8333-333333333333",
  "source": "HYBRID",
  "fallbackReason": null,
  "modelType": "personalized_hybrid",
  "modelContractVersion": "hvtra-personalized-hybrid-v1-60-40-tfidf",
  "trainedAt": "2026-10-11T22:59:30.346481Z",
  "items": [
    {
      "rank": 1,
      "productId": "a",
      "productCode": "A",
      "name": "Lotus tea",
      "score": 0.6
    },
    {
      "rank": 2,
      "productId": "c",
      "productCode": "C",
      "name": "Ginger herbal",
      "score": 0.4
    }
  ],
  "purchaseOrderCount": 2,
  "excludePurchased": false,
  "mode": "REPEAT"
}
```

Actual API-to-service smoke response examples are recorded with the task evidence;
`trainedAt` comes from the loaded artifact. Popularity uses raw counts rather than
probabilities; fallback retains `source=FALLBACK` and its existing reason. An empty
REPEAT response for zero purchases has `source=POPULARITY` and `items=[]`.

## Legacy migration

No `mode` means legacy behavior: `exclude_purchased` defaults true, while false
allows both purchased and unpurchased products. False is NEVER reinterpreted as
purchased-only REPEAT. Legacy default/max limits remain 10/50; explicit modes cap at
five. Legacy responses use `mode=null`; existing fields are retained.

`DISCOVERY&exclude_purchased=true` is redundant but compatible. DISCOVERY+false and
REPEAT with either legacy flag return 422. New callers should use mode alone.
Authentication is mandatory for legacy queries too. Former unsigned calls and
four-header-only contexts must migrate to Gateway or the signed v2 internal context.

Historical benchmark “repeat allowed” is not purchased-only REPEAT. The historical
Hybrid60 Recall@5 48.36% does NOT measure this new mode. Saved evidence is unchanged.

## Ownership and JWT boundary

Gateway retains its existing Keycloak JWT issuer/audience/timestamp/signature
validation. See [Spring Security's resource-server contract](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html).
It replaces browser-supplied identity/context headers with values derived from the
verified JWT and signs the downstream context.

Customer-Service currently has no subject-to-customer ownership field/endpoint.
Subject equality, email and phone are NOT used as ownership proof. An operator must
configure `RECOMMENDATION_CUSTOMER_ID_CLAIM` at Gateway with an authoritative,
server-managed Keycloak claim mapper returning the actual Customer UUID. This claim
must not be self-editable by customers. Empty/missing mapping is signed as empty and
the backend returns 503, rather than assuming JWT subject is the customer ID.

Recommendation-Service verifies both signatures, token-expiry assertion, freshness,
nonce, HTTP method, downstream raw path and raw query. It parses the trusted UUID
mapping and requires target equality. Cross-customer access is 403; missing/forged/
expired authentication is 401. Missing security/mapping/replay configuration is
fail-closed. No arbitrary `X-Customer-ID`, `X-User-ID` or role header is trusted.

Staff cross-customer access is DISABLED by default. `RECOMMENDATION_STAFF_ROLES`
can grant it only after an explicit operational/business authorization decision;
supported existing role names are SUPER_ADMIN, ADMIN, MANAGER, SALE_STAFF. Do not
configure this merely to work around a missing customer mapper. Internal recommendation
callers are also disabled by default; exact service names must be approved in
`RECOMMENDATION_INTERNAL_CALLERS` and present a signed `service:<name>` / SYSTEM context.

## Compatible HMAC v2 extension and catalog synchronization

The existing four-line signature remains unchanged:

```text
X-Signature = HMAC-SHA256(secret, user_id + "\n" + roles + "\n" + request_id + "\n" + authorization)
```

New recommendation/sync requests additionally require `X-Context-Signature` over
the following newline-joined UTF-8 fields, in this exact order:

```text
hvtra-internal-v2
X-User-ID
X-User_Roles
X-Request-ID
Authorization
X-Internal-Timestamp
X-Internal-Nonce
X-Customer-ID
X-Token-Expires-At
HTTP method (uppercase)
downstream raw path
raw query (empty string when absent)
```

Timestamp is UTC epoch seconds; nonce is a new UUID per request. Human expiry is the
validated JWT exp epoch seconds; internal services use expiry 0 and empty customer
mapping, with `Authorization: Bearer service:<exact-name>`. Signed headers must occur
exactly once. Default timestamp skew is ±60 seconds. Replay keys are atomically
accepted once with TTL 121 seconds (2×skew+1), across all API workers/instances using
the SAME Redis namespace/store. Redis failure returns 503 without a memory fallback.
This uses [Redis SET NX EX](https://redis.io/docs/latest/commands/set/).

POST `/internal/catalog/sync` requires this v2 context and a SYSTEM service identity
in `CATALOG_SYNC_INTERNAL_CALLERS` (default exact source application name `catalog-Service`). Human/staff
contexts are denied. Existing Java four-line signatures alone do not provide replay
protection and are rejected here; upgrade the authorized sync caller with the v2
extension. No existing Catalog caller to this endpoint was found, so no speculative
Catalog-Service source changes were made. Actual production sync was not executed.

The Python `generate_internal_auth_headers` helper supports optional method/path/query
arguments to produce both signatures. Its default four-line outbound File/Catalog
behavior remains compatible. Operators can test it in an isolated environment:

```python
headers = generate_internal_auth_headers(
    user_id="service:catalog-Service", roles="SYSTEM",
    authorization="Bearer service:catalog-Service",
    method="POST", path="/internal/catalog/sync",
)
# Use these headers only for an explicitly authorized sync; do not print them.
```

## Configuration and deployment prerequisites

The new `.env.example` contains keys/placeholders, not credentials. Real `.env` and
`recommend.env` are untouched; secrets are not copied automatically. Load approved
values into the child process through the deployment's secure configuration path.

- API: legitimate DB settings, compatible existing NPZ path, explicit
  `INTERNAL_HEADER_SECRET`, and shared `INTERNAL_REPLAY_REDIS_URL`. Production is the
  default; secret must be at least 32 bytes and cannot be the old development default.
  Gateway must use the same secret via `security.internal.header-secret`
  (`SECURITY_INTERNAL_HEADER_SECRET` override), plus its existing Keycloak issuer/audience.
- All production API replicas must share the nonce store; protect its connectivity
  and access, and synchronize clocks. Memory replay is allowed ONLY with explicit
  `APP_ENVIRONMENT=development/test` and `INTERNAL_REPLAY_BACKEND=memory`, for isolated
  single-process workflows with an explicit nondefault test secret.
- Customer access additionally needs the authoritative claim mapper above. Staff/
  internal cross-customer privileges require a reviewed nonempty allowlist.
- Worker: separate process with legitimate DB/Kafka bootstrap/topics/group and
  manual commit settings. API unit tests require no Kafka connection. This task did
  not start a worker or change shared offsets.
- Previous local env inconsistencies remain operator work: select a reachable DB
  and broker explicitly. Do not combine env secrets automatically or use fallback
  credentials. Production Redis, Keycloak mapper, real secret agreement, artifact
  mounting and upstream Catalog availability still need deployment verification.

## Verification

Python tests run with isolated child settings and SQLite/mocks/synthetic temporary
artifact fixtures. Gateway tests include local RSA JWT verification, forged signature,
expiry, forwarded-header spoofing, cross-customer denial, status forwarding, route
rewrite and GET-only route configuration. A real FastAPI loopback fixture receives
signed requests from the Gateway security/relay test; no shared systems are contacted.

This smoke uses a minimal WebFlux test context and the actual security/relay classes,
with routing configuration checked separately. It is not a full production Gateway/
Keycloak/service-discovery deployment test. No new-mode quality benchmark or live
business E2E is claimed.
