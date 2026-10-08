"""Rebuild the standalone ECR map from the untouched local YG baseline.

Requires lxml. Run from any directory: python scripts/mod_ecr/build_map.py.
Only writes custom_maps/global_1940_mod_ecr; never modifies the source map.
"""

import copy
import json
from pathlib import Path
import shutil

from lxml import etree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "local_maps/young_grasshoppers_tournament_edition-master"
DEST = ROOT / "custom_maps/global_1940_mod_ecr"
if not (DEST / "unit-spec.json").exists() and (DEST / "global_1940_mod_ecr/unit-spec.json").exists():
    DEST = DEST / "global_1940_mod_ecr"
MAP_NAME = "global_1940_mod_ecr"
GAME_NAME = "Global 1940 MOD ECR v3.0"
AXIS = ["Germans", "Japanese", "Italians"]
ALLIES = ["British", "UK_Pacific", "Chinese", "Russians", "Americans", "French"]
PLAYERS = AXIS + ALLIES
PREFIX = "games.strategy.triplea.attachments."


def option(parent, name, value, count=None):
    attributes = {"name": name, "value": str(value)}
    if count is not None:
        attributes["count"] = str(count)
    return ET.SubElement(parent, "option", **attributes)


def set_option(parent, name, value, count=None):
    for node in parent.findall(f"option[@name='{name}']"):
        parent.remove(node)
    return option(parent, name, value, count)


def attachment(root, name, player, kind="RulesAttachment", attachment_type="player"):
    return ET.SubElement(root.find("attachmentList"), "attachment", name=name,
                         attachTo=player, javaClass=PREFIX + kind, type=attachment_type)


def named(root, name, target=None):
    return next(a for a in root.findall("attachmentList/attachment")
                if a.get("name") == name and (target is None or a.get("attachTo") == target))


def prop(root, name, value, value_type="boolean"):
    parent = root.find("propertyList")
    node = parent.find(f"property[@name='{name}']")
    if node is None:
        node = ET.SubElement(parent, "property", name=name, editable="false")
        if value_type:
            ET.SubElement(node, value_type)
    node.set("value", str(value))


def ownership(node, territories, allied=False, minimum=None):
    option(node, "alliedOwnershipTerritories" if allied else "directOwnershipTerritories",
           ":".join(territories), minimum if minimum is not None else len(territories))


def condition(root, key, player, territories=(), allied=False, minimum=None):
    node = attachment(root, "conditionAttachment_ECR_" + key, player)
    if territories:
        ownership(node, territories, allied, minimum)
    return node


def objective(root, key, player, reward, territories=(), allied=False, minimum=None,
              war=None, once=False):
    node = attachment(root, "objectiveAttachment_ECR_" + key, player)
    option(node, "objectiveValue", reward)
    if territories:
        ownership(node, territories, allied, minimum)
    if war:
        option(node, "atWarPlayers", ":".join(war), 1)
    if once:
        option(node, "uses", 1)
    return node


def support(root, key, giver, recipients, side, number, bonus_type):
    node = attachment(root, "supportAttachment_ECR_" + key, giver,
                      "UnitSupportAttachment", "unitType")
    for name, value in {"unitType": ":".join(recipients), "faction": "allied",
                        "side": side, "dice": "strength", "bonus": 1,
                        "number": number, "bonusType": bonus_type,
                        "players": ":".join(PLAYERS + ["Dutch"])}.items():
        option(node, name, value)


