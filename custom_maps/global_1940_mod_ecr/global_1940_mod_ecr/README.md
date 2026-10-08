# Global 1940 MOD ECR

A standalone local variant of Young Grasshoppers Tournament Edition 1.0.4,
implementing MOD ECR v3.0 and the user's clarified rules. Original map imagery
and credits are retained. This map requires the accompanying TripleA engine
changes for convoy damage, factory capacity, land/air transport, railroads and
land stacking, special combat, capital-ship bounties and naval mines. Germany starts with 17 counted units against its limit of 15;
move at least two counted units out before ending the first Combat Move.

To install, copy this entire `global_1940_mod_ecr` directory into TripleA's
configured maps folder (normally `downloadedMaps`), then select **Global 1940
MOD ECR v3.0**. Do not install only the XML. Its artwork is bundled.

The desktop client with the engine changes is built under
`game-app/game-headed/build/install/game-headed/`. On Windows, launch it from
the repository with:

```powershell
./scripts/mod_ecr/run.ps1
```

That script uses the portable JDK 25 in `build/tools/jdk25` when available,
without changing your system JDK 17. In TripleA settings, you may point the map
folder directly at this repository's `custom_maps` directory instead of copying
the map elsewhere. The script builds the desktop distribution if it is absent.

Use `unit-spec.json` as the agreed roster reference. Conversion/upgrade-only
entries record the value of the originating unit; they are not direct purchases.
Provisional art is reused from the supplied map and some icons are shared.
The supplied NavalMine.png and reconnaissancePlane.png are installed for every
owner. These shared icons replace the placeholders; ownership remains available
in the game's unit information. Map regeneration preserves the supplied artwork.

The in-game notes describe manual aircraft reclassification and deferred rules.
Read [RULE_STATUS.md](RULE_STATUS.md) before a rules-sensitive game.

To rebuild from the untouched local baseline, with Python and `lxml` installed:

```powershell
python scripts/mod_ecr/build_map.py
python scripts/mod_ecr/audit_map.py
```

The builder writes only this variant. Rebuilding overwrites its generated XML,
copied assets, notifications, and objective tab; edit the builder, roster, and
ECR companion notes as the source of those changes. Rule documents and the
supplied baseline are local inputs excluded from Git by the user's configuration.

Naval mines can now be laid at the start of Purchase. Each eligible sea zone
prompts for a 2 IPC mine: select the displayed destroyer to lay it, or None to
skip that zone. Mines trigger automatically on enemy ship entry/transit in
Combat Move and Noncombat Move. Mine rolls make movement irreversible.
Capital-ship bounties are recorded automatically and paid at the active turn's
end, including rewards earned by defending nations. AI ships follow mine rules;
AI mine purchases and route planning are deferred.
