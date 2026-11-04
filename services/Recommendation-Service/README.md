# Recommendation-Service

FastAPI recommendation serving uses the existing V1 TF-IDF Content + Item-Item CF
artifact. AI report workflows are documented in [AI_REPORTS.md](AI_REPORTS.md).

POS Product V1 integration and the authenticated, snapshot-free guest Popularity
endpoint are documented in [POS_RECOMMENDATION.md](POS_RECOMMENDATION.md).
Source implementation does not imply runtime configuration or E2E verification.

Serving now supports explicit **DISCOVERY** (never-purchased products) and **REPEAT**
(purchased-only products), each capped at five. Authenticated requests go through
`GET /api/recommendation/recommendations/{customer_uuid}?mode=DISCOVERY|REPEAT&limit=5`
at Gateway. See [RECOMMENDATION_API.md](RECOMMENDATION_API.md) for ownership, HMAC v2,
replay protection, frontend examples and operator prerequisites. Missing identity
mapping/security configuration fails closed.

## Approved interim V1 routing

The online policy uses `MIN_HYBRID_PURCHASE_ORDERS = 2` in
`app/service/recommendation_service.py`:

| Completed purchase orders | Route |
|---|---|
| 0 | Popularity |
| 1 | Popularity |
| 2 or more (including 4 and 5) | Existing TF-IDF Hybrid60 |

This replaces the prior rule (0–4 Popularity, ≥5 Hybrid). It is an explicitly
approved interim business rule, not a statistically proven optimal threshold.
There is no separate Content-Based route.

Eligibility remains `COUNT(DISTINCT order_id)` for the customer's PURCHASE events
with non-NULL, nonblank real order IDs. The completed/paid order ingestion contract
supplies PURCHASE events; routing adds no order-status query or cross-service join.
Multiple lines in one order count once. RETURN events and missing order IDs do not
increase the order count. Missing-order PURCHASEs retain their existing product
preference and exclusion semantics.

Hybrid mathematics are unchanged: 60% Content + 40% Item-Item CF, component min-max
normalization after candidate exclusion, and descending score/ascending product ID
tie ordering. `exclude_purchased=true` remains the API default; callers can use
false to allow repurchases. GET `/recommendations/{customer_id}?limit=5` requests
Top-5; the existing default limit is ten and maximum fifty. Response fields and
model contract version are retained; nullable `mode` is added. These 10/50 limits
apply to legacy requests without mode. Legacy false allows purchases, while explicit
REPEAT requires them. No mode mixing or opposite-set padding occurs in fallback.

## Runtime caveats

The new route requires the same compatible artifact and matching catalog as before.
Missing/corrupt/incompatible artifact or catalog mismatch uses the existing
Popularity fallback (`source=FALLBACK`, `V1_MODEL_NOT_AVAILABLE`). Both flat
components use `BOTH_COMPONENTS_FLAT`; empty candidates remain empty without padding.
Database errors retain their existing behavior. Reaching two orders makes a customer
eligible for Hybrid; it does not guarantee a HYBRID response when fallback is needed.

Restart/redeploy API processes to load the changed constant. This source change
does not launch production services or provision operator secrets/identity mapping,
Redis, artifact deployment or ingestion readiness. Authenticated Gateway serving
and backend authorization are implemented; runtime prerequisites are in the API
contract. No model
refresh is required solely for this threshold change; artifact loading and scoring
are unchanged.

## Tests and historical evaluation

Run with the existing virtual environment from this service directory:

```powershell
.venv/Scripts/python.exe -B -m unittest discover -s tests
.venv/Scripts/python.exe -B -m unittest discover -s offline_benchmark/tests
```

Tests use isolated SQLite/mocks/temporary model fixtures. Use child-process
placeholder/loopback settings for external dependencies; shared MySQL/Kafka writes
are not needed. Explicitly set `APP_ENVIRONMENT=test`, `INTERNAL_REPLAY_BACKEND=memory`
and a nondefault isolated HMAC secret for tests; production defaults fail closed.

[EXACT_TFIDF.md](offline_benchmark/EXACT_TFIDF.md) describes the causal benchmark.
Its preserved five-order baseline and saved results refer to the earlier policy;
they are not retroactively relabeled as evaluation of this approved two-order rule.
