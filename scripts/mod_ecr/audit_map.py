"""Audit the generated ECR map without a Java installation (stdlib only)."""

import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAP = ROOT / "custom_maps/global_1940_mod_ecr"
if not (MAP / "unit-spec.json").exists() and (MAP / "global_1940_mod_ecr/unit-spec.json").exists():
    MAP = MAP / "global_1940_mod_ecr"


def main():
    root = ET.parse(MAP / "map/games/global_1940_mod_ecr.xml").getroot()
    spec = json.loads((MAP / "unit-spec.json").read_text(encoding="utf-8"))
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    attachments = root.findall("attachmentList/attachment")
    names = {a.get("name") for a in attachments}
    types = {u.get("name") for u in root.findall("unitList/unit")}
    require(types == set(spec), "Unit list differs from unit-spec.json")
    require(root.find("diceSides").get("value") == "10", "Combat dice must be d10")
    costs = {r.find("result").get("resourceOrUnit"): int(r.find("cost").get("quantity"))
             for r in root.findall("production/productionRule")
             if not r.get("name").endswith("Shipyards")}
    for unit, values in spec.items():
        a = root.find(f"attachmentList/attachment[@attachTo='{unit}'][@name='unitAttachment']")
        require(a is not None, f"Missing attachment: {unit}")
        if a is None:
            continue
        for field in ("attack", "defense", "movement"):
            options = a.findall(f"option[@name='{field}']")
            require(len(options) == 1 and int(options[0].get("value")) == values[field],
                    f"Incorrect {field}: {unit}")
        require(costs.get(unit) == values["cost"], f"Incorrect price: {unit}")
    for a in attachments:
        for option in a.findall("option"):
            if option.get("name") in ("conditions", "trigger"):
                for reference in option.get("value").split(":"):
                    require(reference in names, f"Missing condition {reference} in {a.get('name')}")
    frontier = root.find("production/productionFrontier[@name='production']")
    rules = {r.get("name"): r.find("result").get("resourceOrUnit")
             for r in root.findall("production/productionRule")}
    purchased = {rules[r.get("name")] for r in frontier.findall("frontierRules")}
    for unit, values in spec.items():
        if not values.get("purchase", True):
            require(unit not in purchased, f"Conversion/upgrade-only unit is purchasable: {unit}")
    for folder in (MAP / "map/units").iterdir():
        if folder.is_dir():
            for unit in types:
                require((folder / (unit + ".png")).exists(), f"Missing image: {folder.name}/{unit}")
    for unit in ("bomber", "heavy_bomber", "tactical_bomber"):
        a = root.find(f"attachmentList/attachment[@attachTo='{unit}'][@name='unitAttachment']")
        require(a.find("option[@name='bombingMaxDieSides']").get("value") == "6",
                f"Bombing damage must be d6: {unit}")
    require(not any(s.get("name").endswith("Tech") for s in root.findall("gamePlay/sequence/step")),
            "Unfinished research must not use combat dice")
    occupation = root.find("attachmentList/attachment[@name='conditionAttachment_ECR_Allies_Occupation_Goal']")
    require(set(occupation.find("option[@name='alliedOwnershipTerritories']").get("value").split(":"))
            == {"Poland", "Finland", "Bulgaria", "Iraq"}, "Occupation territory set is incorrect")
    if errors:
        raise SystemExit("ECR audit failed:\n" + "\n".join(errors))
    print(f"ECR audit passed: {len(types)} unit types, values, costs, eligibility, assets, dice and condition references.")


if __name__ == "__main__":
    main()
