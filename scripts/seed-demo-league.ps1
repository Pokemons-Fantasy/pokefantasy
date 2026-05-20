# seed-demo-league.ps1
# ─────────────────────────────────────────────────────────────────────────────
# Crea una liga demo completa con 8 jugadores, 128 Pokémon en el pool,
# draft completado (80 picks), banca de 48, y calendario auto-generado.
#
# Prerequisitos:
#   1. docker-compose up -d   (MongoDB + Redis)
#   2. Backend corriendo en localhost:8080 (con JWT_SECRET configurado)
#
# Uso:
#   .\scripts\seed-demo-league.ps1
#   .\scripts\seed-demo-league.ps1 -Base "http://localhost:8080/v1"
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$Base = "http://localhost:8080/v1"
)

$ErrorActionPreference = "Stop"

# ─── HELPERS ──────────────────────────────────────────────────────────────────

function Invoke-Api {
    param($Method, $Url, $Body, $Token)
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $json = if ($Body) { $Body | ConvertTo-Json -Depth 5 } else { $null }
    try {
        Invoke-RestMethod -Method $Method -Uri "$Base$Url" -Headers $headers -Body $json
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        # 409 Conflict = ya existe → idempotente, continuar
        if ($status -eq 409) { return $null }
        Write-Host "  ERROR $status en $Method $Url" -ForegroundColor Red
        Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function Log {
    param($Msg, $Color = "White")
    Write-Host $Msg -ForegroundColor $Color
}

# ─── DATOS ────────────────────────────────────────────────────────────────────

$password  = "Pikachu123"
$leagueName = "Liga Kanto Demo"
$turnOrder = @("ash", "brock", "misty", "gary", "james", "jessie", "dawn", "may")

# 16 nominaciones por jugador = 128 en el pool
# Todos los nombres son válidos en PokeAPI (lowercase, sin espacios)
$nominations = @{
    ash    = @("pikachu","charizard","gengar","snorlax","dragonite","alakazam","lapras","arcanine","mewtwo","eevee","raichu","bulbasaur","squirtle","venusaur","wartortle","ivysaur")
    brock  = @("onix","golem","rhydon","graveler","omastar","kabutops","aerodactyl","rhyhorn","geodude","omanyte","tyranitar","larvitar","sudowoodo","relicanth","shuckle","pupitar")
    misty  = @("starmie","gyarados","psyduck","vaporeon","golduck","dewgong","cloyster","staryu","slowbro","tentacruel","corsola","seaking","seadra","horsea","goldeen","mantine")
    gary   = @("blastoise","nidoking","exeggutor","jolteon","flareon","porygon","scyther","kangaskhan","tauros","dodrio","nidoqueen","kingler","vileplume","machamp","magnemite","magneton")
    james  = @("weezing","victreebel","lickitung","magmar","electabuzz","tangela","clefairy","jigglypuff","persian","raticate","grimer","koffing","ekans","bellsprout","weepinbell","muk")
    jessie = @("arbok","jynx","wobbuffet","sneasel","houndoom","haunter","misdreavus","ninetales","hypno","meowth","primeape","fearow","mankey","growlithe","spearow","rapidash")
    dawn   = @("piplup","glaceon","leafeon","espeon","umbreon","ambipom","pachirisu","buizel","lopunny","roserade","mamoswine","froslass","garchomp","riolu","lucario","togekiss")
    may    = @("blaziken","beautifly","skitty","munchlax","swampert","sceptile","absol","altaria","flygon","milotic","torchic","mudkip","treecko","marshtomp","grovyle","combusken")
}

# 80 picks: $picks[ronda 0-9][jugador 0-7]  →  ash,brock,misty,gary,james,jessie,dawn,may
$picks = @(
    #          ash          brock         misty         gary          james         jessie        dawn          may
    @("pikachu",    "onix",       "starmie",    "blastoise",  "weezing",    "arbok",      "piplup",     "blaziken"  ),
    @("charizard",  "golem",      "gyarados",   "nidoking",   "victreebel", "jynx",       "glaceon",    "beautifly" ),
    @("gengar",     "rhydon",     "psyduck",    "exeggutor",  "lickitung",  "wobbuffet",  "leafeon",    "skitty"    ),
    @("snorlax",    "graveler",   "vaporeon",   "jolteon",    "magmar",     "sneasel",    "espeon",     "munchlax"  ),
    @("dragonite",  "omastar",    "golduck",    "flareon",    "electabuzz", "houndoom",   "umbreon",    "swampert"  ),
    @("alakazam",   "kabutops",   "dewgong",    "porygon",    "tangela",    "haunter",    "ambipom",    "sceptile"  ),
    @("lapras",     "aerodactyl", "cloyster",   "scyther",    "clefairy",   "muk",        "pachirisu",  "absol"     ),
    @("arcanine",   "rhyhorn",    "staryu",     "kangaskhan", "jigglypuff", "misdreavus", "buizel",     "altaria"   ),
    @("mewtwo",     "geodude",    "slowbro",    "tauros",     "persian",    "ninetales",  "lopunny",    "flygon"    ),
    @("eevee",      "omanyte",    "tentacruel", "dodrio",     "raticate",   "hypno",      "roserade",   "milotic"   )
)

# ─── BIENVENIDA ───────────────────────────────────────────────────────────────

Log ""
Log "╔══════════════════════════════════════════╗" Magenta
Log "║     POKEFANTASY — SEED DEMO LEAGUE       ║" Magenta
Log "╚══════════════════════════════════════════╝" Magenta
Log "  Base URL : $Base" DarkGray
Log "  Liga     : $leagueName" DarkGray
Log "  Pool     : 128 Pokémon (16 × 8 jugadores)" DarkGray
Log "  Draft    : 80 picks (10 × 8 jugadores)" DarkGray
Log "  Banca    : 48 Pokémon no elegidos" DarkGray
Log ""

# ─── PASO 1: Registrar 8 usuarios ─────────────────────────────────────────────

Log "[1/7] Registrando jugadores..." Cyan
foreach ($u in $turnOrder) {
    Invoke-Api POST "/user" @{ username = $u; password = $password } | Out-Null
    Log "  ✓ $u" Green
}

# ─── PASO 2: Login y obtener tokens ───────────────────────────────────────────

Log ""
Log "[2/7] Login..." Cyan
$tokens = @{}
foreach ($u in $turnOrder) {
    $tokens[$u] = Invoke-Api POST "/user/login" @{ username = $u; password = $password }
    Log "  ✓ $u" Green
}

# ─── PASO 3: Ash crea la liga ─────────────────────────────────────────────────

Log ""
Log "[3/7] Ash crea '$leagueName'..." Cyan
$leagueId = Invoke-Api POST "/leagues" @{ name = $leagueName } $tokens["ash"]
Log "  ✓ League ID: $leagueId" Green

# ─── PASO 4: Añadir los otros 7 miembros ──────────────────────────────────────

Log ""
Log "[4/7] Añadiendo 7 miembros..." Cyan
foreach ($u in ($turnOrder | Select-Object -Skip 1)) {
    Invoke-Api POST "/leagues/$leagueId/members" @{ username = $u } $tokens["ash"] | Out-Null
    Log "  ✓ $u añadido" Green
}

# ─── PASO 5: Nominaciones (16 × 8 = 128) ─────────────────────────────────────

Log ""
Log "[5/7] Nominando 128 Pokémon (16 por jugador)..." Cyan
foreach ($u in $turnOrder) {
    $count = 0
    foreach ($poke in $nominations[$u]) {
        Invoke-Api POST "/leagues/$leagueId/closed-list/nominate" @{ pokemonName = $poke } $tokens[$u] | Out-Null
        $count++
    }
    Log ("  ✓ {0,-8} : {1} nominaciones" -f $u, $count) Green
}

# ─── PASO 6: Ash inicia el draft ──────────────────────────────────────────────

Log ""
Log "[6/7] Ash inicia el draft (tiers BST asignados automáticamente)..." Cyan
Invoke-Api POST "/leagues/$leagueId/draft/start" @{ turnOrder = $turnOrder } $tokens["ash"] | Out-Null
Log "  ✓ Draft iniciado — S/A/B/C/D asignados por quintiles BST" Green

# ─── PASO 7: 80 picks (10 rondas × 8 jugadores) ──────────────────────────────

Log ""
Log "[7/7] Ejecutando 80 picks..." Cyan

$totalPick = 0
for ($round = 0; $round -lt 10; $round++) {
    for ($i = 0; $i -lt 8; $i++) {
        $player  = $turnOrder[$i]
        $pokemon = $picks[$round][$i]
        $totalPick++
        Invoke-Api POST "/leagues/$leagueId/draft/pick" @{ pokemonName = $pokemon } $tokens[$player] | Out-Null
        Log ("    [{0,2}/80] {1,-10} → {2}" -f $totalPick, $player, $pokemon) Green
    }
    Log ("  — Ronda {0}/10 completada" -f ($round + 1)) Yellow
}

# El 80º pick completa el draft → auto-genera tiers + calendario (14 jornadas)

# ─── RESUMEN ──────────────────────────────────────────────────────────────────

Log ""
Log "╔══════════════════════════════════════════════════════════╗" Magenta
Log "║             ✅  LIGA DEMO CREADA CON ÉXITO               ║" Magenta
Log "╚══════════════════════════════════════════════════════════╝" Magenta
Log ""
Log ("  League ID   : {0}" -f $leagueId) White
Log "  Pool        : 128 Pokémon en la closed list" White
Log "  Tiers       : S/A/B/C/D asignados por quintiles BST" White
Log "  Equipos     : 8 jugadores × 10 Pokémon elegidos" White
Log "  Banca       : 48 Pokémon no elegidos (disponibles para swap)" White
Log "  Calendario  : 14 jornadas (7 primera vuelta + 7 segunda vuelta)" White
Log ""
Log "  Credenciales (contraseña: Pikachu123)" White
foreach ($u in $turnOrder) {
    $role = if ($u -eq "ash") { " ← ADMIN" } else { "" }
    Log ("    {0,-8}{1}" -f $u, $role) Cyan
}
Log ""
Log "  URLs frontend (http://localhost:5173):" White
Log "    Liga      → http://localhost:5173/leagues/$leagueId" Cyan
Log "    Equipos   → http://localhost:5173/leagues/$leagueId/teams" Cyan
Log "    Banca     → http://localhost:5173/leagues/$leagueId/teams" Cyan
Log "    Calendario → http://localhost:5173/leagues/$leagueId/schedule" Cyan
Log ""
Log "  API directa (http://localhost:8080):" White
Log "    GET /v1/leagues/$leagueId/draft    → status: COMPLETED, 80 picks" DarkGray
Log "    GET /v1/leagues/$leagueId/schedule → 14 jornadas" DarkGray
Log "    GET /v1/leagues/$leagueId/bench    → 48 Pokémon" DarkGray
Log ""
