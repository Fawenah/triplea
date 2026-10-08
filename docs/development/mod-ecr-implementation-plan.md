# MOD ECR Global 1940 implementation plan

Status: first implementation pass ready in
`custom_maps/global_1940_mod_ecr/`. See that map's `RULE_STATUS.md` for completed
behavior, remaining manual rules, and validation results.
Prepared: 2026-10-07.

The first deliverable is a separate, playable d10 map derived from Young
Grasshoppers Tournament Edition, with the agreed unit roster, prices, movement,
combat values, and straightforward support rules. More complex enforcement can
follow in small increments. Each release must state which rules still require
manual handling.

## Sources and precedence

- Primary: `local_rule_docs/AA_Global_1940_MOD_ECR_v3.0.docx.md`, particularly
  Part I Rules 1–28, Part II, and Appendix C. It includes a version of the base
  rules, so an external rulebook is not required to start.
- Secondary references: `UnitRules2026.md`, `units.js` (the 2026 section),
  `ResearchUpgrades.md`, `research.js`, `VictoryObjectives.md`, and
  `objectives.js`. Some contain earlier d6 values or additional rules; do not
  copy these indiscriminately.
- Base map: `local_maps/young_grasshoppers_tournament_edition-master/`, game XML
  `map/games/young_grasshoppers_tournament_edition.xml`, version 1.0.4.
- Project guidance reviewed: root `AGENTS.md` and `CLAUDE.md`; guidance for
  `docs`, `game-app`, `game-core`, its battle subsystem, `game-headed`, `ai`,
  and `map-data`; development setup, engine overview, and map modification and
  metadata documentation. The engine overview is historical; findings below
  are cross-checked against current source.

Confirmed precedence: user decisions, then the primary document's specific
modifications and Appendix C, then its standard rules, then secondary summaries.
Exception: `VictoryObjectives.md` and the objective page (`objectives.html`,
whose data is in `objectives.js`) are authoritative for objectives, as confirmed
by the user. All research dice stay d6, as explicitly directed by the user,
including discovery rolls. Bombing damage and convoy disruption also stay d6.
For remaining uncertainties, the user directs us to retain existing YG behavior
where available and record the interpretation for later review. Appendix A's
provisional values are defaults elsewhere. Research is the last priority and
does not require secrecy; UK economies do not share discoveries.

## What the project already supports

Most initial work belongs in map XML and assets. Unit attachments define stats;
production rules and frontiers define prices and purchase eligibility; support
attachments define combat bonuses; conditions and triggers handle many economy,
politics, and objective changes.

Engine responsibilities are primarily in `:game-core`. Map XML elements are
represented in `:map-data`; the desktop client is `:game-headed`. Standard
Pro/Fast/Weak AI code is in game-core, while FlowField AI is in `:ai`.

Confirmed source findings:

| Area | Existing support | Consequence |
| --- | --- | --- |
| Combat die size | `GameData.setDiceSides` accepts 1–200; `engine/data/gameparser/GameParser` reads `<diceSides value="..."/>` | Set the new map to 10; a global conversion of engine constants is unnecessary |
| Combat display | `BattleModel`, `DiceChooser`, and `DicePanel` use the map's die size; `DiceImageFactory` draws numbered fallback faces above 6 | Verify d10 display; custom dice images are optional |
| Bombing damage | `StrategicBombingRaidBattle` uses unit `bombingMaxDieSides` with the relevant property enabled | Existing bomber and tactical bomber values explicitly specify 6; preserve these and strategic bomber +2 |
| Convoy dice | `AbstractEndTurnDelegate.CONVOY_BLOCKADE_DICE_SIDES` is explicitly 6 | Already preserves d6 in a d10 game, but its damage formula differs from the document |
| AA dice | `UnitAttachment` defaults AA die size to the map die size when no override is set | Use hit strength 1 on d10; inspect overrides and any added cruiser AA |
| Combined arms | `UnitSupportAttachment` supports defense/attack bonuses, recipient lists, counts, and shared bonus categories | Revised basic support should be achievable in XML |
| Technology effects | `TechAbilityAttachment` supports strength, range, AA, air-battle, and roll bonuses; triggers can change other properties/frontiers | Many upgrades can be configured without changing general battle code |
| Research procedure | `TechnologyDelegate` uses map dice and its existing discovery procedure | Does not implement the ECR secret research track or the user-required d6 research rolls in a d10 game |
| Land transport | Capacity-aware code exists in `delegate/move/validation/MoveValidator`, gated by mechanized-infantry technology | Useful starting point for trucks; exact ECR behavior is not established merely by enabling it |
| Stacking | Player/unit movement and placement limits exist | Their presence does not establish support for a territory-specific total of 10 + IPC, phase-end checks, and ECR exemptions |
| AI and odds | Several standard AI calculations and FlowField paths consume the map's die size | Start with smoke checks; custom movement and targeting need later AI work |

