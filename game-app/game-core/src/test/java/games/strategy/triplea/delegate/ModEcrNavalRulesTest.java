package games.strategy.triplea.delegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.triplea.Constants;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.MustFightBattle;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.triplea.java.collections.IntegerMap;

class ModEcrNavalRulesTest extends games.strategy.triplea.settings.AbstractClientSettingTestCase {
  private GameData data;
  private GamePlayer germans;
  private GamePlayer british;
  private IDelegateBridge bridge;

  @BeforeEach
  void loadMap() {
    final var root = Path.of("../../custom_maps/global_1940_mod_ecr");
    final var relative = Path.of("map/games/global_1940_mod_ecr.xml");
    data =
        GameParser.parse(
                Files.exists(root.resolve(relative))
                    ? root.resolve(relative)
                    : root.resolve("global_1940_mod_ecr").resolve(relative),
                false)
            .orElseThrow();
    germans = data.getPlayerList().getPlayerId("Germans");
    british = data.getPlayerList().getPlayerId("British");
    bridge = MockDelegateBridge.newDelegateBridge(germans);
    for (final var territory : data.getMap().getTerritories()) {
      if (territory.isWater() || territory.getName().equals("Germany")) {
        territory.getUnitCollection().removeAll(new ArrayList<>(territory.getUnits()));
      }
    }
    selectDefaultCasualties(bridge);
  }