def configure_units(root, spec):
    for node in list(root.findall("attachmentList/attachment")):
        if node.get("javaClass") == PREFIX + "UnitSupportAttachment":
            node.getparent().remove(node)
        elif node.get("name", "").startswith("triggerAttachment_") and "Improved_Mech_Inf" in node.get("name", ""):
            node.getparent().remove(node)
    for unit, stats in spec.items():
        ua = root.find(f"attachmentList/attachment[@attachTo='{unit}'][@name='unitAttachment']")
        if ua is None:
            ET.SubElement(root.find("unitList"), "unit", name=unit)
            if "base" in stats:
                ua = copy.deepcopy(named(root, "unitAttachment", stats["base"]))
                ua.set("attachTo", unit)
                root.find("attachmentList").append(ua)
            else:
                ua = attachment(root, "unitAttachment", unit, "UnitAttachment", "unitType")
                if unit in ("anti_tank_gun", "mech_anti_tank_gun", "truck"):
                    option(ua, "transportCost", 3)
                elif unit in ("recon_plane", "transport_plane", "cargo_plane"):
                    option(ua, "isAir", "true")
                elif unit == "naval_mine":
                    option(ua, "isSea", "true")
                    option(ua, "isInfrastructure", "true")
        for field in ("movement", "attack", "defense"):
            set_option(ua, field, stats[field])

    for unit in ("artillery", "self_propelled_artillery", "armour", "heavy_tank"):
        support(root, unit + "_defense", unit, ["infantry"], "defence", 2, "infantry_defense")
    support(root, "mech_attack", "artillery", ["mech_infantry"], "offence", 1, "infantry_attack")
    support(root, "spa_attack", "self_propelled_artillery", ["infantry", "mech_infantry"],
            "offence", 2, "infantry_attack")
    for unit in ("mech_infantry",):
        ua = named(root, "unitAttachment", unit)
        set_option(ua, "receivesAbilityWhenWith", "canBlitz:armour")
        option(ua, "receivesAbilityWhenWith", "canBlitz:heavy_tank")
    for unit in ("cruiser", "radar_cruiser"):
        ua = named(root, "unitAttachment", unit)
        for name, value in {"isAAforCombatOnly": "true", "maxAAattacks": 2,
                            "maxRoundsAA": 1, "attackAA": 1, "offensiveAttackAA": 0,
                            "attackAAmaxDieSides": 10}.items():
            set_option(ua, name, value)
    for unit in ("truck", "transport_plane", "cargo_plane"):
        set_option(named(root, "unitAttachment", unit), "canNotMoveDuringCombatMove", "true")
    truck = named(root, "unitAttachment", "truck")
    option(truck, "isLandTransport", "true")
    option(truck, "transportCapacity", 6)
    set_option(named(root, "unitAttachment", "infantry"), "isLandTransportable", "true")
    set_option(named(root, "unitAttachment", "artillery"), "isLandTransportable", "true")
    mech = named(root, "unitAttachment", "mech_infantry")
    set_option(mech, "isLandTransport", "true")
    set_option(mech, "transportCapacity", 3)
    for unit in ("armour", "heavy_tank"):
        set_option(named(root, "unitAttachment", unit), "isLandTransport", "false")
    for unit in spec:
        ua = named(root, "unitAttachment", unit)
        cargo_cost = ua.find("option[@name='transportCost']")
        if cargo_cost is not None and int(cargo_cost.get("value")) > 0:
            set_option(ua, "isAirTransportable", "true")
    for unit, capacity in (("bomber", 4), ("heavy_bomber", 4),
                           ("transport_plane", 4), ("cargo_plane", 3)):
        ua = named(root, "unitAttachment", unit)
        set_option(ua, "isAirTransport", "true")
        set_option(ua, "transportCapacity", capacity)
    tac = named(root, "unitAttachment", "tactical_bomber")
    for field in ("isAirTransport", "transportCapacity"):
        for node in tac.findall(f"option[@name='{field}']"):
            tac.remove(node)
    set_option(named(root, "unitAttachment", "jet_fighter"), "airAttack", 3)
    set_option(named(root, "unitAttachment", "jet_fighter"), "airDefense", 3)
    set_option(named(root, "unitAttachment", "super_carrier"), "carrierCapacity", 3)
    set_option(named(root, "unitAttachment", "improved_transport"), "transportCapacity", 8)
    for unit in ("heavy_bomber", "super_battleship"):
        set_option(named(root, "unitAttachment", unit), "attackRolls", 2)
    for unit in ("radar_aa", "radar_cruiser", "radar_factory_minor",
                 "radar_factory_major", "radar_airfield", "radar_harbour"):
        set_option(named(root, "unitAttachment", unit), "attackAA", 3)
        set_option(named(root, "unitAttachment", unit), "attackAAmaxDieSides", 10)
    set_option(named(root, "unitAttachment", "radar_airfield"), "maxScrambleCount", -1)
    for unit in ("carrier", "super_carrier", "cruiser", "radar_cruiser", "battleship",
                 "super_battleship", "destroyer", "submarine", "super_submarine"):
        set_option(named(root, "unitAttachment", unit), "blockade", 1)
    for unit in ("fighter", "jet_fighter", "tactical_bomber"):
        set_option(named(root, "unitAttachment", unit), "blockade", 0)
    for unit in ("airfield", "radar_airfield"):
        ua = named(root, "unitAttachment", unit)
        for air in ("recon_plane", "transport_plane", "cargo_plane", "jet_fighter", "heavy_bomber"):
            option(ua, "givesMovement", "1:" + air)
    for unit in ("harbour", "radar_harbour"):
        ua = named(root, "unitAttachment", unit)
        set_option(ua, "repairsUnits", "battleship:carrier:super_battleship:super_carrier")
        for sea in ("super_submarine", "super_carrier", "super_battleship", "improved_transport", "radar_cruiser"):
            option(ua, "givesMovement", "1:" + sea)
    set_option(named(root, "playerAttachment", "Japanese"), "suicideAttackResources", "SuicideAttackTokens", 3)
    pa = named(root, "playerAttachment", "British")
    for node in pa.findall("option[@name='shareTechnology']"):
        pa.remove(node)
    for ua in root.findall("attachmentList/attachment[@name='techAttachment']"):
        for node in ua.findall("option[@name='Airborne_Forces']"):
            ua.remove(node)
        set_option(ua, "paratroopers", "true")
    for tech, unit, field in (("superSub", "submarine", "attackBonus"),
                              ("jetPower", "fighter", "attackBonus")):
        set_option(named(root, "techAbilityAttachment", tech), field, "2:" + unit)
    jet = named(root, "techAbilityAttachment", "jetPower")
    set_option(jet, "airAttackBonus", "2:fighter")
    set_option(jet, "airDefenseBonus", "2:fighter")
    radar = named(root, "techAbilityAttachment", "aARadar")
    for node in radar.findall("option[@name='radarBonus']"):
        node.set("value", node.get("value").replace("1:", "2:", 1))
    option(radar, "radarBonus", "2:cruiser")
    lr = named(root, "techAbilityAttachment", "longRangeAir")
    for node in list(lr):
        lr.remove(node)
    for unit, stats in spec.items():
        if stats.get("movement") in (4, 6) and unit not in ("naval_mine",):
            option(lr, "movementBonus", "2:" + unit)
    production = root.find("production")
    existing = {r.find("result").get("resourceOrUnit"): r
                for r in production.findall("productionRule") if not r.get("name").endswith("Shipyards")}
    for unit, stats in spec.items():
        rule = existing.get(unit)
        if rule is None:
            rule = ET.SubElement(production, "productionRule", name="buyEcr_" + unit)
            ET.SubElement(rule, "cost", resource="PUs", quantity=str(stats["cost"]))
            ET.SubElement(rule, "result", resourceOrUnit=unit, quantity="1")
            if stats.get("purchase", True):
                ET.SubElement(production.find("productionFrontier[@name='production']"),
                              "frontierRules", name=rule.get("name"))
        else:
            rule.find("cost").set("quantity", str(stats["cost"]))
    shipyard_costs = {"transport": 5, "carrier": 13, "destroyer": 7,
                     "cruiser": 8, "battleship": 16, "submarine": 5}
    for rule in production.findall("productionRule"):
        if rule.get("name").endswith("Shipyards"):
            rule.find("cost").set("quantity", str(shipyard_costs[rule.find("result").get("resourceOrUnit")]))
    # Research is deliberately last: retain legacy definitions for parser references,
    # but remove the phases so nobody can accidentally research with d10 dice.
    sequence = root.find("gamePlay/sequence")
    for step in list(sequence):
        if step.get("name", "").endswith(("Tech", "TechActivation")):
            sequence.remove(step)
    prop(root, "Paratroopers Can Move During Non Combat", "true")
    prop(root, "LHTR Heavy Bombers", "false")
    prop(root, "Convoy Blockades Count All Dice", "true")
    prop(root, "Factory Production Limited By Territory Value", "true")
    prop(root, "Land Transport Without Technology", "true")
    prop(root, "MOD ECR Rules", "true")


