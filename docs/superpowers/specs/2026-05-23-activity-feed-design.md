# Activity Feed — Design Spec

**Date:** 2026-05-23  
**Status:** Approved

---

## Problem

Players have no visibility into what has happened in the league. Steals, trades, bench swaps, match
results, tier changes and coin movements occur in isolation — there is no shared history. Members
must ask each other or check individual pages to understand the current state of the league.

## Goal

A chronological activity log visible to all league members showing every significant event, updated
automatically while the page is open.

---

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| UI placement | Dedicated page `/leagues/:id/activity` | Full reading experience, accessible from nav |
| Real-time | Polling every 30 s (React Query `refetchInterval`) | SSE adds infra complexity; polling sufficient for a feed |
| Coin movements | Public with exact amount | Agreed by user |
| Filters | None — chronological list only | YAGNI; types are color-coded for quick scanning |
| Pagination | "Load more" button (page state local to component) | Simple, avoids URL complexity |
| Storage | New `activity_events` MongoDB collection | Fast reads, future-proof for SSE, follows existing patterns |

---

## Data Model

### `ActivityEventEntity` (collection: `activity_events`)

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `id` | String | No | MongoDB `_id` |
| `leagueId` | String | No | League this event belongs to |
| `type` | Enum | No | `STEAL`, `BENCH_SWAP`, `TRADE_COMPLETED`, `MATCH_RESULT`, `TIER_CHANGE`, `COIN_EARNED` |
| `actorUsername` | String | No | Who performed the action |
| `targetUsername` | String | Yes | Victim or counterpart (steal victim, trade partner) |
| `pokemonName` | String | Yes | Primary pokemon involved |
| `pokemonName2` | String | Yes | Second pokemon (bench swaps, trades) |
| `coinsAmount` | Integer | Yes | Coins paid or earned |
| `fromTier` | String | Yes | Previous tier (tier changes only) |
| `toTier` | String | Yes | New tier (tier changes only) |
| `roundNumber` | Integer | Yes | Jornada number (match results only) |
| `createdAt` | Instant | No | Event timestamp |

### Index

`{ leagueId: 1, createdAt: -1 }` — covers the primary query (filter by league, sorted by date).

---

## API Contract

### `GET /v1/leagues/{leagueId}/activity?page=0&size=20`

- Auth: Bearer token required
- Any league member can call this endpoint

**Response 200:**
```json
{
  "events": [
    {
      "id": "abc123",
      "type": "STEAL",
      "actorUsername": "ash",
      "targetUsername": "brock",
      "pokemonName": "Pikachu",
      "coinsAmount": 120,
      "createdAt": "2026-05-23T18:30:00Z"
    },
    {
      "id": "abc124",
      "type": "TRADE_COMPLETED",
      "actorUsername": "misty",
      "targetUsername": "ash",
      "pokemonName": "Psyduck",
      "pokemonName2": "Bulbasaur",
      "createdAt": "2026-05-23T13:15:00Z"
    }
  ],
  "page": 0,
  "totalPages": 4,
  "hasMore": true
}
```

---

## Backend Architecture

Follows existing CQRS/Mediator pattern (`XCommand` → `XCommandHandler` → `XFacade` → Controller).

### New files

```
domain/
  ActivityEventEntity.java          — @Document("activity_events")
  ActivityEventRepository.java      — interface, findByLeagueIdOrderByCreatedAtDesc(id, Pageable)
  ActivityFeedResponse.java         — response DTO with events list + pagination
  ActivityEventType.java            — enum STEAL | BENCH_SWAP | TRADE_COMPLETED | MATCH_RESULT | TIER_CHANGE | COIN_EARNED

infrastructure/
  ActivityEventMongoRepository.java — MongoTemplate implementation

application/
  GetActivityFeedCommand.java       — record(String leagueId, int page, int size)
  GetActivityFeedCommandHandler.java
  ActivityFeedFacade.java

api-rest/
  ActivityController.java           — GET /v1/leagues/{id}/activity
```

### Handlers to modify (add activity event write on success)

| Handler | Event emitted | Key fields |
|---------|--------------|------------|
| `StealPokemonCommandHandler` | `STEAL` | actor=thief, target=victim, pokemonName, coinsAmount=price paid |
| `SwapWithBenchCommandHandler` | `BENCH_SWAP` | actor, pokemonName=team pokemon, pokemonName2=bench pokemon, coinsAmount=net change (nullable) |
| `RespondToTradeCommandHandler` | `TRADE_COMPLETED` (only on ACCEPTED) | actor=proposer, target=receiver, pokemonName=proposed, pokemonName2=requested |
| `RecordMatchResultCommandHandler` | `MATCH_RESULT` + 2x `COIN_EARNED` | roundNumber, actor=winner, target=loser; separate COIN_EARNED events for each player |
| `AssignTierCommandHandler` | `TIER_CHANGE` | actor=admin, pokemonName, fromTier, toTier |

---

## Frontend Architecture

### New files

```
src/api/activity.ts       — ActivityEvent interface, ActivityFeedResponse, getActivityFeed()
src/pages/ActivityPage.tsx — chronological feed page
```

### Modified files

```
src/router.tsx (or equivalent) — add /leagues/:leagueId/activity route
League nav component           — add "Actividad" link
```

### ActivityPage behavior

- `useQuery` with `refetchInterval: 30_000` and `staleTime: 20_000`
- Local `page` state starting at 0; "Cargar más" increments it and appends results
- `formatEvent(event)` helper maps each type to a human-readable string and a left-border color:

| Type | Color | Text template |
|------|-------|---------------|
| `STEAL` | `#10b981` (green) | `{actor} robó a {pokemon} de {target} · pagó {coins} monedas` |
| `BENCH_SWAP` | `#8b5cf6` (purple) | `{actor} swap de banca: {pokemon} ↔ {pokemon2}` |
| `TRADE_COMPLETED` | `#3b82f6` (blue) | `{actor} y {target} completaron un trade: {pokemon} ↔ {pokemon2}` |
| `MATCH_RESULT` | `#eab308` (yellow) | `{actor} ganó a {target} en jornada {round}` |
| `TIER_CHANGE` | `#ec4899` (pink) | `{pokemon} cambiado de tier {fromTier} → {toTier}` |
| `COIN_EARNED` | `#f59e0b` (amber) | `{actor} ganó +{coins} monedas` |

---

## Out of Scope (v1)

- Real-time push (SSE / WebSocket) — can be added later by publishing from the same write point
- Filters by event type
- Draft picks in the feed (already visible in the draft history page)
- Deleting or editing events
