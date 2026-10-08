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
    MockDelegateBridge.thenGetRandomShouldHaveBeenCalled(bridge, org.mockito.Mockito.times(2));
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

  private SelectCasualties selection(
      final BattleState state, final FiringGroup group, final int hits) {
    final var fire = new FireRoundState();
    fire.setDice(new DiceRoll(List.of(), hits, hits, germans.getName()));
    return new SelectCasualties(
        state, BattleState.Side.OFFENSE, group, fire, (bridge, step) -> new CasualtyDetails());
  }
}
