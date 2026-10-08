# MOD ECR first-pass status

Updated 2026-10-08. This is a separate variant; the supplied YG map and local
rule documents are untouched. Research is the last priority.

## Automated

| Rule / feature | Behavior |
| --- | --- |
| d10 combat | Ten sides, exact printed attack/defense values, AA 1, kamikaze 3 |
| d6 exceptions | Strategic bombing d6 +2, tactical bombing d6, convoy d6; research phases disabled until their d6 procedure is implemented |
| Complete roster | 40 base, upgraded, radar, transport/cargo, and mine types; normal costs and movement in `unit-spec.json` |
| Purchase restrictions | New ordinary units are purchasable by normal industrial powers; China retains its restricted frontier; conversion/upgrade types and mines are not freely purchasable |
| Basic combined arms | Artillery/tanks boost up to two defending infantry each, nonstacking; artillery supports one attacking mech infantry; old infantry/tactical attack pairing bonuses removed |
| Mechanized blitz | Tank pairing retained, including the manual heavy-tank variant |
| Cruiser AA | Two defensive precombat shots maximum, strength 1 on d10; radar cruiser strength 3 |
| Truck transport | Three fresh infantry together with a truck can move up to two land spaces in noncombat; movement is consumed and undo restores it |
| Artillery/mech pairing | One fresh artillery accompanies one mechanized infantry up to two spaces in noncombat, with a common origin and destination; trucks cannot transport artillery |
| Railroads | Fresh infantry starting Noncombat Move at an allied major factory receive two-space movement; each origin's allowance is its IPC value; split moves and undo retain the origin quota; truck and air/sea passengers do not spend railroad allowances |
| Improved transports | At most three ground units, at most two non-infantry, within the existing weighted capacity; already loaded/unloaded cargo counts |
| Land stacking | 10 + territory IPC per side, allied units combined; exempts AAA including radar AAA, trucks, factories/bases, and attacking aircraft; sea zones unlimited; checks movement, placement, land retreats, and aircraft landing capacity; excess stacks block phase completion |
| Anti-tank fire | Separate specialist rolls on both sides; hits must take tanks/heavy tanks, mech infantry or mech AT first, with normal overflow; mechanized AT priority lasts only through round one |
| Attacking strategic bombers | Survivors withdraw after round-one casualties if defenders remain; they cannot fire or be selected as casualties in later rounds; surviving aircraft still require normal landing |
| Defending strategic bombers | Survivors withdraw from ongoing combat after round one and cannot fire or absorb later combat hits; destroyed when enemy ground units capture their territory; survive if the territory is held or an air-only attack wins without capture |
| Bomber-only transport attacks | Undefended transports are not auto-killed; roll one d10 per bomber (including heavy), scoring a hit on 7 or less; missed transports survive the one-round attack |
| Aircraft reversion | Owned transport/cargo planes automatically return to bomber/heavy-bomber types at the end of Noncombat Move; ownership, movement, damage and dependent links are preserved |
| Custom artwork | Supplied NavalMine.png and reconnaissancePlane.png replace placeholders for every owner; identical artwork is shared, with ownership retained in engine unit data |
| Transport aircraft | Noncombat airlift of two infantry, no research needed; no direct purchase; free bomber replacement still performed manually |
| Cargo aircraft | Noncombat airlift of one transportable ground unit; infantry-only transports reject other cargo; free heavy-bomber replacement still manual |
| Factory production | Optional engine rule caps output at territory IPC and reduces that cap for bombing damage |
| Convoy formula | One d6 per submarine/surface warship, including carriers; count every face, exclude transports/aircraft; existing shared-territory caps retained |
| National objectives | Revised German, Soviet, Japanese, US, Chinese, Italian, and UK rewards/territory lists; Soviet artillery reinforcements and one-time Berlin/China bonuses |
| German submarine objective | Counts German submarines across zones, excluding 113/114/115 |
| China Burma Road | India/Burma/Yunnan allied control yields +6 IPC and unlocks artillery |
| Lend-lease | UK Europe mobilization, while USA is neutral, no fixed three-use cap |
| India complex upgrade | Checks after each power's politics phase, including Japan's declaration phase |
| Victory objectives | Persistent earned switches, full Europe city list including South Africa, Africa including Madagascar, Paris liberation rather than initial French ownership, Occupation excludes Argentina |
| Economy token | Axis territory production totals at least 144, excluding neutral powers |
| London convoy token | Actual capped losses of at least 12 from originally British territories in one turn; acquired territories do not count |
| Final score | End of round 8, count persistent tokens; Allies win ties; Attrition is the tie-break, not an extra collectible token |
| Victory display | Permanent tokens are shown in the Objectives tab |
| Fighter interception | Ordinary battles: after normal AAA, both sides roll simultaneously before casualties are removed; fighter 1, jet 3 on d10; survivors still fire normally; raid interception retained |
| Tactical targeted attack | Attacker selects bombers and a unit type per bomber before any battle dice; fixed 4 through the battle, excess hits lost; escorted transports excluded, submarines require a destroyer; AI uses normal attack 5 |