Avoid save and network compatibility breaks: preserve serialized private fields
and packages, and existing `@RemoteActionCode` signatures. Add optional,
map-controlled behavior with legacy defaults when an engine change is necessary.

## Base map audit

Already present in the XML; preserve and verify these instead of reapplying them:

- Turn order: Germany, UK Pacific, Japan, UK Europe, Italy, China, USSR, USA,
  France.
- ANZAC is merged into UK Pacific.
- Siberia produces 2 IPC and Soviet Far East 0.
- Algeria and French West Africa are connected directly. This is an adjacency
  change, not permission to enter all of the Sahara.
- Union of South Africa has a victory city.
- Argentina is `Neutral_Axis`, worth 2 IPC, with 4 neutral infantry.
- India starts with a minor complex. An upgrade trigger exists, but runs after
  UK Pacific politics rather than at every possible declaration of war.
- Egypt has 2 UK Pacific infantry and 1 UK Europe infantry, plus the existing
  UK Europe artillery, mechanized infantry, and naval base.
- Amur, Sakha, and Buryatia each have 6 Soviet infantry; Sakha also has 2 AAA.
- Starting treasuries match the primary document: Germany 30, USSR 37, Japan
  26, USA 52, China 12, UK Europe 28, UK Pacific 27, Italy 10, France 19.
- Objective condition switches provide persistent award tracking for several
  victory tokens. The end-of-round-eight trigger currently provides a
  notification; automatic winner calculation still needs work.

Known differences:

- The original map explicitly specifies d6; the new variant specifies d10.
- No anti-tank gun, mechanized anti-tank gun, or truck unit type exists.
- Several stats, aircraft prices, AAA price, shipyard prices, and technology
  bonuses use the earlier rules. Tactical bomber pairing bonuses and infantry
  attack support are still active.
- Tactical bombers are configured as air transports, although the primary
  ECR rules give this role to strategic bombers.
- Lend-lease runs after UK Europe end turn, with a three-use limit; ECR requires
  UK Europe mobilization while the USA remains neutral.
- UK Europe Mediterranean objective includes enemy submarines. This conflicts
  with the primary document's surface-warship wording, but agrees with the
  user-authoritative objective references' broader warship wording; retain it.
- UK Pacific Outer Perimeter includes Borneo and Suriname; the explicit ECR
  list is Sumatra, Java, Celebes, and Dutch New Guinea.
- Several Soviet objectives still use the earlier income scheme; Germany's
  five-submarine bonus and newer Japanese/Italian objectives need changes.
- Axis Europe victory condition omits Union of South Africa despite its city
  already existing. Allied Africa omits French Madagascar.
- Allied Occupation lists five territories including Argentina but requires
  only four; clarify the intended set and then require all members of it.
- Axis Economy, Allied Paris liberation, London convoy-based award, and final
  scoring need completion/audit. Existing notifications are not proof of
  complete rule enforcement.
