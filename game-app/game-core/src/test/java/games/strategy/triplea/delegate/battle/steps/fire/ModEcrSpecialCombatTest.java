package games.strategy.triplea.delegate.battle.steps.fire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.triplea.Constants;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.MockDelegateBridge;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.FakeBattleState;
import games.strategy.triplea.delegate.battle.MustFightBattle;
import games.strategy.triplea.delegate.battle.steps.change.ClearGeneralCasualties;
import games.strategy.triplea.delegate.battle.steps.change.RemoveUnprotectedUnits;
import games.strategy.triplea.delegate.battle.steps.fire.general.FiringGroupSplitterGeneral;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModEcrSpecialCombatTest
    extends games.strategy.triplea.settings.AbstractClientSettingTestCase {
  private GameData data;
  private GamePlayer germans;
  private GamePlayer british;

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
  }

  @ParameterizedTest
  @CsvSource({
    "anti_tank_gun,1,true",
    "anti_tank_gun,2,true",
    "mech_anti_tank_gun,1,true",
    "mech_anti_tank_gun,2,false"
  })
  @DisplayName(
      "Anti-tank priority applies every round; mechanized priority applies only in round one")
  void priorityCasualty(final String type, final int round, final boolean hasPriority) {
    final var firing = unit(type, germans);
    final var tank = unit("armour", british);
    final var infantry = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(2, british);
    final List<Unit> targets = new ArrayList<>(infantry);
    targets.add(tank);
    final var state = state(round, List.of(firing), targets);
    final var group = FiringGroup.groupBySuicideOnHit("Anti-tank", List.of(firing), targets).get(0);
    final var selector = mock(SelectMainBattleCasualties.Select.class);
    when(selector.apply(any(), any(), anyCollection(), anyInt()))
        .thenReturn(new CasualtyDetails(List.of(infantry.get(0)), List.of(), false));
    final var casualties =
        new SelectMainBattleCasualties(selector)
            .apply(MockDelegateBridge.newDelegateBridge(germans), selection(state, group, 1));
    assertThat(casualties.getKilled()).containsExactly(hasPriority ? tank : infantry.get(0));
  }

  @Test
  @DisplayName("Anti-tank hits overflow normally after all priority targets are killed")
  void priorityOverflow() {
    final var firing = unit("anti_tank_gun", germans);
    final var tank = unit("heavy_tank", british);
    final var infantry = data.getUnitTypeList().getUnitTypeOrThrow("infantry").create(3, british);
    final List<Unit> targets = new ArrayList<>(infantry);
    targets.add(tank);
    final var state = state(1, List.of(firing), targets);
    final var group = FiringGroup.groupBySuicideOnHit("Anti-tank", List.of(firing), targets).get(0);
    final var selector = mock(SelectMainBattleCasualties.Select.class);
    when(selector.apply(any(), any(), anyCollection(), anyInt()))
        .thenAnswer(
            call -> {
              assertThat((int) call.getArgument(3)).isEqualTo(1);
              assertThat((java.util.Collection<Unit>) call.getArgument(2)).doesNotContain(tank);
              return new CasualtyDetails(List.of(infantry.get(0)), List.of(), false);
            });
    final var casualties =
        new SelectMainBattleCasualties(selector)
            .apply(MockDelegateBridge.newDelegateBridge(germans), selection(state, group, 2));
    assertThat(casualties.getKilled()).containsExactlyInAnyOrder(tank, infantry.get(0));
  }

  @Test
  @DisplayName("Mixed armies roll anti-tank specialists separately without losing ordinary fire")
  void separateFiringGroups() {
    final var gun = unit("anti_tank_gun", germans);
    final var infantry = unit("infantry", germans);
    final var groups =
        FiringGroupSplitterGeneral.of(
                BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
            .apply(state(1, List.of(gun, infantry), List.of(unit("armour", british))));
    assertThat(groups).hasSize(2);
    assertThat(groups)
        .flatExtracting(FiringGroup::getFiringUnits)
        .containsExactlyInAnyOrder(gun, infantry);
    assertThat(
            groups.stream()
                .filter(g -> g.getDisplayName().equals("Anti-tank"))
                .findFirst()
                .orElseThrow()
                .getFiringUnits())
        .containsExactly(gun);
    data.getProperties().set(Constants.MOD_ECR_RULES, false);
    assertThat(
            FiringGroupSplitterGeneral.of(
                    BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
                .apply(state(1, List.of(gun, infantry), List.of(unit("armour", british)))))
        .hasSize(1);
  }

  @Test
  @DisplayName("Surviving attacking bombers withdraw after first-round casualty removal")
  void bombersWithdraw() {
    final var bomber = unit("bomber", germans);
    final var heavy = unit("heavy_bomber", germans);
    final var infantry = unit("infantry", germans);
    final var site = data.getMap().getTerritoryOrNull("France");
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(
        List.of(unit("infantry", british)),
        List.of(bomber, heavy, infantry),
        List.of(),
        british,
        List.of());
    final var actions = mock(BattleActions.class);
    new ClearGeneralCasualties(battle, actions)
        .execute(new ExecutionStack(), MockDelegateBridge.newDelegateBridge(germans));
    verify(actions)
        .clearWaitingToDieAndDamagedChangesInto(
            any(),
            org.mockito.ArgumentMatchers.eq(BattleState.Side.OFFENSE),
            org.mockito.ArgumentMatchers.eq(BattleState.Side.DEFENSE));
    assertThat(battle.filterUnits(BattleState.UnitBattleFilter.ALIVE, BattleState.Side.OFFENSE))
        .containsExactly(infantry);
    assertThat(battle.getRemainingAttackingUnits()).contains(bomber, heavy);
  }

  @ParameterizedTest
  @CsvSource({"6,1", "7,0"})
  @DisplayName("Bombers hit undefended transports on printed 7 and roll once even when heavy")
  void bomberTransportRolls(final int roll, final int hits) {
    final var bomber = unit("heavy_bomber", germans);
    final var transport = unit("transport", british);
    final var site = data.getMap().getTerritoryOrNull("110 Sea Zone");
    final var state =
        FakeBattleState.givenBattleStateBuilder(germans, british)
            .battleSite(site)
            .battleRound(1)
            .attackingUnits(List.of(bomber))
            .defendingUnits(List.of(transport))
            .build();
    final var group =
        FiringGroup.groupBySuicideOnHit("units", List.of(bomber), List.of(transport)).get(0);
    final var step =
        new RollDiceStep(
            state, BattleState.Side.OFFENSE, group, new FireRoundState(), new MainDiceRoller());
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              assertThat((int) call.getArgument(0)).isEqualTo(10);
              assertThat((int) call.getArgument(1)).isEqualTo(1);
              return new int[] {roll};
            });
    assertThat(new MainDiceRoller().apply(bridge, step).getHits()).isEqualTo(hits);
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    site.getUnitCollection().addAll(List.of(bomber, transport));
    final var actions = mock(BattleActions.class);
    new RemoveUnprotectedUnits(state, actions).execute(new ExecutionStack(), bridge);
    org.mockito.Mockito.verifyNoInteractions(actions);
  }

  private Unit unit(final String type, final GamePlayer owner) {
    return data.getUnitTypeList().getUnitTypeOrThrow(type).create(owner);
  }

  @ParameterizedTest
  @CsvSource({"6,0,ATTACKER", "7,1,DEFENDER"})
  @DisplayName(
      "A complete bomber-only battle ends after one roll and preserves transports on a miss")
  void completeBomberTransportBattle(
      final int roll, final int survivingTransports, final String winner) {
    final var site = data.getMap().getTerritoryOrNull("110 Sea Zone");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var bomber = unit("bomber", germans);
    final var transport = unit("transport", british);
    site.getUnitCollection().addAll(List.of(bomber, transport));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(transport), List.of(bomber), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              assertThat((int) call.getArgument(1)).isEqualTo(1);
              return new int[] {roll};
            });
    battle.fight(bridge);
    assertThat(battle.getWhoWon().name()).isEqualTo(winner);
    assertThat(
            site.getUnits().stream().filter(u -> u.getType().getName().equals("transport")).count())
        .isEqualTo(survivingTransports);
    assertThat(battle.getRemainingAttackingUnits()).contains(bomber);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(1));
  }

  private BattleState state(
      final int round, final List<Unit> attackers, final List<Unit> defenders) {
    return FakeBattleState.givenBattleStateBuilder(germans, british)
        .battleRound(round)
        .battleSite(data.getMap().getTerritoryOrNull("France"))
        .attackingUnits(attackers)
        .defendingUnits(defenders)
        .build();
  }

  @ParameterizedTest
  @CsvSource({
    "infantry,bomber,9,ATTACKER,false",
    "infantry,heavy_bomber,9,ATTACKER,false",
    "fighter,bomber,9,ATTACKER,true",
    "infantry,bomber,0,DEFENDER,true"
  })
  @DisplayName("Defending bombers withdraw after one round and are destroyed only on land capture")
  void defendingBomberCapture(
      final String attackingType,
      final String bomberType,
      final int defendingRoll,
      final String winner,
      final boolean survives) {
    final var site = data.getMap().getTerritoryOrNull("France");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var attacker = unit(attackingType, germans);
    final var bomber = unit(bomberType, british);
    site.getUnitCollection().addAll(List.of(attacker, bomber));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(bomber), List.of(attacker), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              final int[] rolls = new int[(int) call.getArgument(1)];
              java.util.Arrays.fill(rolls, call.getArgument(2).equals(germans) ? 9 : defendingRoll);
              return rolls;
            });
    battle.fight(bridge);
    assertThat(battle.getWhoWon().name()).isEqualTo(winner);
    assertThat(site.getUnits().contains(bomber)).isEqualTo(survives);
    assertThat(battle.getRemainingDefendingUnits().contains(bomber)).isEqualTo(survives);
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(
        bridge, org.mockito.Mockito.times(attackingType.equals("fighter") ? 3 : 2));
  }

  @Test
  @DisplayName("A withdrawn defending bomber neither fires nor absorbs casualties in round two")
  void defendingBomberLaterRound() {
    final var site = data.getMap().getTerritoryOrNull("France");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var attacker = unit("infantry", germans);
    final var defender = unit("infantry", british);
    final var bomber = unit("bomber", british);
    site.getUnitCollection().addAll(List.of(attacker, defender, bomber));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(defender, bomber), List.of(attacker), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final int[] expectedCounts = {1, 2, 1, 1};
    final var index = new java.util.concurrent.atomic.AtomicInteger();
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              final int position = index.getAndIncrement();
              assertThat((int) call.getArgument(1)).isEqualTo(expectedCounts[position]);
              final int[] rolls = new int[expectedCounts[position]];
              java.util.Arrays.fill(rolls, position == 2 ? 0 : 9);
              return rolls;
            });
    battle.fight(bridge);
    assertThat(battle.getWhoWon())
        .isEqualTo(games.strategy.triplea.delegate.battle.IBattle.WhoWon.ATTACKER);
    assertThat(battle.getBattleRound()).isEqualTo(2);
    assertThat(site.getUnits()).contains(attacker).doesNotContain(defender, bomber);
    assertThat(index.get()).isEqualTo(4);
  }

  @ParameterizedTest
  @CsvSource({"fighter,0,0", "fighter,1,4", "jet_fighter,2,0"})
  void simultaneousInterception(
      final String fighterType, final int roll, final int expectedNormalRolls) {
    final var site = data.getMap().getTerritoryOrNull("France");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var attacker = unit(fighterType, germans);
    final var defender = unit(fighterType, british);
    site.getUnitCollection().addAll(List.of(attacker, defender));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(defender), List.of(attacker), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final var count = new java.util.concurrent.atomic.AtomicInteger();
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              final int index = count.getAndIncrement();
              return new int[] {index < 2 ? roll : 0};
            });
    battle.fight(bridge);
    assertThat(site.getUnits()).doesNotContain(attacker, defender);
    assertThat(count.get()).isEqualTo(expectedNormalRolls == 0 ? 2 : expectedNormalRolls);
  }

  @Test
  void tacticalChoicesUseExistingDialog() {
    final var bomber = unit("tactical_bomber", germans);
    final var tank = unit("armour", british);
    final var state = state(1, List.of(bomber), List.of(tank));
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final var player = bridge.getRemotePlayer(germans);
    when(player.selectUnitsQuery(any(), anyCollection(), any())).thenReturn(List.of(bomber));
    final var choices =
        games.strategy.triplea.delegate.battle.ModEcrTacticalRules.chooseTargets(state, bridge);
    assertThat(choices).containsEntry(bomber, tank.getType());
  }

  @ParameterizedTest
  @CsvSource({"armour,false,true", "submarine,false,false", "submarine,true,true"})
  void tacticalCategoryRestrictions(
      final String targetType, final boolean destroyer, final boolean canFire) {
    final var bomber = unit("tactical_bomber", germans);
    final var target = unit(targetType, british);
    final List<Unit> attackers = new ArrayList<>(List.of(bomber));
    if (destroyer) {
      attackers.add(unit("destroyer", germans));
    }
    final var state =
        org.mockito.Mockito.spy(
            FakeBattleState.givenBattleStateBuilder(germans, british)
                .battleRound(2)
                .battleSite(
                    data.getMap()
                        .getTerritoryOrNull(
                            targetType.equals("submarine") ? "110 Sea Zone" : "France"))
                .attackingUnits(attackers)
                .defendingUnits(List.of(target))
                .build());
    org.mockito.Mockito.doReturn(java.util.Map.of(bomber, target.getType()))
        .when(state)
        .getModEcrTacticalTargets();
    final var groups =
        FiringGroupSplitterGeneral.of(
                BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
            .apply(state);
    assertThat(groups.stream().filter(g -> g.getFiringUnits().contains(bomber)).count())
        .isEqualTo(canFire ? 1 : 0);
  }

  @Test
  void targetedBombersUseFixedFourAndLoseExcessHits() {
    final var bombers =
        data.getUnitTypeList().getUnitTypeOrThrow("tactical_bomber").create(2, germans);
    final var tank = unit("armour", british);
    final var infantry = unit("infantry", british);
    final var state = org.mockito.Mockito.spy(state(1, bombers, List.of(tank, infantry)));
    org.mockito.Mockito.doReturn(
            java.util.Map.of(bombers.get(0), tank.getType(), bombers.get(1), tank.getType()))
        .when(state)
        .getModEcrTacticalTargets();
    final var group =
        FiringGroupSplitterGeneral.of(
                BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
            .apply(state)
            .get(0);
    assertThat(group.getTargetUnits()).containsExactly(tank);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge).thenReturn(new int[] {3, 4});
    final var fire = new FireRoundState();
    final var dice =
        new MainDiceRoller()
            .apply(
                bridge,
                new RollDiceStep(
                    state, BattleState.Side.OFFENSE, group, fire, new MainDiceRoller()));
    assertThat(dice.getHits()).isEqualTo(1);
    final var casualties =
        new SelectMainBattleCasualties().apply(bridge, selection(state, group, 2));
    assertThat(casualties.getKilled()).containsExactly(tank).doesNotContain(infantry);
    final var nextRound = org.mockito.Mockito.spy(state(2, bombers, List.of(infantry)));
    org.mockito.Mockito.doReturn(state.getModEcrTacticalTargets())
        .when(nextRound)
        .getModEcrTacticalTargets();
    assertThat(
            FiringGroupSplitterGeneral.of(
                    BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
                .apply(nextRound))
        .isEmpty();
  }

  @Test
  void transportTargetCannotBeSelectedWhileEscorted() {
    final var bomber = unit("tactical_bomber", germans);
    final var transport = unit("transport", british);
    final var destroyer = unit("destroyer", british);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final var player = bridge.getRemotePlayer(germans);
    final List<String> prompts = new ArrayList<>();
    when(player.selectUnitsQuery(any(), anyCollection(), any()))
        .thenAnswer(
            call -> {
              prompts.add(call.getArgument(2));
              return List.of(bomber);
            });
    final var choices =
        games.strategy.triplea.delegate.battle.ModEcrTacticalRules.chooseTargets(
            state(1, List.of(bomber), List.of(transport, destroyer)), bridge);
    assertThat(choices).containsEntry(bomber, destroyer.getType());
    assertThat(prompts).noneMatch(prompt -> prompt.contains("targeting transport"));
  }

  @Test
  void normalAaRemovesFightersBeforeInterception() {
    final var site = data.getMap().getTerritoryOrNull("France");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var fighter = unit("fighter", germans);
    final var infantry = unit("infantry", germans);
    final var aa = unit("aaGun", british);
    final var defender = unit("fighter", british);
    site.getUnitCollection().addAll(List.of(fighter, infantry, aa, defender));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(
        List.of(aa, defender), List.of(fighter, infantry), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    final List<String> annotations = new ArrayList<>();
    MockDelegateBridge.whenGetRandom(bridge)
        .thenAnswer(
            call -> {
              annotations.add(call.getArgument(4));
              final int[] rolls = new int[(int) call.getArgument(1)];
              java.util.Arrays.fill(
                  rolls, annotations.size() > 1 && call.getArgument(2).equals(germans) ? 9 : 0);
              return rolls;
            });
    battle.fight(bridge);
    assertThat(site.getUnits()).doesNotContain(fighter);
    assertThat(annotations).noneMatch(a -> a.startsWith("Fighter interception"));
  }

  @Test
  void interruptedInterceptionResumesWithoutRerolling() throws Exception {
    final var site = data.getMap().getTerritoryOrNull("France");
    site.getUnitCollection().removeAll(new ArrayList<>(site.getUnits()));
    final var attacker = unit("fighter", germans);
    final var defender = unit("fighter", british);
    site.getUnitCollection().addAll(List.of(attacker, defender));
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(defender), List.of(attacker), List.of(), british, List.of());
    battle.setHeadless(true);
    final var bridge = MockDelegateBridge.newDelegateBridge(germans);
    MockDelegateBridge.whenGetRandom(bridge)
        .thenReturn(new int[] {0})
        .thenThrow(new IllegalStateException("pause"));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> battle.fight(bridge))
        .hasMessage("pause");
    final var bytes = new java.io.ByteArrayOutputStream();
    try (final var out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(data);
      out.writeObject(battle);
    }
    final MustFightBattle restored;
    try (final var in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      ((GameData) in.readObject()).postDeSerialize();
      restored = (MustFightBattle) in.readObject();
    }
    restored.getGameData().postDeSerialize();
    final var resumed =
        MockDelegateBridge.newDelegateBridge(restored.getPlayer(BattleState.Side.OFFENSE));
    MockDelegateBridge.whenGetRandom(resumed).thenReturn(new int[] {0});
    restored.fight(resumed);
    assertThat(restored.getRemainingAttackingUnits()).isEmpty();
    assertThat(restored.getRemainingDefendingUnits()).isEmpty();
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(resumed, org.mockito.Mockito.times(1));
  }

  @Test
  void tacticalCategoriesSurviveSerialization() throws Exception {
    final var site = data.getMap().getTerritoryOrNull("France");
    final var bomber = unit("tactical_bomber", germans);
    final var target = unit("armour", british);
    final var battle =
        new MustFightBattle(site, germans, data, data.getBattleDelegate().getBattleTracker());
    battle.setUnits(List.of(target), List.of(bomber), List.of(), british, List.of());
    final var field = MustFightBattle.class.getDeclaredField("modEcrTacticalTargets");
    field.setAccessible(true);
    field.set(battle, java.util.Map.of(bomber, target.getType()));
    final var bytes = new java.io.ByteArrayOutputStream();
    try (final var out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(data);
      out.writeObject(battle);
    }
    try (final var in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      ((GameData) in.readObject()).postDeSerialize();
      final var restored = (MustFightBattle) in.readObject();
      final var restoredBomber =
          restored
              .filterUnits(BattleState.UnitBattleFilter.ALIVE, BattleState.Side.OFFENSE)
              .iterator()
              .next();
      assertThat(restored.getModEcrTacticalTargets())
          .containsEntry(
              restoredBomber,
              restored.getGameData().getUnitTypeList().getUnitTypeOrThrow("armour"));
      assertThat(
              FiringGroupSplitterGeneral.of(
                      BattleState.Side.OFFENSE, FiringGroupSplitterGeneral.Type.NORMAL, "units")
                  .apply(restored))
          .singleElement()
          .satisfies(
              group ->
                  assertThat(group.getTargetUnits().stream().map(Unit::getType).distinct().toList())
                      .extracting(games.strategy.engine.data.UnitType::getName)
                      .containsExactly("armour"));
    }
  }

  private SelectCasualties selection(
      final BattleState state, final FiringGroup group, final int hits) {
    final var fire = new FireRoundState();
    fire.setDice(new DiceRoll(List.of(), hits, hits, germans.getName()));
    return new SelectCasualties(
        state, BattleState.Side.OFFENSE, group, fire, (bridge, step) -> new CasualtyDetails());
  }
}