  private static void selectDefaultCasualties(final IDelegateBridge bridge) {
    when(bridge
            .getRemotePlayer()
            .selectCasualties(
                anyCollection(),
                any(),
                anyInt(),
                anyString(),
                any(),
                any(),
                anyCollection(),
                anyCollection(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean()))
        .thenAnswer(call -> new CasualtyDetails(call.getArgument(10), true));
  }

  private Unit unit(final String type, final GamePlayer owner) {
    return data.getUnitTypeList().getUnitTypeOrThrow(type).create(owner);
  }

  private Territory zone(final int number) {
    return data.getMap().getTerritoryOrNull(number + " Sea Zone");
  }

  private Unit place(final String type, final GamePlayer owner, final int number) {
    final var unit = unit(type, owner);
    zone(number).getUnitCollection().add(unit);
    return unit;
  }

  @ParameterizedTest
  @CsvSource({
    "battleship,1",
    "carrier,1",
    "super_battleship,1",
    "super_carrier,1",
    "cruiser,0",
    "destroyer,0"
  })
  void bountyTypesAndOnceOnlyPayment(final String type, final int reward) {
    final var ship = unit(type, british);
    final int before = germans.getResources().getQuantity(Constants.PUS);
    ModEcrBountyRules.record(bridge, List.of(ship), List.of(germans));
    ModEcrBountyRules.record(bridge, List.of(ship), List.of(germans));
    assertThat(germans.getResources().getQuantity(Constants.PUS)).isEqualTo(before);
    ModEcrBountyRules.pay(bridge);
    ModEcrBountyRules.pay(bridge);
    assertThat(germans.getResources().getQuantity(Constants.PUS)).isEqualTo(before + reward);
  }

  @Test
  void mixedDefendersUseFirstParticipantInTurnOrder() {
    final var americans = data.getPlayerList().getPlayerId("Americans");
    data.getRelationshipTracker()
        .setRelationship(
            americans, germans, data.getRelationshipTypeList().getDefaultWarRelationship());
    final int americanBefore = americans.getResources().getQuantity(Constants.PUS);
    final int britishBefore = british.getResources().getQuantity(Constants.PUS);
    ModEcrBountyRules.record(
        bridge, List.of(unit("battleship", germans)), List.of(british, americans));
    ModEcrBountyRules.pay(bridge);
    assertThat(americans.getResources().getQuantity(Constants.PUS)).isEqualTo(americanBefore + 1);
    assertThat(british.getResources().getQuantity(Constants.PUS)).isEqualTo(britishBefore);
  }

  @Test
  void bountySurvivesSaveAndDoesNotPayTwice() {
    final int before = germans.getResources().getQuantity(Constants.PUS);
    ModEcrBountyRules.record(bridge, List.of(unit("carrier", british)), List.of(germans));
    final var copy =
        GameDataUtils.cloneGameData(data, GameDataManager.Options.builder().build()).orElseThrow();
    final var copiedPlayer = copy.getPlayerList().getPlayerId("Germans");
    final var copiedBridge = MockDelegateBridge.newDelegateBridge(copiedPlayer);
    ModEcrBountyRules.pay(copiedBridge);
    ModEcrBountyRules.pay(copiedBridge);
    assertThat(copiedPlayer.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void battleCasualtyRemovalRecordsBountyButDamageDoesNot() {
    final var attacker = unit("destroyer", germans);
    final var carrier = place("carrier", british, 114);
    final var battle =
        new MustFightBattle(zone(114), germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(carrier), List.of(attacker), List.of(), british, List.of());
    final int before = germans.getResources().getQuantity(Constants.PUS);
    bridge.addChange(ChangeFactory.unitsHit(IntegerMap.of(Map.of(carrier, 1)), List.of(zone(114))));
    assertThat(ModEcrBountyRules.pay(bridge)).isEmpty();
    battle.removeUnits(List.of(carrier), bridge, zone(114), BattleState.Side.DEFENSE);
    ModEcrBountyRules.pay(bridge);
    assertThat(germans.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void nonParticipantDoesNotReceiveBounty() {
    ModEcrBountyRules.record(bridge, List.of(unit("carrier", british)), List.of());
    assertThat(ModEcrBountyRules.pay(bridge)).isEmpty();
  }

  @Test
  void purchaseLaysOneMinePerSideAndZoneForTwoIpc() {
    place("destroyer", germans, 113);
    place("destroyer", germans, 113);
    place("destroyer", germans, 114);
    final int before = germans.getResources().getQuantity(Constants.PUS);
    when(bridge.getRemotePlayer().selectUnitsQuery(any(), anyCollection(), anyString()))
        .thenAnswer(call -> List.copyOf(call.getArgument(1)));
    ModEcrMineRules.offerPurchase(bridge, germans);
    ModEcrMineRules.offerPurchase(bridge, germans);
    assertThat(zone(113).getUnits().stream().filter(ModEcrMineRules::isMine)).hasSize(1);
    assertThat(zone(114).getUnits().stream().filter(ModEcrMineRules::isMine)).hasSize(1);
    assertThat(germans.getResources().getQuantity(Constants.PUS)).isEqualTo(before - 4);
    assertThat(ModEcrMineRules.allowance(bridge, germans)).isEqualTo(1);
  }

  @Test
  void alliedMinePreventsPlacementButEnemyMineDoesNot() {
    final var italians = data.getPlayerList().getPlayerId("Italians");
    place("destroyer", germans, 113);
    place("destroyer", germans, 114);
    place("naval_mine", italians, 113);
    place("naval_mine", british, 114);
    when(bridge.getRemotePlayer().selectUnitsQuery(any(), anyCollection(), anyString()))
        .thenAnswer(call -> List.copyOf(call.getArgument(1)));
    ModEcrMineRules.offerPurchase(bridge, germans);
    assertThat(zone(113).getUnits().stream().filter(ModEcrMineRules::isMine)).hasSize(1);
    assertThat(zone(114).getUnits().stream().filter(ModEcrMineRules::isMine)).hasSize(2);
  }

  @Test
  void destroyerLossKeepsExistingMinesAndBlocksNewPlacement() {
    place("destroyer", germans, 113);
    final var mine1 = place("naval_mine", germans, 114);
    final var mine2 = place("naval_mine", germans, 115);
    ModEcrMineRules.offerPurchase(bridge, germans);
    assertThat(ModEcrMineRules.allowance(bridge, germans)).isZero();
    assertThat(zone(114).getUnits()).contains(mine1);
    assertThat(zone(115).getUnits()).contains(mine2);
    org.mockito.Mockito.verify(bridge.getRemotePlayer(), org.mockito.Mockito.never())
        .selectUnitsQuery(any(), anyCollection(), anyString());
  }

  @Test
  void minePurchaseRequiresMoneyAndDestroyer() {
    place("destroyer", germans, 113);
    bridge.addChange(
        ChangeFactory.changeResourcesChange(
            germans,
            data.getResourceList().getResourceOrThrow(Constants.PUS),
            1 - germans.getResources().getQuantity(Constants.PUS)));
    ModEcrMineRules.offerPurchase(bridge, germans);
    assertThat(zone(113).getUnits().stream().filter(ModEcrMineRules::isMine)).isEmpty();
    org.mockito.Mockito.verify(bridge.getRemotePlayer(), org.mockito.Mockito.never())
        .selectUnitsQuery(any(), anyCollection(), anyString());
  }

  @ParameterizedTest
  @CsvSource({"0,true", "1,true", "2,false", "9,false"})
  void mineHitBoundariesAndRemoval(final int roll, final boolean hit) {
    final var ship = place("destroyer", germans, 113);
    final var mine = place("naval_mine", british, 114);
    final var route = new Route(zone(113), zone(114));
    final var move = new UndoableMove(List.of(ship), route);
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {roll});
    final var resolver = new ModEcrMineMove(List.of(ship), route, germans, move);
    resolver.execute(bridge);
    assertThat(resolver.getSurvivors().contains(ship)).isEqualTo(!hit);
    assertThat(zone(114).getUnits().contains(mine)).isEqualTo(!hit);
    assertThat(move.getCanUndo()).isFalse();
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(1));
  }

  @Test
  void oneRollForWholeFleetAndMovementPhase() {
    final var ships = data.getUnitTypeList().getUnitTypeOrThrow("destroyer").create(3, germans);
    zone(113).getUnitCollection().addAll(ships);
    place("naval_mine", british, 114);
    final var route = new Route(zone(113), zone(114));
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {9});
    for (final var ship : ships) {
      new ModEcrMineMove(List.of(ship), route, germans, new UndoableMove(List.of(ship), route))
          .execute(bridge);
    }
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(1));
    ModEcrMineRules.finishMovement(bridge, germans);
    new ModEcrMineMove(ships, route, germans, new UndoableMove(ships, route)).execute(bridge);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(2));
  }

