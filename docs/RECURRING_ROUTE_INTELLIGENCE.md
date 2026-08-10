# Recurring Route Intelligence

Phase 7A derives recurring routes entirely on-device from cached drive summaries and already-known drive endpoints. It never requests drive details to discover routes and does not change TeslaMate, TeslaMateApi, PostgreSQL, Oracle, Fleet API access, authentication, or sync.

## Route identity

Routes are directional: `A → B` and `B → A` are distinct. A Smart Place is preferred whenever an endpoint falls inside an enabled place; the stable Smart Place ID becomes the endpoint identity. This makes small parking-position variation at a saved place resolve consistently.

Endpoints that do not match a Smart Place are clustered locally with a dedicated 200 m tolerance. Smart Place radii are intentionally not reused: a broad 1–2 km place radius must not merge unrelated destinations. Endpoint clustering uses spatial buckets and nearby candidates rather than pairwise history comparison, so discovery remains practical for large histories.

Route observations are derived every time from current places and local coordinates. Editing, disabling, or deleting a Smart Place does not alter raw drive history; route derivation naturally reclassifies the endpoints. User-confirmed route names and dismissals are local metadata with coordinate anchors so they survive ordinary endpoint re-derivation.

## Suggestions and coverage

A route is suggested only after at least three completed occurrences. Drives without both locally known endpoints are excluded rather than causing eager detail downloads. The Routes screen reports endpoint coverage, so users can see the portion of local history on which discovery is based.

## Commute and tags

Recurring frequency never creates a Commute classification. Home ↔ Work remains the sole automatic Commute rule and existing manual Commute assign/remove overrides remain authoritative.

`COMMUTE` is a derived system tag. User tags are local, reusable, manually assigned, and multi-value per drive. Activity and Drive Detail keep their provenance distinct: removing a user tag cannot remove the system Commute result. Phase 7A does not infer a semantic purpose such as Gym, School, Grocery, Airport, or Work from location, timing, or driving behavior. Naming a route is always an explicit user action.

## Scope limits

Phase 7A reports endpoint-based route count, distance, average duration, most-recent occurrence, and weighted energy efficiency. It does not compare GPS shapes, build canonical polylines, predict future trips, or automatically enable route-to-tag rules. Those may be considered in a later, separately approved phase.