def configure_objectives(root):
    for node in list(root.findall("attachmentList/attachment")):
        name = node.get("name", "")
        if (name.startswith("objectiveAttachment") or
                name in ("triggerAttachment_Russians_1_Lend_Lease",
                         "triggerAttachment_Americans_5_Presence_In_France",
                         "triggerAttachment_Germans_4_Presence_In_Egypt",
                         "triggerAttachment_Germans_5_Swedish_Iron_Ore")):
            node.getparent().remove(node)
    # Original conditions are retained for political and production triggers.
    objective(root, "German_Trade", "Germans", 5, war=["Russians"]).append(
        ET.Element("option", name="invert", value="true"))
    objective(root, "German_Cities", "Germans", 5, ["Novgorod", "Volgograd", "Russia"],
              minimum="each", war=["Russians"])
    objective(root, "German_Caucasus", "Germans", 5, ["Caucasus"], allied=True, war=["Russians"])
    node = objective(root, "German_Egypt", "Germans", 5, ["Egypt"], allied=True)
    option(node, "directPresenceTerritories", "Egypt", 1)
    option(node, "unitPresence", "infantry:artillery:mech_infantry:armour:anti_tank_gun:mech_anti_tank_gun", 1)
    option(node, "atWarPlayers", "British:French", 2)
    node = objective(root, "German_Sweden", "Germans", 5, ["Denmark", "Norway"])
    option(node, "conditions", "conditionAttachment_Germans_5_Sweden_Not_Enemy")
    option(node, "atWarPlayers", "British:French", 2)
    node = objective(root, "German_Middle_East", "Germans", 2,
                     ["Iraq", "Persia", "Northwest Persia"], minimum="each")
    option(node, "atWarPlayers", "British:French", 2)
    node = objective(root, "German_Submarines", "Germans", 5)
    node.set("javaClass", PREFIX + "ModEcrRulesAttachment")
    option(node, "check", "germanSubmarines")
    option(node, "atWarPlayers", "British:French", 2)
    europe_war = ["Germans", "Italians"]
    objective(root, "Soviet_Berlin", "Russians", 10, ["Germany"], war=europe_war, once=True)
    objective(root, "Soviet_Eastern_Europe", "Russians", 10,
              ["Finland", "Poland", "Slovakia Hungary", "Romania"], minimum=3, war=europe_war)
    node = objective(root, "Soviet_No_Foreign_Allies", "Russians", 5, war=europe_war)
    option(node, "alliedExclusionTerritories", "original")
    for key, location, allies, war in (
            ("Archangel", "Archangel", ["Scotland", "Iceland"], europe_war),
            ("Caucasus", "Caucasus", ["Persia", "Northwest Persia"], europe_war),
            ("Amur", "Amur", ["Aleutian Islands", "Alaska"], ["Japanese"])):
        node = condition(root, "Soviet_" + key, "Russians", [location])
        option(node, "alliedOwnershipTerritories", ":".join(allies), len(allies))
        option(node, "atWarPlayers", ":".join(war), 1)
        trigger = attachment(root, "triggerAttachment_ECR_Soviet_" + key, "Russians", "TriggerAttachment")
        option(trigger, "conditions", node.get("name"))
        option(trigger, "placement", location + ":artillery")
        option(trigger, "when", "after:russiansEndTurn")
    japan_war = ["Chinese", "Americans", "British", "UK_Pacific", "French", "Russians"]
    objective(root, "Japan_Calcutta", "Japanese", 5, ["India"], allied=True, war=japan_war)
    china = ["Kansu", "Tsinghai", "Sikang", "Kiangsu", "Shantung", "Hopei", "Jehol", "Kweichow",
             "Hunan", "Kiangsi", "Yunnan", "Anhwe", "Chahar", "Suiyuyan", "Shensi", "Szechwan", "Kwangsi", "Manchuria"]
    objective(root, "Japan_China", "Japanese", 10, china, allied=True, war=japan_war, once=True)
    objective(root, "Japan_Islands", "Japanese", 3,
              ["Guam", "Midway", "Wake Island", "Gilbert Islands", "Solomon Islands", "Fiji",
               "Philippines", "Johnston Island", "Line Islands", "Hawaiian Islands"],
              allied=True, minimum="each", war=japan_war)
    objective(root, "Japan_Burma", "Japanese", 5, ["Burma"], allied=True, war=japan_war)
    objective(root, "Japan_Pacific_Chain", "Japanese", 5,
              ["Guam", "Midway", "Wake Island", "Gilbert Islands", "Solomon Islands"], allied=True, war=japan_war)
    objective(root, "Japan_Capitals", "Japanese", 5,
              ["India", "New South Wales", "Hawaiian Islands", "Western United States"],
              allied=True, minimum="each", war=japan_war)
    objective(root, "Japan_Dutch_Indies", "Japanese", 5,
              ["Sumatra", "Java", "Borneo", "Celebes"], allied=True, war=japan_war)
    for key, value, territories in (
            ("Core", 10, ["Eastern United States", "Central United States"]),
            ("Perimeter", 5, ["Alaska", "Aleutian Islands", "Hawaiian Islands", "Johnston Island", "Line Islands"]),
            ("Caribbean", 5, ["Southeast Mexico", "Central America", "West Indies"]),
            ("Philippines", 5, ["Philippines"])):
        objective(root, "USA_" + key, "Americans", value, territories, war=AXIS)
    # The shared condition also controls China's artillery production frontier.
    burma = named(root, "conditionAttachmentJapaneseDoNotControlBurmaRoad")
    for node in list(burma):
        burma.remove(node)
    ownership(burma, ["India", "Burma", "Yunnan"], allied=True)
    node = objective(root, "China_Burma_Road", "Chinese", 6, war=["Japanese"])
    option(node, "conditions", burma.get("name"))
    for trigger_name in ("triggerAttachment_Chinese_Artillery_Supplies", "triggerAttachment_Chinese_Loses_Burma_Road"):
        trigger = named(root, trigger_name)
        for field in ("conditions", "trigger"):
            for reference in trigger.findall(f"option[@name='{field}']"):
                reference.set("value", reference.get("value").replace(
                    "objectiveAttachment_Chinese_1_Burma_Road", node.get("name")))
    italy_war = ALLIES
    node = objective(root, "Italy_Mediterranean", "Italians", 5, war=italy_war)
    option(node, "enemySurfaceExclusionTerritories", ":".join(f"{n} Sea Zone" for n in range(92, 100)), 8)
    objective(root, "Italy_Strategic", "Italians", 5,
              ["Gibraltar", "Southern France", "Greece", "Egypt"], allied=True, minimum=3, war=italy_war)
    objective(root, "Italy_North_Africa", "Italians", 5,
              ["Morocco", "Algeria", "Tunisia", "Libya", "Tobruk", "Alexandria"], allied=True, war=italy_war)
    objective(root, "Italy_Middle_East", "Italians", 2,
              ["Iraq", "Persia", "Northwest Persia"], minimum="each", war=italy_war)
    objective(root, "Italy_Islands", "Italians", 5,
              ["Cyprus", "Crete", "Malta", "Sardinia", "Sicily"], allied=True, war=italy_war)
    objective(root, "Italy_East_Africa", "Italians", 5,
              ["British Somaliland", "Italian Somaliland", "Ethiopia", "Anglo Egyptian Sudan", "Kenya"],
              allied=True, war=italy_war)
    africa = ["Egypt", "Anglo Egyptian Sudan", "Union of South Africa", "Gold Coast", "Nigeria",
              "British Somaliland", "Kenya", "Tanganyika Territory", "Rhodesia", "South West Africa"]
    original_uk = [x.get("territory") for x in root.findall("initialize/ownerInitialize/territoryOwner")
                   if x.get("owner") == "British"]
    objective(root, "UK_Colonies", "British", 5, [t for t in africa if t in original_uk])
    objective(root, "UK_Commonwealth", "British", 5, [t for t in original_uk if t not in africa])
    node = objective(root, "UK_Mediterranean", "British", 5)
    option(node, "enemyExclusionTerritories", ":".join(f"{n} Sea Zone" for n in range(92, 100)), 8)
    option(node, "unitPresence", "battleship:carrier:submarine:destroyer:cruiser:super_submarine:super_battleship:super_carrier:radar_cruiser", 0)
    land = "infantry:artillery:mech_infantry:armour:anti_tank_gun:mech_anti_tank_gun:truck:aaGun:self_propelled_artillery:heavy_tank:radar_aa"
    node = objective(root, "UK_France", "British", 5)
    option(node, "directPresenceTerritories", "France:Normandy Bordeaux:Southern France", 1)
    option(node, "unitPresence", land, 1)
    for key, territory, allied in (("Hong_Kong", "Kwangtung", False),
                                   ("Malaya", "Malaya", False), ("Shanghai", "Kiangsu", True)):
        objective(root, "Pacific_" + key, "UK_Pacific", 5, [territory], allied=allied, war=["Japanese"])
    node = objective(root, "Pacific_Perimeter", "UK_Pacific", 5, war=["Japanese"])
    option(node, "directPresenceTerritories", "Sumatra:Java:Celebes:Dutch New Guinea", 1)
    option(node, "unitPresence", land, 1)
    for name in ("triggerAttachment_British_Lend_Lease_From_US", "triggerAttachment_British_Lend_Lease_From_US_Notification"):
        node = named(root, name)
        set_option(node, "when", "after:britishPlace")
        set_option(node, "uses", -1)
    lend = named(root, "conditionAttachment_British_Lend_Lease_Eligible")
    for node in list(lend):
        lend.remove(node)
    option(lend, "players", "Americans")
    option(lend, "atWarPlayers", ":".join(AXIS), 0)
    india = named(root, "triggerAttachment_UK_Pacific_War_Production_India")
    for node in india.findall("option[@name='when']"):
        india.remove(node)
    for step in root.findall("gamePlay/sequence/step"):
        if step.get("name", "").endswith("Politics"):
            option(india, "when", "after:" + step.get("name"))
    configure_victory(root, china)