- Existing capital-purge triggers change `destroysPUs` after battle. Audit
  first versus subsequent captures and every attacker before retaining them.

## First-release unit specification

Values below come from primary Rule 22 and Appendix C unless explicitly marked
as secondary. A dash means no normal combat value; AA strength is separate from
normal defense. Keep existing XML unit identifiers where practical.

| Unit / XML ID | IPC | Move | Attack | Defense | Initial implementation |
| --- | ---: | ---: | ---: | ---: | --- |
| Infantry / `infantry` | 3 | 1 | 2 | 2 | Defensive support can raise defense to 3 |
| Artillery / `artillery` | 4 | 1 | 3 | 3 | Supports 2 infantry on defense; 1 mechanized infantry on attack |
| Mechanized infantry / `mech_infantry` | 4 | 2 | 2 | 2 | Attack 3 with artillery; blitz with tank pairing |
| Tank / `armour` | 6 | 2 | 5 | 5 | Blitz; supports 2 infantry on defense |
| Anti-tank gun / proposed `anti_tank_gun` | 4 | 1 | 2 | 3 | Normal stats first; casualty priority later |
| Mechanized anti-tank gun / proposed `mech_anti_tank_gun` | 5 | 2 | 3 | 5 | Cannot blitz; first-round targeting later |
| AAA / `aaGun` | 3 | 1 | — | — | Up to 3 precombat shots at 1; no ordinary defense roll |
| Truck / proposed `truck` | 2 | 2 | — | — | No combat move; capacity goal 3 infantry; transport enforcement may follow |
| Fighter / `fighter` | 10 | 4 | 4 | 5 | Raid escort/interceptor strength 1; general-combat shot later |
| Tactical bomber / `tactical_bomber` | 10 | 4 | 5 | 3 | Carrier compatible; base d6 damage versus air/naval bases |
| Strategic bomber / `bomber` | 10 | 6 | 7 | 1 | d6 +2 bombing; 2 infantry airlift goal |
| Submarine / `submarine` | 6 | 2 | 4 | 2 | Preserve surprise strike and destroyer interactions |
| Aircraft carrier / `carrier` | 16 | 2 | 1 | 3 | Capacity 2 aircraft, 2 hit points |
| Cruiser / `cruiser` | 12 | 2 | 5 | 6 | Bombard at 5; up to 2 defensive AA shots at 1 |
| Battleship / `battleship` | 20 | 2 | 7 | 7 | Bombard at 7; 2 hit points |
| Transport / `transport` | 7 | 2 | — | — | Existing cargo weights allow 2 infantry or infantry + 1 other land unit |
| Major complex / `factory_major` | 30 | 0 | — | — | Existing capacity baseline 10; facility AA 1 |
| Major upgrade / `factory_upgrade` | 20 | 0 | — | — | Consumes minor complex; facility AA 1 |
| Minor complex / `factory_minor` | 12 | 0 | — | — | Existing capacity baseline 3; facility AA 1 |
| Air base / `airfield` | 15 | 0 | — | — | AA 1 against bombing; +1 aircraft movement; scramble 3 |
| Naval base / `harbour` | 15 | 0 | — | — | AA 1 against bombing; +1 naval movement; repairs |
| Naval mine / proposed `naval_mine` | 2 | 0 | — | — | Movement-event hazard at 2/10, not a defending combat unit |
| Reconnaissance plane / proposed `recon_plane` | 5 | 6 | — | — | Confirmed inclusion; ordinary aircraft movement only, no scouting or Fog of War |
| Transport aircraft / proposed `transport_plane` | Conversion only | 6 | — | — | Free strategic/heavy bomber reclassification; capacity goal 2 infantry |
| Cargo aircraft / proposed `cargo_plane` | Conversion only | 6 | — | — | Free heavy bomber reclassification; capacity goal 1 ground unit |

