package games.strategy.triplea.delegate.battle;

import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.Die;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.ToIntFunction;

/** One unmodified combat die per unit, for ECR's fixed-accuracy attacks. */
public final class ModEcrDice {
  private ModEcrDice() {}

  public static DiceRoll roll(
      final Collection<Unit> units,
      final BattleState state,
      final BattleState.Side side,
      final IDelegateBridge bridge,
      final String annotation,
      final ToIntFunction<Unit> strength) {
    final var player = state.getPlayer(side);
    if (units.isEmpty()) {
      return new DiceRoll(java.util.List.of(), 0, 0, player.getName());
    }
    final int sides = state.getGameData().getDiceSides();
    final int[] rolls = bridge.getRandom(sides, units.size(), player, DiceType.COMBAT, annotation);
    final var dice = new ArrayList<Die>();
    int index = 0;
    int hits = 0;
    double expected = 0;
    for (final var unit : units) {
      final int hitAt = strength.applyAsInt(unit);
      final int roll = rolls[index++];
      final boolean hit = roll < hitAt;
      hits += hit ? 1 : 0;
      expected += hitAt / (double) sides;
      dice.add(new Die(roll, hitAt, hit ? Die.DieType.HIT : Die.DieType.MISS));
    }
    final var result = new DiceRoll(dice, hits, expected, player.getName());
    bridge.getHistoryWriter().addChildToEvent(annotation, result);
    return result;
  }
}
