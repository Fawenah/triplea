package games.strategy.triplea.delegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.triplea.Constants;
import games.strategy.triplea.UnitUtils;
import games.strategy.triplea.ai.pro.util.ProTransportUtils;
import games.strategy.triplea.attachments.AbstractConditionsAttachment;
import games.strategy.triplea.attachments.ICondition;
import games.strategy.triplea.attachments.ModEcrRulesAttachment;
import games.strategy.triplea.attachments.RulesAttachment;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.TriggerAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.dice.RollDiceFactory;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.delegate.power.calculator.CombatValueBuilder;
import games.strategy.triplea.delegate.power.calculator.PowerStrengthAndRolls;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.triplea.java.collections.IntegerMap;

class ModEcrMapTest {
  private GameData data;
  private GamePlayer germans;

  @BeforeEach
  void loadMap() {
    data =
        GameParser.parse(
                Path.of("../../custom_maps/global_1940_mod_ecr/map/games/global_1940_mod_ecr.xml"),
                false)
            .orElseThrow(() -> new AssertionError("The MOD ECR map must load in the engine"));
    germans = data.getPlayerList().getPlayerId("Germans");
  }

  @Test
  @DisplayName("The d10 map preserves d6 bombing and disables unfinished research")
  void diceAndPhases() {
    assertThat(data.getDiceSides()).isEqualTo(10);
    assertThat(attachment("bomber").getBombingMaxDieSides()).isEqualTo(6);
    assertThat(attachment("bomber").getBombingBonus()).isEqualTo(2);
    assertThat(attachment("tactical_bomber").getBombingMaxDieSides()).isEqualTo(6);
    assertThat(data.getSequence()).noneMatch(step -> step.getName().endsWith("Tech"));
    assertThat(germans.getTechAttachment().getParatroopers()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "infantry,2,2,1", "armour,5,5,2", "anti_tank_gun,2,3,1",
    "mech_anti_tank_gun,3,5,2", "fighter,4,5,4", "bomber,7,1,6",
    "tactical_bomber,5,3,4", "carrier,1,3,2", "cruiser,5,6,2",
    "aaGun,0,0,1", "truck,0,0,2", "recon_plane,0,0,6",
    "heavy_tank,5,7,2", "jet_fighter,6,5,4", "super_submarine,6,2,2"
  })
  @DisplayName("The actual parser applies ECR values to base and upgraded units")
  void unitValues(final String name, final int attack, final int defense, final int movement) {
    final var unit = attachment(name);
    assertThat(unit.getAttack(germans)).isEqualTo(attack);
    assertThat(unit.getDefense(germans)).isEqualTo(defense);
    assertThat(unit.getMovement(germans)).isEqualTo(movement);
  }

  @Test
  @DisplayName("Conversion-only aircraft and upgraded units are not freely purchasable")
  void purchaseEligibility() {
    assertThat(germans.getProductionFrontier().getRules())
        .flatExtracting(rule -> rule.getResults().keySet())
        .extracting(result -> result.getName())
        .contains("anti_tank_gun", "mech_anti_tank_gun", "truck", "recon_plane")
        .doesNotContain("transport_plane", "cargo_plane", "heavy_tank", "naval_mine");
  }

  @Test
  @DisplayName("Factories respect IPC capacity without altering legacy behavior")
  void factoryCapacity() {
    final var territory = data.getMap().getTerritoryOrNull("Western Germany");
    final var factory = data.getUnitTypeList().getUnitTypeOrThrow("factory_major").create(germans);
    assertThat(UnitUtils.getHowMuchCanUnitProduce(factory, territory, true, true)).isEqualTo(5);
    factory.setUnitDamage(2);
    assertThat(UnitUtils.getHowMuchCanUnitProduce(factory, territory, true, true)).isEqualTo(3);
    factory.setUnitDamage(0);
    data.getProperties().set(Constants.FACTORY_PRODUCTION_LIMITED_BY_TERRITORY_VALUE, false);
    assertThat(UnitUtils.getHowMuchCanUnitProduce(factory, territory, true, true)).isEqualTo(10);
  }