## Preserved YG behavior and assumptions to review

- Initial forces and treasuries, merged UK Pacific, neutral standing armies,
  turn order, Sahara passage, Siberian values, and South African city.
- UK Pacific begins at war with Germany/Italy, matching YG. The primary
  document's contradictory description of it as completely neutral is not used.
- Japan may fight China before war with Western Allies. USSR/Japan war
  permissions remain separate from USSR/European Axis permissions.
- Dutch relationships and territory/capital restoration follow YG.
- First-capture capital-purge triggers are retained. The intended interpretation
  is global first capture of each capital; unusual capture/recapture sequences
  need further playtesting.
- Factory placement and hostile-sea placement follow YG. The confirmed IPC
  output cap is implemented separately.
- Unplaced purchases follow YG retention behavior.
- India upgrades at a politics-phase checkpoint rather than inside the war
  declaration operation. This occurs before combat; an exact declaration-time
  hook can follow if needed.
- London losses use the engine's existing allocation when several convoy zones
  share territories. Different mathematical allocations may yield different
  original-territory totals; this preserves established YG handling.
- Japanese Calcutta earns both separately listed rewards, as in the authoritative
  objective reference. UK Mediterranean includes submarines (the reference says
  warships, while Italy's reference explicitly says surface warships).
- Unit art is provisional and reused; some upgraded/new units share icons.
  The printed artwork still includes YG annotations, so use the ECR roster and
  game notes for current combat values and objectives.

The fighter interception and tactical attack rules above are automated. Tactical
choices survive save/reload; mid-battle saves from older builds continue with
normal tactical attacks. AI targeting decisions remain a later improvement.

## Manual or deferred

| Rule | Current limitation / agreed target |
| --- | --- |
| Reclassification | Use Edit mode before/during movement to replace the qualifying bomber for free; the reverse conversion at Noncombat Move end is automated; a direct conversion interface is deferred |
| Research | No automatic progression or discoveries; public information is allowed, all research dice d6, UK economies do not share discoveries |
| Upgrade acquisition | Use manual replacement variants until research is implemented; no automatic conversion of existing forces or upgrade purchase frontier |
| Paratrooper details | Base airlift support works, but required friendly ground-unit ratios, full cargo casualty handling, and onward-flight restrictions need further work |
| Stacking overflow | Resolve excess by moving units out; if removal is necessary, select and remove them in Edit mode. No automatic deletion. Germany alone starts over the limit: 17 counted units versus 15; its starting forces are preserved |
| Capital-ship bounty | Manually pay 1 IPC at end turn to the participating player first in turn order |
| Mines | Type and cost exist; placement and attacks are manual; one entry roll per movement phase, not per ship; remove on a successful hit; losing destroyers leaves existing mines intact but prevents new placement while over allowance |
| Naval blockade screening | Deferred; ordinary YG naval movement still applies |
| ELO/faction bids | Manual separate budgets; do not pool them to buy a unit |
| AI | Recognizes ordinary d10 values and truck transport; combat/noncombat previews are safe during Politics/Purchase and preserve the move's actual rule context; tactical planning for stacking, rail quotas and remaining special rules still needs work |

## Validation

The map has been loaded and initialized by the real TripleA parser. Focused
tests cover unit values and purchase eligibility, d10 hit boundaries, defensive
support limits, factory capacity, truck/air cargo limits, move/undo, persistent
awards, score comparison, original-territory convoy awards, and save/reload.
Movement follow-up tests cover railroad quotas across split moves and save/reload,
truck separation, improved transport manifests, per-side/allied stacking,
airlift cargo, landing reservations, placement, and preserving starting excess.
Existing movement, transport, and production regression tests also passed.

The complete game-core and game-headed checks (including formatting and PMD)
and desktop distribution build passed: 2,601 game-core tests, including 89
MOD ECR integration cases, and 78 desktop tests, with zero failures.
AI regression coverage includes movement previews during Politics/Purchase,
combat aircraft exemptions during Politics, and German attack/defense option
enumeration during Politics.
Combat follow-up coverage includes anti-tank priority/overflow and round limits,
separate specialist fire, bomber withdrawal, full bomber-versus-transport hit/miss
battles, one-roll heavy bombers against transports, and aircraft reversion/state.
Defending-bomber tests cover destruction on ground capture, survival when held
or attacked only by aircraft, heavy bombers, and no fire/casualty exposure in round two.
An interactive tabletop playtest remains useful for the deferred rules and
provisional map artwork.

The supplied global_40_expansion_uhd_boxes map was inspected as a mine reference.
Its notes explicitly use Edit mode for naval mine placement/removal and manual
handling; it supplies nation/colour/border markers rather than an automated ECR
entry-roll implementation. ECR's destroyer caps, one roll per movement phase,
and removal on successful hits take precedence. No files in that map were changed.

Interception tests cover simultaneous fire, normal/jet hit boundaries, normal
AAA removal before interception, and save/resume without rerolling. Tactical
tests cover category selection, submarine/destroyer and transport restrictions,
fixed accuracy, excess hits, exhausted categories, and persisted choices.