Keep the base map's cargo weights initially: infantry 2, other transportable
land units 3, naval transport capacity 5. Add cargo eligibility for new land
units deliberately; a truck carried by sea must not carry its own cargo.

Represent technology upgrades as player-specific effects on base units where
possible, rather than making upgraded units freely purchasable. The desired
values must be accounted for from the beginning even if research acquisition
is temporarily manual.

| Upgrade | Required effect |
| --- | --- |
| Self Propelled Artillery | Movement 2; attacking support for 2 infantry and/or mech infantry at 3; retains defensive support |
| Heavy Tanks | Defense 7; attack stays 5 |
| Heavy Bombers | Two attack dice at 7; two d6 bombing dice, each +2; defense remains 1 |
| Super Battleships | Two attack dice at 7 and two bombardment dice at 7; defense unchanged |
| Super Carriers | Capacity 3 aircraft |
| Improved Transports | Any 2 ground units plus 1 infantry; weighted capacity alone must be checked against this exact constraint |
| Super Submarines | Attack and surprise strike 6; defense remains 2 |
| Jet Fighters | Attack 6; raid air attack/defense and general interception 3; ordinary defense stays 5 |
| Radar | AAA, cruiser AA, and facility AA strength 3; unlimited air-base scramble |
| Long Range Aircraft | +2 movement: fighters/tactical bombers 6, strategic bombers 8, plus operative base bonus |
| Improved Shipyards | Sub 5, transport 5, destroyer 7, cruiser 8, carrier 13, battleship 16 |

The base map has long-range bonuses of only +1; super-sub/jet/radar bonuses are
also +1 and need to become +2. Its shipyard transport/cruiser/battleship prices
are 6/9/17 instead of 5/8/16. Its `LHTR Heavy Bombers` setting is true: verify
and configure actual two-dice/two-hit and summed-damage behavior, rather than
assuming that setting implements Appendix C.

The user confirmed inclusion of the secondary-only aircraft. Reconnaissance
planes have no special abilities, and Fog of War is explicitly out of scope.
Their range is 6, or 8 with long-range technology. Transport/cargo aircraft
move 6 and have no combat value; attempt existing air-transport support for
2 infantry/1 ground unit if straightforward, otherwise document the limitation.
The user chose free reclassification only, with enforcement deferred: these
aircraft must not be freely purchasable. Initially document a manual edit-mode
replacement procedure that removes the qualifying bomber and adds its transport
or cargo form. Conversion cost is zero; the originating bomber's purchase cost
is 10 IPC. Clarify reversibility, long-range bonuses, and eligible cargo before
automating reclassification.

## Staged work

### Stage 0 — Agree the contract and preserve the baseline

1. Resolve the first-release roster and source precedence questions.
2. Create a separate map named provisionally `global_1940_mod_ecr`, with game
   name `Global 1940 MOD ECR v3.0`, derived from the supplied map. Retain its
   credits and original assets; preserve the supplied source map as a baseline.
3. Keep the new map in a tracked location, provisionally `custom_maps/`, with
   its own `map.yml`, XML, notes, and assets. The user's existing `.gitignore`
   excludes `local_maps/*` and `local_rule_docs/*`; writing the new map there
   would otherwise hide the deliverable from normal Git review.
4. Establish JDK 25 and a working Gradle wrapper. The user's installation is
   JDK 17. A portable Temurin JDK 25 was downloaded into ignored `build/tools/`
   and successfully used to compile the engine and run tests. No system Java
   installation was changed.
5. Keep a rule status list: automated, approximate/manual, deferred, or awaiting
   a decision. Tie entries to rule numbers and specific map/source locations.

### Stage 1 — Playable d10 unit foundation (recommended first implementation)

1. Set `<diceSides value="10"/>` and implement the agreed roster and base table.
   Update attachments, production rules/frontiers, repair references, and any
   explicit unit lists affected by the new types. China unlocks artillery when
   Burma Road is open, as confirmed by the user. Add transport/cargo forms as conversion-only
   units with a manual replacement procedure initially.