  @Test
  @DisplayName("German submarine pressure counts ships across zones and excludes the Baltic")
  void aggregateSubmarines() {
    final var rule =
        (ModEcrRulesAttachment) germans.getAttachment("objectiveAttachment_ECR_German_Submarines");
    // Remove the war precondition to isolate the aggregate-count rule.
    rule.getPropertyOrEmpty("atWarPlayers").orElseThrow().resetValue();
    for (final var territory : data.getMap().getTerritories()) {
      territory.getUnitCollection().removeAll(territory.getUnits());
    }
    final var submarine = data.getUnitTypeList().getUnitTypeOrThrow("submarine");
    data.getMap()
        .getTerritoryOrNull("113 Sea Zone")
        .getUnitCollection()
        .addAll(submarine.create(5, germans));
    final var bridge = mock(IDelegateBridge.class);
    when(bridge.getData()).thenReturn(data);
    assertThat(rule.isSatisfied(new HashMap<ICondition, Boolean>(), bridge)).isFalse();
    data.getMap()
        .getTerritoryOrNull("109 Sea Zone")
        .getUnitCollection()
        .addAll(submarine.create(3, germans));
    data.getMap()
        .getTerritoryOrNull("110 Sea Zone")
        .getUnitCollection()
        .addAll(submarine.create(2, germans));
    assertThat(rule.isSatisfied(new HashMap<ICondition, Boolean>(), bridge)).isTrue();
  }