def configure_victory(root, china):
    for node in list(root.findall("attachmentList/attachment")):
        if "_Objective" in node.get("name", "") or node.get("name") == "triggerAttachment_End_Game_Notificaion":
            node.getparent().remove(node)
    africa = ["Morocco", "Algeria", "Tunisia", "Libya", "Tobruk", "Alexandria", "Egypt",
              "French West Africa", "Gold Coast", "French Central Africa", "Nigeria", "French Equatorial Africa",
              "Anglo Egyptian Sudan", "Ethiopia", "British Somaliland", "Italian Somaliland", "Kenya",
              "Belgian Congo", "Tanganyika Territory", "Rhodesia", "South West Africa", "Union of South Africa", "French Madagascar"]
    asia = ["India", "Malaya", "Kwangtung", "Kiangsu"]
    goals = {
        "Axis": [
            ("Supremacy", ["Eastern United States", "Western United States"], 1),
            ("London", ["United Kingdom"], 1), ("Sydney", ["New South Wales"], 1), ("Moscow", ["Russia"], 1),
            ("China", china, 18),
            ("Europe", ["Eastern United States", "Ontario", "Union of South Africa", "Egypt", "United Kingdom",
                        "Russia", "Novgorod", "Volgograd", "Poland", "Germany", "Southern Italy", "France"], 7),
            ("Pacific", ["Philippines", "Guam", "Wake Island", "Johnston Island", "Hawaiian Islands", "Midway", "Line Islands", "Aleutian Islands"], 8),
            ("Africa", ["Morocco", "Algeria", "Tunisia", "Libya", "Tobruk", "Alexandria", "Egypt"], 7),
            ("Asia", asia, 4), ("Economy", [], None)],
        "Allies": [
            ("Supremacy", ["Germany", "Japan"], 1), ("Rome", ["Southern Italy"], 1), ("China", china, 18),
            ("Europe", ["France"], 1),
            ("Pacific", ["Okinawa", "Iwo Jima", "Marianas", "Marshall Islands", "Caroline Islands", "Paulau Island", "Hainan", "Formosa"], 8),
            ("Africa", africa, 23), ("Asia", asia, 4),
            ("Occupation", ["Poland", "Bulgaria", "Finland", "Iraq"], 4),
            ("Diplomacy", ["Eire", "Yugoslavia", "Greece", "Crete", "Northwest Persia", "Persia", "Eastern Persia", "Brazil"], 8)]}
    switches = {}
    checkpoints = ["after:" + s.get("name") for s in root.findall("gamePlay/sequence/step")
                   if s.get("name", "").endswith(("CombatMove", "Battle", "NonCombatMove", "Place", "EndTurn"))]
    for side, entries in goals.items():
        player = "Germans" if side == "Axis" else "British"
        switches[side] = []
        for key, territories, minimum in entries:
            key = side + "_" + key
            switch = condition(root, key + "_Earned", player)
            option(switch, "switch", "false")
            switches[side].append(switch.get("name"))
            unmet = condition(root, key + "_Unmet", player)
            option(unmet, "conditions", switch.get("name"))
            option(unmet, "invert", "true")
            goal = condition(root, key + "_Goal", player, territories, True, minimum)
            if key == "Axis_Economy":
                goal.set("javaClass", PREFIX + "ModEcrRulesAttachment")
                option(goal, "check", "axisEconomy")
            if key == "Allies_Europe":
                set_option(goal, "directOwnershipTerritories", "France", 1)
                option(goal, "players", "French")
                option(goal, "conditions", "conditionAttachment_French_1_Liberation_Switch")
            trigger = attachment(root, "triggerAttachment_ECR_" + key, player, "TriggerAttachment")
            option(trigger, "conditions", goal.get("name") + ":" + unmet.get("name"))
            option(trigger, "players", player)
            option(trigger, "playerAttachmentName", "RulesAttachment", switch.get("name"))
            option(trigger, "playerProperty", "switch", "true")
            for checkpoint in checkpoints:
                option(trigger, "when", checkpoint)
            option(trigger, "uses", 1)
    # Compare persistent earned switches at the end of round 8. Attrition is a
    # tie-break, not a tenth freely obtainable Allied token.
    for axis_score in range(11):
        axis_count = condition(root, f"Final_Axis_{axis_score}", "French")
        option(axis_count, "conditions", ":".join(switches["Axis"]))
        option(axis_count, "conditionType", axis_score)
        for allied_score in range(10):
            allied_count = condition(root, f"Final_Allies_{axis_score}_{allied_score}", "French")
            option(allied_count, "conditions", ":".join(switches["Allies"]))
            option(allied_count, "conditionType", allied_score)
            trigger = attachment(root, f"triggerAttachment_ECR_Final_{axis_score}_{allied_score}",
                                 "French", "TriggerAttachment")
            option(trigger, "conditions", "conditionAttachment_Game_Round_8:" + axis_count.get("name") + ":" + allied_count.get("name"))
            option(trigger, "when", "after:endRoundStep")
            winner = "Axis" if axis_score > allied_score else "Allies"
            option(trigger, "victory", f"ECR_{winner}_Victory")
            option(trigger, "players", ":".join(AXIS if winner == "Axis" else ALLIES))
            option(trigger, "uses", 1)
    (DEST / "map/notifications.properties").write_text(
        (DEST / "map/notifications.properties").read_text(encoding="utf-8") +
        "\nECR_Axis_Victory=Axis win MOD ECR after eight rounds.\n"
        "ECR_Allies_Victory=Allies win MOD ECR after eight rounds (including tied scores).\n", encoding="utf-8")


