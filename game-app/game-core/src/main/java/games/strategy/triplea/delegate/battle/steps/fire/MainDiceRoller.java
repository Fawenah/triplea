package games.strategy.triplea.delegate.battle.steps.fire;

import static games.strategy.triplea.delegate.battle.BattleState.UnitBattleFilter.ACTIVE;

import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.dice.RollDiceFactory;
import games.strategy.triplea.delegate.power.calculator.CombatValueBuilder;
import java.io.Serializable;
import java.util.function.BiFunction;

/** Rolls dice for normal (basically, anything that isn't AA) dice requests */
public class MainDiceRoller
    implements BiFunction<IDelegateBridge, RollDiceStep, DiceRoll>, Serializable {
  private static final long serialVersionUID = 11934707918127558L;

  @Override
  public DiceRoll apply(final IDelegateBridge bridge, final RollDiceStep step) {
    final var state = step.getBattleState();
    final var firing = step.getFiringGroup().getFiringUnits();
    final var targets = step.getFiringGroup().getTargetUnits();
    if (Properties.getModEcrRules(state.getGameData().getProperties())
        && state.getBattleSite().isWater()
        && step.getSide() == games.strategy.triplea.delegate.battle.BattleState.Side.OFFENSE
        && !firing.isEmpty()
        && !targets.isEmpty()
        && firing.stream()
            .allMatch(games.strategy.triplea.delegate.battle.ModEcrCombatRules::isStrategicBomber)
        && state.filterUnits(ACTIVE, step.getSide()).stream()
            .allMatch(games.strategy.triplea.delegate.battle.ModEcrCombatRules::isStrategicBomber)
        && targets.stream()
            .allMatch(games.strategy.triplea.delegate.Matches.unitIsSeaTransport())) {
      final var player = state.getPlayer(step.getSide());
      final var annotation =
          DiceRoll.getAnnotation(
              firing, player, state.getBattleSite(), state.getStatus().getRound());
      final var rolls =
          bridge.getRandom(
              state.getGameData().getDiceSides(),
              firing.size(),
              player,
              games.strategy.engine.random.IRandomStats.DiceType.COMBAT,
              annotation);
      final java.util.List<games.strategy.triplea.delegate.Die> dice = new java.util.ArrayList<>();
      int hits = 0;
      for (final int roll : rolls) {
        final boolean hit = roll < 7;
        hits += hit ? 1 : 0;
        dice.add(
            new games.strategy.triplea.delegate.Die(
                roll,
                7,
                hit
                    ? games.strategy.triplea.delegate.Die.DieType.HIT
                    : games.strategy.triplea.delegate.Die.DieType.MISS));
      }
      final var result = new DiceRoll(dice, hits, firing.size() * 0.7, player.getName());
      bridge.getHistoryWriter().addChildToEvent(annotation, result);
      return result;
    }
    return RollDiceFactory.rollBattleDice(
        step.getFiringGroup().getFiringUnits(),
        step.getBattleState().getPlayer(step.getSide()),
        bridge,
        DiceRoll.getAnnotation(
            step.getFiringGroup().getFiringUnits(),
            step.getBattleState().getPlayer(step.getSide()),
            step.getBattleState().getBattleSite(),
            step.getBattleState().getStatus().getRound()),
        CombatValueBuilder.mainCombatValue()
            .enemyUnits(step.getBattleState().filterUnits(ACTIVE, step.getSide().getOpposite()))
            .friendlyUnits(step.getBattleState().filterUnits(ACTIVE, step.getSide()))
            .side(step.getSide())
            .gameSequence(step.getBattleState().getGameData().getSequence())
            .supportAttachments(
                step.getBattleState().getGameData().getUnitTypeList().getSupportRules())
            .lhtrHeavyBombers(
                Properties.getLhtrHeavyBombers(step.getBattleState().getGameData().getProperties()))
            .gameDiceSides(step.getBattleState().getGameData().getDiceSides())
            .territoryEffects(step.getBattleState().getTerritoryEffects())
            .build());
  }
}
