# PokeFantasy — Diagramas del proyecto

> **Cómo actualizar**: dile a Claude "actualiza los diagramas" y describe el cambio.  
> Los diagramas se renderizan directamente en GitHub (abre este archivo desde el móvil/iPad).

---

## 1. Diagrama de entidades

Las entidades en **negrita** son colecciones MongoDB independientes.  
Los bloques en _cursiva_ son documentos embebidos (no tienen colección propia).

```mermaid
erDiagram
    UserEntity {
        String id PK
        String name "único"
        String password "bcrypt"
        List~String~ roles
    }
    Pokemons_embedded {
        int    id
        String name
        String leagueId FK
        Object stats
        List~String~ types
    }
    LeagueEntity {
        String id PK
        String name
        String createdBy FK
        String status "SETUP | ACTIVE"
    }
    LeagueMember_embedded {
        String username FK
        String leagueRole "ADMIN | USER"
        int    coinBalance
    }
    LeagueSettings_embedded {
        Integer coinsPerWin
        Integer coinsPerLoss
        Integer priceTierS
        Integer priceTierA
        Integer priceTierB
        Integer priceTierC
        Integer priceTierD
        String  seasonStartDate "YYYY-MM-DD"
        Integer maxTeamSize "default 20"
        Integer tierPctS "% pool en tier S (def 20)"
        Integer tierPctA "% pool en tier A (def 20)"
        Integer tierPctB "% pool en tier B (def 20)"
        Integer tierPctC "% pool en tier C (def 20)"
        Integer tierPctD "% pool en tier D (def 20)"
    }
    ClosedListEntity {
        String id PK
        String leagueId FK
        int    pokemonId
        String pokemonName
        String nominatedBy FK
        String tier "S|A|B|C|D"
        Object stats
        List~String~ types
        String sprite
    }
    DraftEntity {
        String id PK
        String leagueId FK
        String status "PENDING|IN_PROGRESS|COMPLETED|CANCELLED"
        List~String~ turnOrder
        int    currentTurnIndex
        int    currentRound
        Long   version "optimistic lock"
        List~DraftPick~ draftHistory "historial original, inmutable"
    }
    TradeEntity {
        String  id PK
        String  leagueId FK
        String  proposerUsername FK
        String  receiverUsername FK
        int     proposedPokemonId
        String  proposedPokemonName
        int     requestedPokemonId
        String  requestedPokemonName
        Integer coins "opcional"
        String  status "PENDING|ACCEPTED|REJECTED|CANCELLED"
        Instant createdAt
    }
    DraftPick_embedded {
        String  username FK
        String  pokemonName
        int     pokemonId
        int     round
        Instant pickedAt
        Integer customStealPrice "null = usa priceTierX"
        Integer lockedUntilRound "null = libre; N = robado en jornada N"
    }
    ScheduleEntity {
        String id PK
        String leagueId FK
    }
    Jornada_embedded {
        int    roundNumber
        String startDate "YYYY-MM-DD"
    }
    Match_embedded {
        String id "UUID"
        String player1 FK
        String player2 FK
        String winnerUsername
        String status "PENDING | COMPLETED"
    }

    UserEntity          ||--o{  Pokemons_embedded      : "pokemons (por liga)"
    LeagueEntity        ||--o{  LeagueMember_embedded  : "members"
    LeagueEntity        ||--o|  LeagueSettings_embedded: "settings"
    LeagueMember_embedded}o--|| UserEntity             : "referencia username"
    LeagueEntity        ||--o{  ClosedListEntity        : "pool"
    LeagueEntity        ||--o|  DraftEntity             : "draft (1 activo)"
    DraftEntity         ||--o{  DraftPick_embedded      : "picks"
    LeagueEntity        ||--o{  TradeEntity             : "trades"
    LeagueEntity        ||--o|  ScheduleEntity          : "schedule"
    ScheduleEntity      ||--o{  Jornada_embedded        : "jornadas"
    Jornada_embedded    ||--o{  Match_embedded          : "matches"
```

---

## 2. Diagrama de flujo de negocio