  @Test
  @DisplayName("One truck moves three fresh infantry two spaces, but not four or in combat")
  void truckMovement() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var route =
        new Route(
            start,
            data.getMap().getTerritoryOrNull("Western Germany"),
            data.getMap().getTerritoryOrNull("Greater Southern Germany"));
    final var units = new ArrayList<Unit>();
    units.addAll(data.getUnitTypeList().getUnitTypeOrThrow("truck").create(1, germans));
    units.addAll(data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(3, germans));
    start.getUnitCollection().addAll(units);
    assertThat(ProTransportUtils.findBestUnitsToLandTransport(units.get(0), start, Set.of()))
        .hasSize(4);
    final var move = new MoveDescription(units, route);
    assertThat(new MoveValidator(data, true).validateMove(move, germans).isMoveValid()).isTrue();
    data.getProperties().set(Constants.LAND_TRANSPORT_WITHOUT_TECHNOLOGY, false);
    assertThat(new MoveValidator(data, true).validateMove(move, germans).isMoveValid()).isFalse();
    data.getProperties().set(Constants.LAND_TRANSPORT_WITHOUT_TECHNOLOGY, true);
    final var fourth = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(germans);
    start.getUnitCollection().add(fourth);
    units.add(fourth);
    assertThat(
            new MoveValidator(data, true)
                .validateMove(new MoveDescription(units, route), germans)
                .isMoveValid())
        .isFalse();
    units.remove(fourth);
    units.get(1).setAlreadyMoved(BigDecimal.ONE);
    assertThat(
            new MoveValidator(data, true)
                .validateMove(new MoveDescription(units, route), germans)
                .isMoveValid())
        .isFalse();
    MockDelegateBridge.advanceToStep(bridge, "germansCombatMove");
    assertThat(
            new MoveValidator(data, false)
                .validateMove(new MoveDescription(units, route), germans)
                .isMoveValid())
        .isFalse();
  }

  @Test
  @DisplayName("A transport aircraft airlifts two infantry in noncombat without researching")
  void airTransportMovement() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var route =
        new Route(
            start,
            data.getMap().getTerritoryOrNull("Western Germany"),
            data.getMap().getTerritoryOrNull("Greater Southern Germany"));
    final var plane = data.getUnitTypeList().getUnitTypeOrThrow("transport_plane").create(germans);
    final var infantry = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(2, germans);
    final var units = new ArrayList<Unit>(infantry);
    units.add(plane);
    start.getUnitCollection().addAll(units);
    final var move = new MoveDescription(units, route, Map.of(), Map.of(plane, infantry));
    assertThat(new MoveValidator(data, true).validateMove(move, germans).isMoveValid()).isTrue();
  }

  @Test
  @DisplayName("Cargo aircraft carry one tank, while transport aircraft reject non-infantry")
  void cargoAircraft() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var route =
        new Route(
            start,
            data.getMap().getTerritoryOrNull("Western Germany"),
            data.getMap().getTerritoryOrNull("Greater Southern Germany"));
    final var plane = data.getUnitTypeList().getUnitTypeOrThrow("cargo_plane").create(germans);
    final var tanks = data.getUnitTypeList().getUnitTypeOrThrow("armour").create(2, germans);
    final var units = new ArrayList<Unit>(tanks);
    units.add(plane);
    start.getUnitCollection().addAll(units);
    final var oneTank =
        new MoveDescription(
            List.of(plane, tanks.get(0)), route, Map.of(), Map.of(plane, List.of(tanks.get(0))));
    assertThat(new MoveValidator(data, true).validateMove(oneTank, germans).isMoveValid()).isTrue();
    final var twoTanks = new MoveDescription(units, route, Map.of(), Map.of(plane, tanks));
    assertThat(new MoveValidator(data, true).validateMove(twoTanks, germans).isMoveValid())
        .isFalse();
    final var transport =
        data.getUnitTypeList().getUnitTypeOrThrow("transport_plane").create(germans);
    start.getUnitCollection().add(transport);
    final var invalidCargo =
        new MoveDescription(
            List.of(transport, tanks.get(0)),
            route,
            Map.of(),
            Map.of(transport, List.of(tanks.get(0))));
    assertThat(new MoveValidator(data, true).validateMove(invalidCargo, germans).isMoveValid())
        .isFalse();
  }

  @Test
  @DisplayName("A mixed airlift assigns a tank to cargo and two infantry to a transport aircraft")
  void mixedAirlift() {
    MockDelegateBridge.advanceToStep(
        MockDelegateBridge.newDelegateBridge(germans), "germansNonCombatMove");
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var route =
        new Route(
            start,
            data.getMap().getTerritoryOrNull("Western Germany"),
            data.getMap().getTerritoryOrNull("Greater Southern Germany"));
    final var cargo = data.getUnitTypeList().getUnitTypeOrThrow("cargo_plane").create(germans);
    final var transport =
        data.getUnitTypeList().getUnitTypeOrThrow("transport_plane").create(germans);
    final var tank = data.getUnitTypeList().getUnitTypeOrThrow("armour").create(germans);
    final var infantry = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(2, germans);
    final var units = new ArrayList<Unit>(infantry);
    units.addAll(List.of(cargo, transport, tank));
    start.getUnitCollection().addAll(units);
    assertThat(
            new MoveValidator(data, true)
                .validateMove(
                    new MoveDescription(
                        units, route, Map.of(), Map.of(cargo, List.of(tank), transport, infantry)),
                    germans)
                .isMoveValid())
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"heavy_bomber", "super_battleship"})
  @DisplayName("Heavy bombers and super battleships roll two independent combat dice")
  void upgradedAttackDice(final String type) {
    final var units = data.getUnitTypeList().getUnitTypeOrThrow(type).create(1, germans);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            invocation -> {
              assertThat((int) invocation.getArgument(0)).isEqualTo(10);
              assertThat((int) invocation.getArgument(1)).isEqualTo(2);
              return new int[] {6, 6};
            });
    final var calculator =
        CombatValueBuilder.mainCombatValue()
            .friendlyUnits(units)
            .enemyUnits(List.of())
            .side(BattleState.Side.OFFENSE)
            .gameSequence(data.getSequence())
            .gameDiceSides(data.getDiceSides())
            .supportAttachments(data.getUnitTypeList().getSupportRules())
            .territoryEffects(List.of())
            .lhtrHeavyBombers(false)
            .build();
    assertThat(
            RollDiceFactory.rollBattleDice(units, germans, bridge, "upgrade", calculator).getHits())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Defensive infantry support never stacks and supports at most two per giver")
  void supportLimits() {
    final var infantry = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(5, germans);
    final var units = new ArrayList<Unit>(infantry);
    units.add(data.getUnitTypeList().getUnitTypeOrThrow("artillery").create(germans));
    units.add(data.getUnitTypeList().getUnitTypeOrThrow("armour").create(germans));
    final var calculator =
        CombatValueBuilder.mainCombatValue()
            .friendlyUnits(units)
            .enemyUnits(List.of())
            .side(BattleState.Side.DEFENSE)
            .gameSequence(data.getSequence())
            .gameDiceSides(data.getDiceSides())
            .supportAttachments(data.getUnitTypeList().getSupportRules())
            .territoryEffects(List.of())
            .lhtrHeavyBombers(false)
            .build();
    final var power = PowerStrengthAndRolls.build(units, calculator);
    assertThat(infantry.stream().mapToInt(power::getStrength).sorted().toArray())
        .containsExactly(2, 3, 3, 3, 3);
  }

  @ParameterizedTest
  @CsvSource({"infantry,2", "battleship,7", "bomber,7"})
  @DisplayName("Combat rolls use ten sides and the exact hit/miss boundary")
  void combatBoundaries(final String type, final int strength) {
    final var units = data.getUnitTypeList().getUnitTypeOrThrow(type).create(1, germans);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            invocation -> {
              assertThat((int) invocation.getArgument(0)).isEqualTo(10);
              return new int[] {strength - 1};
            })
        .thenReturn(new int[] {strength});
    final var calculator =
        CombatValueBuilder.mainCombatValue()
            .friendlyUnits(units)
            .enemyUnits(List.of())
            .side(BattleState.Side.OFFENSE)
            .gameSequence(data.getSequence())
            .gameDiceSides(data.getDiceSides())
            .supportAttachments(data.getUnitTypeList().getSupportRules())
            .territoryEffects(List.of())
            .lhtrHeavyBombers(false)
            .build();
    assertThat(
            RollDiceFactory.rollBattleDice(units, germans, bridge, "boundary", calculator)
                .getHits())
        .isEqualTo(1);
    assertThat(
            RollDiceFactory.rollBattleDice(units, germans, bridge, "boundary", calculator)
                .getHits())
        .isZero();
  }

  @Test
  @DisplayName("Paris is not a liberation token at setup and a token remains after territory loss")
  void permanentVictoryTokens() {
    final var british = data.getPlayerList().getPlayerId("British");
    final var bridge = MockDelegateBridge.newDelegateBridge(british);
    final var paris = RulesAttachment.get(british, "conditionAttachment_ECR_Allies_Europe_Goal");
    assertThat(
            AbstractConditionsAttachment.testAllConditionsRecursive(Set.of(paris), null, bridge)
                .get(paris))
        .isFalse();
    data.getMap().getTerritoryOrNull("Southern Italy").setOwner(british);
    final var award =
        (TriggerAttachment) british.getAttachment("triggerAttachment_ECR_Allies_Rome");
    TriggerAttachment.collectAndFireTriggers(
        Set.copyOf(data.getPlayerList().getPlayers()),
        trigger -> trigger.equals(award) && TriggerAttachment.availableUses.test(trigger),
        bridge,
        "after",
        "britishBattle");
    final var earned = RulesAttachment.get(british, "conditionAttachment_ECR_Allies_Rome_Earned");
    assertThat(earned.isSatisfied(new HashMap<>(), bridge)).isTrue();
    data.getMap()
        .getTerritoryOrNull("Southern Italy")
        .setOwner(data.getPlayerList().getPlayerId("Italians"));
    assertThat(earned.isSatisfied(new HashMap<>(), bridge)).isTrue();
  }

  @Test
  @DisplayName("London convoy token uses original UK land losses and is awarded once")
  void londonConvoyToken() throws Exception {
    final var british = data.getPlayerList().getPlayerId("British");
    final var bridge = MockDelegateBridge.newDelegateBridge(british);
    final var uk = data.getMap().getTerritoryOrNull("United Kingdom");
    final var canada = data.getMap().getTerritoryOrNull("Ontario");
    final var acquired = data.getMap().getTerritoryOrNull("France");
    TerritoryAttachment.get(uk)
        .orElseThrow()
        .getPropertyOrEmpty("originalOwner")
        .orElseThrow()
        .setValue(british);
    TerritoryAttachment.get(canada)
        .orElseThrow()
        .getPropertyOrEmpty("originalOwner")
        .orElseThrow()
        .setValue(british);
    TerritoryAttachment.get(acquired)
        .orElseThrow()
        .getPropertyOrEmpty("originalOwner")
        .orElseThrow()
        .setValue(data.getPlayerList().getPlayerId("French"));
    final var losses = new IntegerMap<Territory>();
    losses.put(uk, 6);
    losses.put(canada, 5);
    losses.put(acquired, 6);
    final var earned = RulesAttachment.get(germans, "conditionAttachment_ECR_Axis_London_Earned");
    ModEcrRulesAttachment.recordConvoyLoss(british, losses, bridge);
    assertThat(earned.isSatisfied(new HashMap<>(), bridge)).isFalse();
    losses.put(canada, 6);
    ModEcrRulesAttachment.recordConvoyLoss(british, losses, bridge);
    assertThat(earned.isSatisfied(new HashMap<>(), bridge)).isTrue();
    ModEcrRulesAttachment.recordConvoyLoss(british, losses, bridge);
    assertThat(earned.isSatisfied(new HashMap<>(), bridge)).isTrue();
  }

  @Test
  @DisplayName("New units, aggregate conditions and permanent victory tokens survive serialization")
  void saveReload() throws Exception {
    final var earned = RulesAttachment.get(germans, "conditionAttachment_ECR_Axis_London_Earned");
    earned.getPropertyOrEmpty("switch").orElseThrow().setValue("true");
    final GameData copy;
    try (var ignored = data.acquireWriteLock()) {
      copy =
          GameDataUtils.cloneGameData(data, GameDataManager.Options.builder().build())
              .orElseThrow();
    }
    assertThat(copy.getDiceSides()).isEqualTo(10);
    assertThat(copy.getUnitTypeList().getUnitTypeOrThrow("cargo_plane")).isNotNull();
    final var copiedGermans = copy.getPlayerList().getPlayerId("Germans");
    assertThat(copiedGermans.getAttachment("objectiveAttachment_ECR_German_Submarines"))
        .isInstanceOf(ModEcrRulesAttachment.class);
    final var copiedEarned = RulesAttachment.get(copiedGermans, earned.getName());
    assertThat(
            copiedEarned.isSatisfied(
                new HashMap<>(), MockDelegateBridge.newDelegateBridge(copiedGermans)))
        .isTrue();
  }

  @Test
  @DisplayName("The engine initializes the new map with original owners and production rules")
  void initialization() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final var initializer =
        data.getDelegates().stream()
            .filter(InitializationDelegate.class::isInstance)
            .map(InitializationDelegate.class::cast)
            .findFirst()
            .orElseThrow();
    initializer.setDelegateBridgeAndPlayer(bridge);
    initializer.start();
    initializer.end();
    assertThat(
            TerritoryAttachment.get(data.getMap().getTerritoryOrNull("United Kingdom"))
                .orElseThrow()
                .getOriginalOwner())
        .contains(data.getPlayerList().getPlayerId("British"));
  }

  @ParameterizedTest
  @CsvSource({"0,0,Allies", "3,3,Allies", "4,3,Axis", "3,4,Allies", "10,9,Axis"})
  @DisplayName("Round-eight scoring selects one winner and awards tied games to the Allies")
  void finalScoring(final int axisScore, final int alliedScore, final String winner)
      throws Exception {
    final var endRound =
        data.getSequence().getSteps().stream()
            .filter(step -> step.getName().equals("endRoundStep"))
            .findFirst()
            .orElseThrow();
    data.getSequence().setRoundAndStep(8, endRound.getDisplayName(), null);
    for (final var side : List.of("Axis", "Allies")) {
      final var owner =
          data.getPlayerList().getPlayerId(side.equals("Axis") ? "Germans" : "British");
      final var awards =
          owner.getAttachments().values().stream()
              .filter(
                  a ->
                      a.getName().startsWith("conditionAttachment_ECR_" + side + "_")
                          && a.getName().endsWith("_Earned"))
              .toList();
      final int score = side.equals("Axis") ? axisScore : alliedScore;
      for (int i = 0; i < score; i++) {
        awards.get(i).getPropertyOrEmpty("switch").orElseThrow().setValue("true");
      }
    }
    final var french = data.getPlayerList().getPlayerId("French");
    final var finals =
        french.getAttachments().values().stream()
            .filter(a -> a.getName().startsWith("triggerAttachment_ECR_Final_"))
            .map(TriggerAttachment.class::cast)
            .collect(java.util.stream.Collectors.toSet());
    final var tested =
        AbstractConditionsAttachment.testAllConditionsRecursive(
            Set.copyOf(finals), null, MockDelegateBridge.newDelegateBridge(french));
    assertThat(finals.stream().filter(trigger -> trigger.isSatisfied(tested)).toList())
        .singleElement()
        .satisfies(
            trigger ->
                assertThat(trigger.getPropertyOrEmpty("victory").orElseThrow().getValue())
                    .isEqualTo("ECR_" + winner + "_Victory"));
  }

  @Test
  @DisplayName(
      "Truck movement consumes infantry movement and undo restores both units and movement")
  void truckMoveAndUndo() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    final var delegate =
        data.getDelegates().stream()
            .filter(MoveDelegate.class::isInstance)
            .map(MoveDelegate.class::cast)
            .findFirst()
            .orElseThrow();
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var end = data.getMap().getTerritoryOrNull("Greater Southern Germany");
    final var route = new Route(start, data.getMap().getTerritoryOrNull("Western Germany"), end);
    final var units = new ArrayList<Unit>();
    units.addAll(data.getUnitTypeList().getUnitTypeOrThrow("truck").create(1, germans));
    units.addAll(data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(3, germans));
    start.getUnitCollection().addAll(units);
    assertThat(delegate.move(units, route)).isEmpty();
    assertThat(end.getUnits()).containsAll(units);
    assertThat(units)
        .allSatisfy(
            unit -> assertThat(unit.getMovementLeft()).isLessThanOrEqualTo(BigDecimal.ZERO));
    assertThat(delegate.undoMove(0)).isNull();
    assertThat(start.getUnits()).containsAll(units);
    assertThat(units)
        .allSatisfy(
            unit -> assertThat(unit.getAlreadyMoved()).isEqualByComparingTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("Mech infantry accompanies one fresh artillery two spaces only in noncombat")
  void artilleryNoncombatPairing() {
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    final var start = data.getMap().getTerritoryOrNull("Germany");
    final var route =
        new Route(
            start,
            data.getMap().getTerritoryOrNull("Western Germany"),
            data.getMap().getTerritoryOrNull("Greater Southern Germany"));
    final var mech = data.getUnitTypeList().getUnitTypeOrThrow("mech_infantry").create(germans);
    final var artillery = data.getUnitTypeList().getUnitTypeOrThrow("artillery").create(2, germans);
    start.getUnitCollection().addAll(artillery);
    start.getUnitCollection().add(mech);
    final var pair = new MoveDescription(List.of(mech, artillery.get(0)), route);
    assertThat(new MoveValidator(data, true).validateMove(pair, germans).isMoveValid()).isTrue();
    assertThat(
            new MoveValidator(data, true)
                .validateMove(
                    new MoveDescription(List.of(mech, artillery.get(0), artillery.get(1)), route),
                    germans)
                .isMoveValid())
        .isFalse();
    final var truck = data.getUnitTypeList().getUnitTypeOrThrow("truck").create(germans);
    start.getUnitCollection().add(truck);
    assertThat(
            new MoveValidator(data, true)
                .validateMove(new MoveDescription(List.of(truck, artillery.get(0)), route), germans)
                .isMoveValid())
        .isFalse();
    MockDelegateBridge.advanceToStep(bridge, "germansCombatMove");
    assertThat(games.strategy.triplea.util.TransportUtils.canCarry(mech, artillery.get(0)))
        .isFalse();
    MockDelegateBridge.advanceToStep(bridge, "germansNonCombatMove");
    artillery.get(0).setAlreadyMoved(BigDecimal.ONE);
    assertThat(new MoveValidator(data, true).validateMove(pair, germans).isMoveValid()).isFalse();
  }

  private UnitAttachment attachment(final String unit) {
    return data.getUnitTypeList().getUnitTypeOrThrow(unit).getUnitAttachment();
  }
}