def copy_assets(spec):
    custom_images = {
        "naval_mine": ROOT / "local_rule_docs/NavalMine.png",
        "recon_plane": ROOT / "local_rule_docs/reconnaissancePlane.png",
    }
    for folder in (DEST / "map/units").iterdir():
        if not folder.is_dir():
            continue
        for unit, stats in spec.items():
            image = folder / (unit + ".png")
            custom_image = custom_images.get(unit)
            if custom_image and custom_image.exists():
                shutil.copy2(custom_image, image)
                continue
            if image.exists():
                continue
            preferred = folder / (stats.get("image", stats.get("base", unit)) + ".png")
            candidates = [preferred, folder / "artillery.png", DEST / "map/units/_rocket.png"]
            source = next(p for p in candidates if p.exists())
            shutil.copy2(source, image)
            # Hit art is required for the new two-hit naval variants.
            base = stats.get("base")
            if base and (folder / (base + "_hit.png")).exists():
                shutil.copy2(folder / (base + "_hit.png"), folder / (unit + "_hit.png"))


def order_attachments(root):
    """The engine resolves attachment references as it parses, in document order."""
    parent = root.find("attachmentList")
    pending = list(parent)
    ready = set()
    ordered = []
    while pending:
        progressed = False
        for node in list(pending):
            dependencies = set()
            for item in node.findall("option"):
                if item.get("name") in ("conditions", "trigger"):
                    dependencies.update(item.get("value").split(":"))
                if item.get("name") == "playerAttachmentName" and item.get("count", "").startswith("conditionAttachment"):
                    dependencies.add(item.get("count"))
            if dependencies <= ready:
                ordered.append(node)
                ready.add(node.get("name"))
                pending.remove(node)
                progressed = True
        if not progressed:
            raise ValueError("Unresolved attachment dependencies: " + ", ".join(n.get("name") for n in pending))
    parent[:] = ordered