2. Add distinct, recognizable unit images for the new units and relevant
   nations/neutral owners. Prefer existing usable art or clearly marked
   placeholders at this stage. Confirm that missing images do not break loading.
3. Remove base infantry attack support and tactical bomber +tank/+fighter
   attack bonuses. Add artillery and tank defensive support of +1 for 2 infantry
   each using the same nonstacking bonus category; retain artillery attack
   support for mechanized infantry only.
4. Add defensive cruiser AA using existing combat-AA attachments if its
   casualty behavior matches Rule 24. Check it fires once before combat, only
   for the defender, and coexists correctly with other AA.
5. Preserve d6 bombing damage and d6 convoy dice. Set Japanese kamikaze hit
   strength to 3. Verify air-battle and facility-AA hit strength remains 1/10.
6. Define the eleven upgrade effects and remove unrelated legacy breakthroughs
   from the new map's offered research. Implement straightforward stat/range/
   price effects in XML. Keep research acquisition manual until the last stage if
   necessary, with a documented means to grant a player's technology for testing.
7. Preserve verified territory, neutral, treasury, and turn-order changes.
   Audit total initial territorial incomes separately from starting cash.
8. Update map name references, objective-tab keys, notifications, and companion
   notes. List limitations concretely: trucks may lack cargo enforcement,
   anti-tank guns use ordinary casualty assignment, tactical bombers use normal
   attack, and bombers retain engine behavior until later work.

Acceptance: the new map loads in TripleA; units can be bought/placed by eligible
powers and display correctly; combat uses d10 with the table values; bombing
uses d6 +2/d6; basic supports do not stack; the original map remains playable.
No engine change is presumed necessary before these checks expose a gap.

### Stage 2 — Economy, setup, diplomacy, and objective corrections

- Correct national objective lists/amounts/timing: German five-submarine bonus;
  revised Soviet income and free artillery; Japanese China, island and Burma
  rewards; Italian island/East Africa bonuses; revised USA conditions; UK
  warship and Outer Perimeter conditions; China Burma Road conditions.
  Follow `VictoryObjectives.md`/the objective page where primary prose differs:
  retain the separately listed Calcutta rewards; use the explicit 18-territory
  China set and island list. Retain confirmed Burma Road artillery eligibility.
  Remove any legacy objective absent from
  those authoritative references, including the USA land-unit-in-France bonus.
  If the objective references disagree with one another, ask before choosing.
- Fix UK Europe lend-lease eligibility and run it during mobilization without
  an arbitrary three-turn cap; test early USA entry into war.
- Make India's free major-complex upgrade happen when war with Japan begins,
  including declarations during Japan's turn, and preserve existing damage as
  appropriate. Check ownership and recapture cases.
- Audit Dutch ownership and capital-liberation behavior, UK declaration-of-war
  conditions, shared UK political status, and first-capture-only treasury
  transfers. Retain YG diplomacy and setup where primary prose conflicts.
- Complete persistent victory objectives: add South Africa to the Europe city
  list, Madagascar to Allied Africa, correct Occupation, add Paris/Economy, and
  check awards after captures, blitzes, neutral activation, income events, and
  other actions that can satisfy an objective.
- Implement or explicitly track the London 12-IPC convoy award and final
  round-eight score comparison with Allied tie victory. Use XML triggers and
  resource/switch state where suitable; a small engine hook may be needed for
  convoy-loss event data. Avoid recalculating permanent tokens from current
  ownership alone.
- Add confirmed optional ECR convoy damage behavior: one d6 per eligible
  ship and count all face values, retaining default behavior for existing maps.
  This needs engine work: current code sums only rolls <=3, and the base map
  rolls twice for submarines and some aircraft, zero for carriers/transports.
  Separate phase presentation and income/objective ordering need review too.