  @Test
  void airOnlyMovementDoesNotTriggerMine() {
    final var fighter = place("fighter", germans, 113);
    place("naval_mine", british, 114);
    final var route = new Route(zone(113), zone(114));
    new ModEcrMineMove(List.of(fighter), route, germans, new UndoableMove(List.of(fighter), route))
        .execute(bridge);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.never());
  }

  @Test
  void friendlyMineDoesNotTrigger() {
    final var ship = place("destroyer", germans, 113);
    place("naval_mine", germans, 114);
    final var route = new Route(zone(113), zone(114));
    new ModEcrMineMove(List.of(ship), route, germans, new UndoableMove(List.of(ship), route))
        .execute(bridge);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.never());
  }

  @Test
  void sinkingTransportRemovesCargo() {
    final var ship = place("transport", germans, 113);
    final var infantry = place("infantry", germans, 113);
    bridge.addChange(
        ChangeFactory.unitPropertyChange(infantry, ship, Unit.PropertyName.TRANSPORTED_BY));
    place("naval_mine", british, 114);
    final var units = List.of(ship, infantry);
    final var route = new Route(zone(113), zone(114));
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    final var resolver = new ModEcrMineMove(units, route, germans, new UndoableMove(units, route));
    resolver.execute(bridge);
    assertThat(resolver.getSurvivors()).isEmpty();
    assertThat(zone(113).getUnits()).doesNotContain(ship, infantry);
  }

  @ParameterizedTest
  @CsvSource({"0,true,0", "1,false,1"})
  void capitalMineHitDamagesOrSinksAndPaysOwner(
      final int existingHits, final boolean survives, final int bounty) {
    final var ship = place("battleship", germans, 113);
    ship.setHits(existingHits);
    place("naval_mine", british, 114);
    final int before = british.getResources().getQuantity(Constants.PUS);
    final var route = new Route(zone(113), zone(114));
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    final var resolver =
        new ModEcrMineMove(List.of(ship), route, germans, new UndoableMove(List.of(ship), route));
    resolver.execute(bridge);
    assertThat(resolver.getSurvivors().contains(ship)).isEqualTo(survives);
    if (survives) {
      assertThat(ship.getHits()).isEqualTo(1);
    }
    ModEcrBountyRules.pay(bridge);
    assertThat(british.getResources().getQuantity(Constants.PUS)).isEqualTo(before + bounty);
  }

  @ParameterizedTest
  @CsvSource({"germansCombatMove", "germansNonCombatMove"})
  void actualMovementThroughMineAndUndoBoundary(final String phase) {
    final var ship = place("destroyer", germans, 113);
    final var mine = place("naval_mine", british, 114);
    MockDelegateBridge.advanceToStep(bridge, phase);
    final var delegate = (MoveDelegate) data.getMoveDelegate();
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {9});
    assertThat(delegate.move(List.of(ship), new Route(zone(113), zone(114), zone(115)))).isEmpty();
    assertThat(zone(115).getUnits()).contains(ship);
    assertThat(zone(114).getUnits()).contains(mine);
    assertThat(data.getBattleDelegate().getBattleTracker().getPendingBattles(zone(114))).isEmpty();
    assertThat(delegate.undoMove(0)).contains("naval mine");
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(1));
  }

  @Test
  void mineHitInTransitRemovesShipBeforeArrival() {
    final var ship = place("destroyer", germans, 113);
    place("naval_mine", british, 114);
    MockDelegateBridge.advanceToStep(bridge, "germansCombatMove");
    final var delegate = (MoveDelegate) data.getMoveDelegate();
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    assertThat(delegate.move(List.of(ship), new Route(zone(113), zone(114), zone(115)))).isEmpty();
    assertThat(zone(113).getUnits()).doesNotContain(ship);
    assertThat(zone(114).getUnits()).isEmpty();
    assertThat(zone(115).getUnits()).doesNotContain(ship);
  }

  @Test
  void passiveTransportOwnerDoesNotQualifyForBounty() {
    final var russians = data.getPlayerList().getPlayerId("Russians");
    final var attacker = place("battleship", germans, 114);
    final var destroyer = place("destroyer", british, 114);
    final var transport = place("transport", russians, 114);
    final var battle =
        new MustFightBattle(zone(114), germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(
        List.of(destroyer, transport), List.of(attacker), List.of(), british, List.of());
    final int before = british.getResources().getQuantity(Constants.PUS);
    battle.removeUnits(List.of(attacker), bridge, zone(114), BattleState.Side.OFFENSE);
    ModEcrBountyRules.pay(bridge);
    assertThat(british.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void payoutAtActiveTurnEndIncludesDefendersWithoutIncome() {
    final int before = british.getResources().getQuantity(Constants.PUS);
    ModEcrBountyRules.record(bridge, List.of(unit("carrier", germans)), List.of(british));
    data.getMap().getTerritoryOrNull("Germany").setOwner(british);
    MockDelegateBridge.advanceToStep(bridge, "germansEndTurn");
    final var delegate = (EndTurnDelegate) data.getDelegate("endTurn");
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    delegate.start();
    assertThat(british.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void kamikazeCapitalShipSinkEarnsBounty() throws Exception {
    final var japanese = data.getPlayerList().getPlayerId("Japanese");
    data.getRelationshipTracker()
        .setRelationship(
            japanese, british, data.getRelationshipTypeList().getDefaultWarRelationship());
    final var carrier = place("carrier", british, 114);
    carrier.setHits(1);
    final var token = data.getResourceList().getResourceOrThrow("SuicideAttackTokens");
    final var battle = data.getBattleDelegate();
    battle.setDelegateBridgeAndPlayer(bridge);
    final var method =
        battle
            .getClass()
            .getDeclaredMethod(
                "fireKamikazeSuicideAttacks",
                Unit.class,
                IntegerMap.class,
                IntegerMap.class,
                GamePlayer.class,
                Territory.class);
    method.setAccessible(true);
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    final int before = japanese.getResources().getQuantity(Constants.PUS);
    method.invoke(
        battle,
        carrier,
        IntegerMap.of(Map.of(token, 1)),
        IntegerMap.of(Map.of(token, 3)),
        japanese,
        zone(114));
    assertThat(zone(114).getUnits()).doesNotContain(carrier);
    ModEcrBountyRules.pay(bridge);
    assertThat(japanese.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void noMoreMineRollsAfterAllShipsSink() {
    final var ship = place("destroyer", germans, 113);
    place("naval_mine", british, 114);
    final var secondMine = place("naval_mine", british, 115);
    final var route = new Route(zone(113), zone(114), zone(115));
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    final var resolver =
        new ModEcrMineMove(List.of(ship), route, germans, new UndoableMove(List.of(ship), route));
    resolver.execute(bridge);
    assertThat(resolver.getSurvivors()).isEmpty();
    assertThat(zone(115).getUnits()).contains(secondMine);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(1));
  }

  @Test
  void seaCargoOwnersDoNotQualifyForBounty() {
    final var russians = data.getPlayerList().getPlayerId("Russians");
    final var attacker = place("battleship", germans, 114);
    final var destroyer = place("destroyer", british, 114);
    final var infantry = place("infantry", russians, 114);
    final var battle =
        new MustFightBattle(zone(114), germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(destroyer, infantry), List.of(attacker), List.of(), british, List.of());
    final int before = british.getResources().getQuantity(Constants.PUS);
    battle.removeUnits(List.of(attacker), bridge, zone(114), BattleState.Side.OFFENSE);
    ModEcrBountyRules.pay(bridge);
    assertThat(british.getResources().getQuantity(Constants.PUS)).isEqualTo(before + 1);
  }

  @Test
  void mineDeclineIsRememberedAcrossSavedPurchasePhase() {
    place("destroyer", germans, 113);
    ModEcrMineRules.offerPurchase(bridge, germans);
    final var copied =
        GameDataUtils.cloneGameData(data, GameDataManager.Options.builder().build()).orElseThrow();
    final var copiedPlayer = copied.getPlayerList().getPlayerId("Germans");
    final var copiedBridge = MockDelegateBridge.newDelegateBridge(copiedPlayer);
    ModEcrMineRules.offerPurchase(copiedBridge, copiedPlayer);
    org.mockito.Mockito.verify(copiedBridge.getRemotePlayer(), org.mockito.Mockito.never())
        .selectUnitsQuery(any(), anyCollection(), anyString());
  }

  @Test
  void navalRulesDoNotAffectOtherMaps() {
    data.getProperties().set(Constants.MOD_ECR_RULES, false);
    final var ship = place("destroyer", germans, 113);
    final var mine = place("naval_mine", british, 114);
    final var route = new Route(zone(113), zone(114));
    final var move = new UndoableMove(List.of(ship), route);
    new ModEcrMineMove(List.of(ship), route, germans, move).execute(bridge);
    ModEcrMineRules.offerPurchase(bridge, germans);
    ModEcrBountyRules.record(bridge, List.of(unit("carrier", british)), List.of(germans));
    assertThat(ModEcrBountyRules.pay(bridge)).isEmpty();
    assertThat(zone(114).getUnits()).contains(mine);
    assertThat(move.getCanUndo()).isTrue();
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.never());
  }

  @Test
  void pausedMineCasualtySelectionSurvivesFullGameSave() throws Exception {
    final var ship = place("battleship", germans, 113);
    final var escort = place("destroyer", germans, 113);
    place("naval_mine", british, 114);
    MockDelegateBridge.advanceToStep(bridge, "germansCombatMove");
    final var delegate = (MoveDelegate) data.getMoveDelegate();
    delegate.setDelegateBridgeAndPlayer(bridge);
    delegate.start();
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {0});
    final var remote = bridge.getRemotePlayer();
    org.mockito.Mockito.doThrow(new IllegalStateException("pause"))
        .when(remote)
        .selectCasualties(
            anyCollection(),
            any(),
            anyInt(),
            anyString(),
            any(),
            any(),
            anyCollection(),
            anyCollection(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean());
    assertThatThrownBy(() -> delegate.move(List.of(ship, escort), new Route(zone(113), zone(114))))
        .hasMessage("pause");
    final var bytes = new ByteArrayOutputStream();
    GameDataManager.saveGame(bytes, data);
    final var restored =
        GameDataManager.loadGame(new ByteArrayInputStream(bytes.toByteArray())).orElseThrow();
    final var restoredPlayer = restored.getPlayerList().getPlayerId("Germans");
    final var resumedBridge = MockDelegateBridge.newDelegateBridge(restoredPlayer);
    selectDefaultCasualties(resumedBridge);
    final var resumedDelegate = (MoveDelegate) restored.getMoveDelegate();
    resumedDelegate.setDelegateBridgeAndPlayer(resumedBridge);
    resumedDelegate.start();
    assertThat(restored.getMap().getTerritoryOrNull("114 Sea Zone").getUnits())
        .hasSize(2)
        .anySatisfy(u -> assertThat(u.getHits()).isEqualTo(1));
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(
        resumedBridge, org.mockito.Mockito.never());
  }
}