def main():
    if not SOURCE.is_dir():
        raise SystemExit(f"YG baseline is missing: {SOURCE}")
    spec = json.loads((DEST / "unit-spec.json").read_text(encoding="utf-8"))
    shutil.copytree(SOURCE / "map", DEST / "map", dirs_exist_ok=True)
    parser = ET.XMLParser(remove_blank_text=True, remove_comments=True)
    tree = ET.parse(str(SOURCE / "map/games/young_grasshoppers_tournament_edition.xml"), parser)
    root = tree.getroot()
    root.find("info").set("name", GAME_NAME)
    root.find("info").set("version", "0.1.0")
    dice = root.find("diceSides")
    if dice is None:
        dice = ET.SubElement(root, "diceSides")
    dice.set("value", "10")
    configure_units(root, spec)
    configure_objectives(root)
    order_attachments(root)
    prop(root, "mapName", MAP_NAME, None)
    notes = (DEST / "map/games/global_1940_mod_ecr.notes.html").read_text(encoding="utf-8")
    old_notes = root.find("propertyList/property[@name='notes']")
    if old_notes is not None:
        old_notes.getparent().remove(old_notes)
    node = ET.SubElement(root.find("propertyList"), "property", name="notes", editable="false")
    ET.SubElement(node, "value").text = ET.CDATA(notes)
    copy_assets(spec)
    # The original game's companion files must not create a second selectable game.
    for name in ("young_grasshoppers_tournament_edition.xml", "young_grasshoppers_tournament_edition.notes.html"):
        (DEST / "map/games" / name).unlink(missing_ok=True)
    tree.write(str(DEST / "map/games/global_1940_mod_ecr.xml"), encoding="UTF-8",
               xml_declaration=True, pretty_print=True, doctype='<!DOCTYPE game SYSTEM "game.dtd">')
    (DEST / "map.yml").write_text(
        f"map_name: {MAP_NAME}\nversion: 1\ngames:\n"
        f"  - game_name: {GAME_NAME}\n    file_name: global_1940_mod_ecr.xml\n", encoding="utf-8")
    # Rebuild the objective tab from the actual new objective names.
    rows = ["Global_1940_MOD_ECR_v3.0.TABLEGROUP.01;Germans=" + ";".join(
        a.get("name") for a in root.findall("attachmentList/attachment")
        if a.get("name", "").startswith("objectiveAttachment") and a.get("attachTo") == "Germans")]
    for index, player in enumerate(PLAYERS[1:], 2):
        rows.append(f"Global_1940_MOD_ECR_v3.0.TABLEGROUP.{index:02};{player}=" + ";".join(
            a.get("name") for a in root.findall("attachmentList/attachment")
            if a.get("name", "").startswith("objectiveAttachment") and a.get("attachTo") == player))
    for a in root.findall("attachmentList/attachment"):
        if a.get("name", "").startswith("objectiveAttachment"):
            reward = a.find("option[@name='objectiveValue']").get("value")
            rows.append(f"Global_1940_MOD_ECR_v3.0.{a.get('attachTo')};{a.get('name')}={a.get('name').removeprefix('objectiveAttachment_ECR_').replace('_',' ')}: {reward} IPC (see game notes).")
    for index, side in ((10, "Axis"), (11, "Allies")):
        earned = [a for a in root.findall("attachmentList/attachment")
                  if a.get("name", "").startswith("conditionAttachment_ECR_" + side + "_")
                  and a.get("name", "").endswith("_Earned")]
        rows.append(f"Global_1940_MOD_ECR_v3.0.TABLEGROUP.{index};{side} Victory Tokens=" +
                    ";".join(a.get("name") for a in earned))
        for a in earned:
            label = a.get("name").removeprefix("conditionAttachment_ECR_").removesuffix("_Earned").replace("_", " ")
            rows.append(f"Global_1940_MOD_ECR_v3.0.{a.get('attachTo')};{a.get('name')}={label}: 1 permanent victory token")
    (DEST / "map/objectives.properties").write_text("\n".join(rows) + "\n", encoding="utf-8")
    # Normalize copied text without changing coordinate data or source-map files.
    for path in DEST.rglob("*"):
        if path.is_file() and path.suffix in (".txt", ".properties", ".xml", ".html", ".yml"):
            content = b"\n".join(line.rstrip(b" \t\r") for line in path.read_bytes().split(b"\n"))
            path.write_bytes(content.rstrip(b"\n") + b"\n")
    print(f"Built {GAME_NAME}: {len(spec)} unit types, standalone assets at {DEST}")


if __name__ == "__main__":
    main()