- Handle ELO and faction bids manually first, as separate nonpoolable budgets
  with unspent funds discarded. Do not assume the existing single bid pool
  enforces this rule.

Acceptance: representative objective conditions produce correct IPCs/units,
one-time awards persist through loss and save/reload, and political transitions
and endgame scoring follow the agreed interpretation.

### Stage 3 — Movement and deployment rules

Work in separate increments, reusing existing validation where it fits:

1. Trucks: capacity 3 infantry in noncombat only, no prior or subsequent cargo
   movement, start/end together, no hostile route, and no nested sea cargo.
2. Artillery paired with mechanized infantry: one-to-one extra noncombat move,
   same start/end, no extra combat range. Do not enable a technology globally
   if it also gives unintended tank transport abilities.
3. Railroad infantry: per-origin allowance equal to IPC value with a major
   complex, noncombat only, no combination with truck transport. Track use
   across separate moves and undo.
4. Stacking: shared territory total of 10 + IPC; exempt AAA, trucks, facilities,
   bases, and attacking aircraft. Enforce the agreed checkpoints, landing and
   retreat limits; postcombat removals remain explicit Edit-mode actions. Retain
   YG setup, including Germany's initial 17 counted units against its limit of 15.
5. Strategic bomber paratroopers: up to 2 infantry without research, required
   supporting ground units, cargo loss to AA, no bomber combat while delivering,
   noncombat eligibility and onward flight. Remove the base tactical airlift.

These rules touch movement state, placement, UI explanations, undo, save/reload,
and AI movement. They should not be approximated merely by globally raising
movement stats.

### Stage 4 — Advanced combat and naval rules

Separate changes in increasing interaction complexity:

1. Strategic bombers fight and can be casualties only in round one, then leave
   the battle; bomber-only attacks on undefended transports roll at 7 rather
   than auto-destroying them.
2. Anti-tank casualty priority (tank/mech infantry/mech AT), normal overflow if
   no priority units remain; mechanized AT priority only in round one.
3. Fighter interception shots in ordinary battles for both sides before round
   one, while retaining normal subsequent fire; establish ordering versus AA
   and simultaneous casualty removal.
4. Tactical bomber choice per battle: normal attack 5 or selected unit type at
   fixed 4; discard excess hits, restrict transport targets, preserve the
   confirmed requirement for a destroyer when targeting submarines.
5. End-of-turn 1-IPC capital-ship bounty, with attribution for mixed defenders,
   mines/kamikaze, and two-hit ships.
6. Mines bought/placed at destroyers, caps per faction/zone and per nation,
   entry and transit rolls in both move phases, immediate casualties, cargo
   consequences, undo boundaries, and removal on a successful hit.
7. Naval screening/blockades with conditional downstream battles. Reuse the
   existing battle dependency graph where possible. This affects route
   validation, movement commitment, battle ordering, retreats, UI, and AI.

Reconnaissance remains an ordinary aircraft with no special ability. Fog of War
and scouting are out of scope by user instruction.

### Stage 5 — Research progression and upgrade completion (last priority)

Implement one selected technology in development per eligible power, free major
power entry at setup, paid entry/advancement, separate d6 P-3 price/success,
d6 H discovery dice succeeding on 5–6, abandonment, and immediate activation.
Technology may be public; UK economies do not share discoveries. Use Stage 1's
effects for the eleven technologies and finish any missing capacity, scramble,
support, or bombardment effects. Preserve manual acquisition until this stage;
do not substitute the ordinary engine's d10 research probabilities.

## Decisions and rule conflicts

Confirmed user decisions:

- Include reconnaissance, transport, and cargo aircraft initially, even if
  transport abilities need to follow later. Reconnaissance has no special rules;
  do not implement Fog of War.
- Transport/cargo aircraft are free reclassifications only, with enforcement
  deferred; do not offer direct purchase.
