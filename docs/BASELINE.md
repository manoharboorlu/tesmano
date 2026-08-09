# TesMano Phase 0 — Baseline Decision

Audit date: 2026-08-09

## Decision

Use MateDroid upstream `main` at `9333a14521e232147df4f47e72cccff68531ad10` as the TesMano code baseline. The local TesMano `main` is that source commit plus the repository-setup commit `a7c81df53451478aef1d3be32a58de25a64cf263`.

This is safer than resetting to stable 1.10.0 because the five-day post-release delta contains one user-visible imperial-unit correctness fix, three measured performance changes relevant to multi-year history, and three chart/map interaction fixes. The delta adds no schema migration, API endpoint, dependency upgrade, visual redesign, or vehicle/timezone behavior change. The Phase 0 build gate will validate the integrated current-main state.

## Exact refs and ancestry

| Ref | Commit | Meaning |
|---|---|---|
| MateDroid tag object | `396a70e…` | Annotated `v1.10.0` tag object; not the source commit |
| MateDroid 1.10.0 source | `17620b5f7b5323bcfdd3ffa3a6cff7fe4d670a18` | `chore: release v1.10.0`, 2026-07-01 |
| Current `upstream/main` | `9333a14521e232147df4f47e72cccff68531ad10` | Merge of chart interaction fixes, 2026-07-06 |
| Fork setup commit | `a7c81df53451478aef1d3be32a58de25a64cf263` | Adds only `AGENTS.md` and `.vscode/tasks.json` |
| Local `main` before Phase 0 docs | `a7c81df53451478aef1d3be32a58de25a64cf263` | Parent is exactly current `upstream/main` |

The merge base of local `main` and `upstream/main` is `9333a145…`. There are no private/machine-local files in the setup commit. `~/.zshrc`, `local.properties`, `.env`, credentials, and keystores are not tracked.

The 1.10.0 release introduced drive/charge comparisons, trip numbering and maps, charge-cost editing through the TeslaMate web UI, redesigned details, and an AC/DC labeling fix. Current upstream contains 23 commits after that tag: 19 non-merge changes and four merge commits. The aggregate source delta is 58 files, +1,216/−1,812 lines.

## Post-1.10.0 classification

The classification below answers “what would we selectively take if we stayed on 1.10.0?” Because local `main` already contains the integrated and validated sequence, the recommendation is to retain current upstream rather than replay these commits individually.

### BACKPORT NOW

| Commit | Change | Why |
|---|---|---|
| `8d56f2f` | Stop double-converting distance/speed for imperial users | Data correctness; directly relevant in America/New_York deployments that use miles |
| `2b1d6c3` | Lifecycle-aware state collection, off-main stats, list `contentType` | Reduces off-screen polling/recomposition and main-thread work |
| `214ca71` | Hoist chart paints, pre-sort overlays, memoize mileage charts | Directly reduces chart work on large histories |
| `047c8ac` | Move comparison/trip CPU off main; cheaper charge sync lookup and route simplification | Directly addresses years-of-history workloads and map rendering |
| `47228bc` | Correct comparison tooltip/crosshair behavior | Chart usability correctness |
| `6e197d2` | Allow vertical page scroll to win over single-finger chart gestures | Required for usable detail screens, especially on the folded display |
| `e1db0ce` | Require two-finger pan on embedded maps | Prevents maps from trapping page scroll; aligns embedded maps with trip detail |

### USE LATER

These are sound cleanup/extraction changes but do not independently justify moving a reliability baseline: `fa4c6ea` (dead code), `e7bb2c1` (energy/cost formatting), `c4972cb` (shared leaf composables), `0bfef19` (chart label/data deduplication), `7a9906b` (worker notifier), `e1636a3` (fullscreen chart frame), and `916355e` (country/region sorting).

They are already present in the recommended base. Avoid reworking them during correctness phases unless a Phase 1–4 change naturally touches the same code.

### NOT RELEVANT

`9877ad4` and `ef8ff71` only add changelog notes. They carry no runtime behavior.

### RISKY AS SELECTIVE BACKPORTS

| Commit | Risk |
|---|---|
| `f0998ea` | Collapses all 12 repository wrappers into a generic mapper; broad error/null behavior surface for no required feature |
| `4547c22` | Merges date-filter and chart-granularity enums across screens; dependent source changes make isolated replay unattractive |
| `07107fc` | Replaces per-screen chart binning with a shared implementation; changes several analytics paths at once |

These commits are not known-bad. The risk is in cherry-picking them out of their tested sequence. Keeping current upstream preserves their integrated context; reverting to stable and selectively replaying them would create more risk than it removes.

## Meaningful upstream findings

- Correctness: only the imperial double-conversion fix; no timezone or vehicle-generation correction landed after 1.10.0.
- Performance: meaningful off-main work, lifecycle collection, chart memoization, route simplification, and removal of per-charge summary lookup.
- Interaction: chart scroll arbitration, map two-finger pan, and comparison tooltip fixes.
- API compatibility: no endpoint/model/version negotiation changes.
- Security: no credential, TLS, backup, logging, or release-signing hardening.
- Vehicle images: no PN01/P74D/generation separation improvement.
- Dependencies/database: no upgrade or Room schema change.

## Baseline rule going forward

Anchor TesMano work to `9333a145…` and preserve the setup commit above it. Do not merge a moving `upstream/main` automatically. Review future upstream commits individually against the same categories, cherry-pick only evidence-backed fixes, and run the full test/lint/debug-assembly gate after each intake batch.