```mermaid
flowchart TD
    subgraph SETUP["🏗️ Configuración de liga"]
        A([Admin crea liga]) --> B[Invitar jugadores]
        B --> C[Jugadores nominan Pokémon al pool]
        C --> D[Tiers auto-asignados al iniciar draft\nS/A/B/C/D por quintil de BST]
    end

    subgraph DRAFT["🎯 Draft"]
        D --> E([Admin inicia Draft])
        E --> F{Turno rotativo}
        F -->|Jugador elige| G[Pick registrado]
        G --> F
        F -->|10 picks × jugador| H([Draft completado])
    end

    subgraph POST_DRAFT["⚙️ Post-draft setup"]
        H --> I[Calendario generado\nprimera + segunda vuelta\nround-robin]
        H --> J[Admin configura settings\nmonedas · precios tier · maxTeamSize\n% de pool por tier S/A/B/C/D]
        J --> J2[Guardado recalcula tiers del pool\nvia TierAssignmentService]
        J2 --> K[Admin introduce seasonStartDate]
        K --> L[Jornadas obtienen fechas\njornada N = startDate + N-1 semanas]
    end

    subgraph JORNADA["📅 Ciclo de jornada"]
        L --> M([Jornada activa])

        M --> N["🗡️ Ventana de ROBO\nhasta JUE 23:59\n✅ Implementado\nLadrón paga precio · dueño recibe ×2\nPokémon robado queda bloqueado la jornada"]
        M --> O["🔄 Ventana de SWAP con banca\nhasta VIE 16:00\n✅ Implementado\nRegla de tier · cambio neto de monedas"]
        M --> TR1["🤝 Ventana de TRADES\nhasta VIE 16:00\n✅ Implementado\nA propone · B acepta/rechaza · monedas opcionales"]

        N -->|Precio = priceTierX o custom| P[Pokémon pasa al ladrón\nDueño recibe ×2 · pick bloqueado]
        O -->|No se puede subir de tier\nNet coins si se baja| Q[Intercambio con banca]
        TR1 -->|Aceptado| TR2[Pokémon intercambiados\nmonedas transferidas]
        TR1 -->|Rechazado/cancelado| M

        P --> R
        Q --> R
        TR2 --> R

        R([Admin registra resultado]) --> S{¿Forfeit?\njugador sin Pokémon}
        S -->|Sí auto-forfeit| T[Sistema asigna ganador]
        S -->|No| T
        T --> U[Monedas distribuidas\nganador + perdedor]
        U --> V{¿Todos los partidos\nde la jornada completados?}
        V -->|No| R
        V -->|Sí| W([Jornada completada])
        W --> M
    end

    subgraph TIER_ADJ["🔧 Ajuste manual de tiers"]
        W --> X["Admin hace click en badge de tier\n  en TeamsPage"]
        X --> Y["Cascada bidireccional\n  promoción: baja el de menor BST\n  degradación: sube el de mayor BST"]
        Y --> Z["Conteos de tier equilibrados\n  TierAdjustmentResponse devuelto"]
    end

    subgraph FUTURO["🔮 Próximos pasos"]
        direction LR
        F1["🏦 Comprar de la banca\n  con monedas"]
        F2["🪟 Popup unificado\n  pokémon rival"]
    end

    style N fill:#d1fae5,stroke:#10b981,color:#064e3b
    style O fill:#d1fae5,stroke:#10b981,color:#064e3b
    style P fill:#d1fae5,stroke:#10b981,color:#064e3b
    style Q fill:#d1fae5,stroke:#10b981,color:#064e3b
    style TR1 fill:#d1fae5,stroke:#10b981,color:#064e3b
    style TR2 fill:#d1fae5,stroke:#10b981,color:#064e3b
    style X fill:#d1fae5,stroke:#10b981,color:#064e3b
    style Y fill:#d1fae5,stroke:#10b981,color:#064e3b
    style Z fill:#d1fae5,stroke:#10b981,color:#064e3b
    style J2 fill:#d1fae5,stroke:#10b981,color:#064e3b
    style TIER_ADJ fill:#f0fdf4,stroke:#10b981
    style FUTURO fill:#f9fafb,stroke:#e5e7eb
    style F1 fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95
    style F2 fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95
```

---

## 3. Estado de implementación

| Módulo | Estado |
|--------|--------|
| Auth (registro / login / JWT) | ✅ Completo |
| Gestión de ligas (crear, miembros, roles) | ✅ Completo |
| Pool de Pokémon (nominar, tiers auto) | ✅ Completo |
| Draft (turno rotativo, 10 picks/jugador) | ✅ Completo |
| Cancelar draft / expulsar miembro | ✅ Completo |
| Equipos (TeamsPage) | ✅ Completo |
| Banca + swap 1×1 | ✅ Completo |
| Calendar round-robin (primera + segunda vuelta) | ✅ Completo |
| Registro de resultados + distribución de monedas | ✅ Completo |
| Balance de monedas por jugador (privado) | ✅ Completo |
| Jornadas con fechas + ventanas de tiempo | ✅ Completo |
| Swap de banca de pago (tier parity + net coin change) | ✅ Completo |
| Sistema de robos entre jugadores | ✅ Completo |
| **Ajuste manual de tiers por admin** (cascada bidireccional) | ✅ Completo |
| **Panel propio fijo en TeamsPage** (sticky + colapsable) | ✅ Completo |
| **Porcentajes de tier configurables** (S/A/B/C/D, suma 100) | ✅ Completo |
| **Recálculo automático de tiers al guardar** (`TierAssignmentService`) | ✅ Completo |
| **Trades entre jugadores** (propuesta · aceptación/rechazo · monedas opcionales) | ✅ Completo |
| **Banner global de trades pendientes** | ✅ Completo |
| **Unificación ventanas robo/swap** (backend única fuente de verdad, `JornadaWindowService`) | ✅ Completo |
| **Historial original del draft** (`DraftEntity.draftHistory`, inmutable ante robos/swaps/trades) | ✅ Completo |
| **Activity feed / panel de movimientos** | ✅ Completo |
| **Rediseño pantalla de configuración** (dos columnas, sidebar sticky, cambios pendientes) | ✅ Completo |
| **Banners estado robo/swap** (TeamsPage muestra siempre ambos estados) | ✅ Completo |
| **Comprar Pokémon de la banca con monedas** | 🔲 Pendiente |
| **Popup unificado en Pokémon rival** (robar o proponer trade desde el mismo modal) | 🔲 Pendiente |

---

_Última actualización: 2026-05-23_