- Use the primary v3.0 document for rules, with the user's explicit correction
  that all research rolls use d6. Keep bombing and convoy damage d6 too.
- Use `VictoryObjectives.md` and the objective page as authoritative for
  objectives. Occupation requires Poland, Finland, Bulgaria, and Iraq only.
- Retain mechanized infantry/tank blitz pairing; count every d6 convoy result
  within territory caps; cap factory output at territory IPC; retain YG
  unplaced-purchase behavior.
- Exclude factories/bases from stacking; remove mines on successful hits;
  capital purge is globally first-capture-only; pay capital-ship bounties at
  end turn; China unlocks artillery with Burma Road; targeted attacks on
  submarines require a destroyer.
- Research is last, may be public, and discoveries are independent for the UK
  economies. Retain YG implementations for other uncertain rules and highlight
  those choices after the first pass.

Remaining review items and resolved conflicts are recorded below:

| Question | Evidence / proposed treatment |
| --- | --- |
| Research discovery dice — resolved | User explicitly requires d6 research. Use Rule 20's d6 discovery on 5–6; P-3 is d6, success on 6. This overrides the document's conflicting d10 discovery text |
| Jet interception — resolved | Primary Appendix C takes precedence over unit summaries: 3 |
| AAA/facility “defense” | Secondary unit chart puts AA strength in the defense column. Use no ordinary combat defense; separate AA strength 1 or radar 3 |
| Mechanized infantry blitz — resolved | Retain tank pairing |
| Convoy formula — resolved | Count all d6 results within existing territory caps; surface warships include carriers, exclude transports and aircraft |
| UK political status — first-pass fallback | Retain YG relationships and movement permissions, including initial UK Pacific war with Germany/Italy |
| Japan politics — first-pass fallback | Retain YG: Japan may fight China before war with Western Allies |
| USSR politics — first-pass fallback | Retain YG's separate Japanese and European war permissions |
| Lend-lease timing | Rule 5 first refers to US mobilization but explicitly specifies UK Europe mobilization. Propose UK Europe mobilization |
| Starting UK Pacific setup — first-pass fallback | Retain YG starting forces |
| Factory capacity/placement — resolved/fallback | IPC production cap is intentional; retain YG placement rules where not explicitly changed |
| Unplaced purchases — resolved | Retain YG behavior |
| Stacking facilities — resolved | Factories and bases do not count |
| Mine persistence — resolved | Remove mines on a successful hit; retain existing behavior elsewhere where available |
| Interception ordering | User requires both sides to fire AA simultaneously; retain separate raid interception |
| Capital purge — resolved | Global first capture of each defeated capital |
| Capital-ship bounty — resolved | Pay at end turn to the participating player first in turn order |
| Objective source conflicts — resolved | Use the explicit territory sets, rewards, and requirements from `VictoryObjectives.md` and the objective page. This resolves primary/summary differences in Japanese China/islands, Soviet reinforcement control, and other objectives; report any disagreement between the two authoritative references |
| China purchases/Burma Road — resolved | Unlock artillery |
| London token — first-pass treatment | Use the engine's actual capped per-territory loss allocation, count original British territory losses only, and combine enemy Axis contributions in that turn |
| Targeted attack versus submarines — resolved | Requires a destroyer |
| Secret research / UK sharing — resolved | Public research is acceptable; economies do not share discoveries |

## Validation strategy

Movement and placement follow-up (2026-10-08): railroad infantry bonuses and
per-origin quotas, the improved transport three-unit ceiling, and land stacking
checks are implemented. Germany alone starts above its limit: 17 counted units
versus 15. Preserve those starting forces and move two counted units out during
the first Combat Move. Excess units block movement phase completion; removals,
when necessary, remain explicit Edit-mode actions. Factories/bases, AAA including
radar AAA, trucks and attacking aircraft are exempt; allied units count together
and attackers/defenders have separate limits. Sea zones are unlimited.

