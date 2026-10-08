package games.strategy.triplea.delegate.battle;

import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.steps.fire.FiringGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed tactical bomber categories are declared before the first battle die. */
public final class ModEcrTacticalRules {
  private ModEcrTacticalRules() {}

  public static Map<Unit, UnitType> chooseTargets(
      final BattleState state, final IDelegateBridge bridge) {
    final Map<Unit, UnitType> result = new HashMap<>();
    if (!Properties.getModEcrRules(state.getGameData().getProperties())
        || state.getStatus().isHeadless()) {
      return result;
    }
    final var bombers =
        state.filterUnits(BattleState.UnitBattleFilter.ALIVE, BattleState.Side.OFFENSE).stream()
            .filter(u -> u.getType().getName().equals("tactical_bomber"))
            .toList();
    if (bombers.isEmpty()) {
      return result;
    }
    final var enemies =
        state.filterUnits(BattleState.UnitBattleFilter.ALIVE, BattleState.Side.DEFENSE);
    final var categories =
        enemies.stream()
            .filter(Matches.unitIsNotInfrastructure())
            .filter(u -> allowedTarget(state, u, enemies))
            .map(Unit::getType)
            .distinct()
            .sorted(Comparator.comparing(UnitType::getName))
            .toList();
    if (categories.isEmpty()) {
      return result;
    }
    final var player = bridge.getRemotePlayer(state.getPlayer(BattleState.Side.OFFENSE));
    final var selected =
        player.selectUnitsQuery(
            state.getBattleSite(),
            bombers,
            "Select tactical bombers for targeted attacks at 4. Unselected bombers attack normally at 5.");
    if (selected == null || selected.isEmpty()) {
      return result;
    }
    final List<Unit> remaining =
        new ArrayList<>(bombers.stream().filter(selected::contains).toList());
    for (final var category : categories) {
      if (remaining.isEmpty()) {
        break;
      }
      final var assigned =
          player.selectUnitsQuery(
              state.getBattleSite(),
              List.copyOf(remaining),
              "Select bombers targeting "
                  + category.getName()
                  + " at 4 for the entire battle. Excess hits are lost. Unassigned bombers attack normally.");
      if (assigned == null) {
        continue;
      }
      for (final var bomber : List.copyOf(remaining)) {
        if (assigned.contains(bomber)) {
          result.put(bomber, category);
          remaining.remove(bomber);
        }
      }
    }
    if (!result.isEmpty()) {
      bridge
          .getHistoryWriter()
          .addChildToEvent(
              "Tactical bomber targets: "
                  + result.values().stream().map(UnitType::getName).sorted().toList(),
              new ArrayList<>(result.keySet()));
    }
    return result;
  }

  private static boolean allowedTarget(
      final BattleState state, final Unit target, final Collection<Unit> enemies) {
    if (target.getType().getName().equals("submarine")
        && state.filterUnits(BattleState.UnitBattleFilter.ACTIVE, BattleState.Side.OFFENSE).stream()
            .noneMatch(Matches.unitIsDestroyer())) {
      return false;
    }
    return !Matches.unitIsSeaTransport().test(target)
        || enemies.stream()
            .noneMatch(
                u ->
                    Matches.unitIsAir().test(u)
                        || (Matches.unitIsSea().test(u)
                            && !Matches.unitIsSeaTransport().test(u)
                            && !Matches.unitIsInfrastructure().test(u)));
  }

  public static List<FiringGroup> splitGroups(
      final BattleState state, final BattleState.Side side, final Collection<FiringGroup> groups) {
    final var choices = state.getModEcrTacticalTargets();
    if (side != BattleState.Side.OFFENSE
        || choices.isEmpty()
        || !Properties.getModEcrRules(state.getGameData().getProperties())) {
      return new ArrayList<>(groups);
    }
    final List<FiringGroup> result = new ArrayList<>();
    for (final var group : groups) {
      final var ordinary =
          group.getFiringUnits().stream().filter(u -> !choices.containsKey(u)).toList();
      if (!ordinary.isEmpty()) {
        result.addAll(
            FiringGroup.groupBySuicideOnHit(
                group.getDisplayName(), ordinary, new ArrayList<>(group.getTargetUnits())));
      }
      final var categories =
          group.getFiringUnits().stream()
              .filter(choices::containsKey)
              .map(choices::get)
              .distinct()
              .sorted(Comparator.comparing(UnitType::getName))
              .toList();
      for (final var category : categories) {
        final var firing =
            group.getFiringUnits().stream().filter(u -> category.equals(choices.get(u))).toList();
        final var targets =
            group.getTargetUnits().stream()
                .filter(u -> u.getType().equals(category))
                .filter(
                    u ->
                        !u.getType().getName().equals("submarine")
                            || state.filterUnits(BattleState.UnitBattleFilter.ACTIVE, side).stream()
                                .anyMatch(Matches.unitIsDestroyer()))
                .toList();
        if (!targets.isEmpty()) {
          result.addAll(
              FiringGroup.groupBySuicideOnHit(
                  "Targeted " + category.getName(), firing, new ArrayList<>(targets)));
        }
      }
    }
    return result;
  }

  public static boolean isTargeted(
      final BattleState state, final BattleState.Side side, final FiringGroup group) {
    return side == BattleState.Side.OFFENSE
        && Properties.getModEcrRules(state.getGameData().getProperties())
        && !group.getFiringUnits().isEmpty()
        && group.getFiringUnits().stream().allMatch(state.getModEcrTacticalTargets()::containsKey);
  }
}
