package games.strategy.triplea.delegate.battle;

import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.display.IDisplay;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.BattleState.Side;
import games.strategy.triplea.delegate.battle.BattleState.UnitBattleFilter;
import games.strategy.triplea.delegate.battle.steps.fire.FiringGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Special combat rules enabled only by the MOD ECR map. */
public final class ModEcrCombatRules {
  private ModEcrCombatRules() {}

  public static boolean isStrategicBomber(final Unit unit) {
    return Set.of("bomber", "heavy_bomber").contains(unit.getType().getName());
  }

  /** Withdraw attackers without moving or deleting their surviving aircraft. */
  public static void withdrawAttackingBombers(
      final BattleState state, final IDelegateBridge bridge) {
    withdrawBombers(state, bridge, Side.OFFENSE);
  }

  public static void withdrawDefendingBombers(
      final BattleState state, final IDelegateBridge bridge) {
    withdrawBombers(state, bridge, Side.DEFENSE);
  }

  private static void withdrawBombers(
      final BattleState state, final IDelegateBridge bridge, final Side side) {
    if (Properties.getModEcrRules(state.getGameData().getProperties())
        && state.getStatus().isFirstRound()
        && state.filterUnits(UnitBattleFilter.ALIVE, side.getOpposite()).stream()
            .anyMatch(Matches.unitIsNotInfrastructure())) {
      final var bombers =
          state.filterUnits(UnitBattleFilter.ALIVE, side).stream()
              .filter(ModEcrCombatRules::isStrategicBomber)
              .toList();
      if (bombers.isEmpty()) {
        return;
      }
      if (!state.getStatus().isHeadless()) {
        if (games.strategy.triplea.settings.ClientSetting.useWebsocketNetwork
            .getValue()
            .orElse(false)) {
          bridge.sendMessage(
              new IDisplay.NotifyUnitsRetreatingMessage(state.getBattleId(), bombers));
        } else {
          bridge.getDisplayChannelBroadcaster().notifyRetreat(state.getBattleId(), bombers);
        }
      }
      state.retreatUnits(side, bombers);
      bridge
          .getHistoryWriter()
          .addChildToEvent("Strategic bombers withdraw after round one", bombers);
    }
  }

  public static boolean isAntiTankTarget(final Unit unit) {
    return Set.of("armour", "heavy_tank", "mech_infantry", "mech_anti_tank_gun")
        .contains(unit.getType().getName());
  }

  public static boolean isAntiTankFiringGroup(final BattleState state, final FiringGroup group) {
    return Properties.getModEcrRules(state.getGameData().getProperties())
        && !group.getFiringUnits().isEmpty()
        && group.getFiringUnits().stream().allMatch(u -> hasAntiTankAbility(state, u));
  }

  public static List<FiringGroup> splitAntiTankGroups(
      final BattleState state, final List<FiringGroup> groups) {
    if (!Properties.getModEcrRules(state.getGameData().getProperties())) {
      return groups;
    }
    final List<FiringGroup> result = new ArrayList<>();
    for (final var group : groups) {
      final var specialists =
          group.getFiringUnits().stream().filter(u -> hasAntiTankAbility(state, u)).toList();
      if (specialists.isEmpty()) {
        result.add(group);
        continue;
      }
      result.addAll(
          FiringGroup.groupBySuicideOnHit("Anti-tank", specialists, group.getTargetUnits()));
      final Collection<Unit> ordinary = new ArrayList<>(group.getFiringUnits());
      ordinary.removeAll(specialists);
      if (!ordinary.isEmpty()) {
        result.addAll(
            FiringGroup.groupBySuicideOnHit(
                group.getGroupName(), ordinary, group.getTargetUnits()));
      }
    }
    return result;
  }

  private static boolean hasAntiTankAbility(final BattleState state, final Unit unit) {
    return unit.getType().getName().equals("anti_tank_gun")
        || (unit.getType().getName().equals("mech_anti_tank_gun")
            && state.getStatus().isFirstRound());
  }
}