Further user rulings: award capital-ship bounties at end turn to the participating
player first in turn order. Free aircraft conversion happens before/during
movement and reverses at the end of Noncombat Move. Mines attack once per entry/
movement phase rather than per ship; existing mines persist after destroyer
losses, while new placement is blocked when over the destroyer allowance. These
aircraft/naval changes are recorded for subsequent implementation batches.

Initial planning used local rules/guidance, relevant source, and XML inspection.
Implementation validation now includes the real TripleA parser, initialization,
delegate movement and undo, battle dice/support, serialization, and objective
triggers. The focused tests are in `ModEcrMapTest` and the movement/convoy tests.

During implementation:

- Add a reproducible roster/map audit comparing actual XML values, production
  frontiers, support recipients, territory names, and setup against the agreed
  specification. Make it usable without JDK for quick map review.
- Load the map through TripleA's real parser; use its test-fixture loading
  conventions (`TestMapGameData` / `TestMapGameDataLoader`) where appropriate.
  Keep engine regression fixtures small instead of duplicating 50 MB of art.
- Use fixed random results to check d10 boundaries (infantry rolls 2 hit/3 miss,
  battleship 7 hit/8 miss), AA 1 hit/2 miss, kamikaze 3 hit/4 miss, support
  limits, bombing d6 +2, and unchanged convoy die size.
- Check each technology on one player while another remains unmodified; verify
  multiple hits/summed bombing damage, capacities, shipyard prices, and range.
- Exercise purchases, placement, combat display, bombardment, raid interception,
  casualty selection, air landing, save/reload, and a short AI-driven session.
- For later engine changes add focused JUnit 5/AssertJ regression tests,
  including six-sided existing-map behavior. Test movement undo, mid-battle
  save/reload, and network serialization for stateful features.
- Run affected module tests and formatting/static checks; use `./verify` for
  the completed engine change set when the build environment is ready.

## First-pass delivery

Delivered Stage 0/1, most of Stage 2, and the truck, artillery/mech pairing and
transport/cargo aircraft, railroad, improved transport capacity and stacking
portions of Stage 3. Overflow removals remain explicit Edit-mode actions.
Unit upgrades have correct manual
variants while research acquisition remains deferred. Full rule enforcement is
not claimed; the map's `RULE_STATUS.md` lists every manual limitation and the
YG assumptions retained under the user's direction.

The engine uses opt-in map properties for ECR convoy damage, territory-capped
factory production and land transport without technology. MOD ECR-specific
cargo and aggregate-objective hooks apply only to the new variant. No existing
serialized fields/packages or remote API signatures were changed.

Validation: `:game-core:check`, `:game-headed:check` and
`:game-headed:installDist` passed with JDK 25.
All 2,571 game-core tests passed, including 59 MOD ECR integration cases;
78 desktop tests passed. Formatting and
PMD passed. The independent Python map audit passed. The supplied YG baseline
was preserved. The user played a German turn on the first-pass version;
interactive playtesting of the movement/placement follow-up remains useful.

German AI startup follow-up: move validation uses its explicit combat/noncombat
context rather than the current sequence phase. AI previews during Politics and
Purchase no longer throw movement-phase exceptions. Regression tests cover both
preview modes, attacking-aircraft exemptions and real German AI territory-option
enumeration during Politics. Transport/air phase probes also tolerate previews
outside movement phases. No serialized fields or remote signatures changed.

Launch the built Windows client with `scripts/mod_ecr/run.ps1` and select the
new map after configuring `custom_maps` as the maps folder. A copyable map ZIP
is generated under `build/distributions/`.

Next priorities are the combat/aircraft rules still handled manually, followed
by capital-ship bounties, mines/naval screening and finally research. Use
the first playtest to review the documented YG setup/diplomacy/placement choices
and provisional unit art before adding those more invasive features.
