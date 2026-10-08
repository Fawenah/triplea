package games.strategy.triplea.delegate;

import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.casualty.CasualtySelector;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import games.strategy.triplea.delegate.power.calculator.CombatValueBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.triplea.java.collections.IntegerMap;

/** A saved movement sub-stack separates mine rolls from interactive casualty selection. */
public final class ModEcrMineMove implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ExecutionStack stack = new ExecutionStack();
  @Getter private boolean mineRolled;
  @Getter private final List<Unit> survivors;
  private final Route route;
  private final GamePlayer player;
  private final UndoableMove move;

  public ModEcrMineMove(
      final Collection<Unit> units,
      final Route route,
      final GamePlayer player,
      final UndoableMove move) {
    survivors = new ArrayList<>(units);
    this.route = route;
    this.player = player;
    this.move = move;
    final var steps = new ArrayList<>(route.getSteps());
    java.util.Collections.reverse(steps);
    for (final var zone : steps) {
      if (zone.isWater()) {
        final var shot = new MineShot(zone);
        stack.push(shot);
      }
    }
  }

  public void execute(final IDelegateBridge bridge) {
    if (!Properties.getModEcrRules(bridge.getData().getProperties())
        || EditDelegate.getEditMode(bridge.getData().getProperties())) {
      return;
    }
    stack.execute(bridge);
  }

  private final class MineShot implements IExecutable {
    private static final long serialVersionUID = 1L;
    private final Territory zone;
    private Unit mine;
    private DiceRoll dice;
    private CasualtyDetails casualties;
    private boolean resolved;

    private MineShot(final Territory zone) {
      this.zone = zone;
    }

    @Override
    public void execute(final ExecutionStack execution, final IDelegateBridge bridge) {
      if (resolved) {
        return;
      }
      final var ships =
          survivors.stream()
              .filter(Matches.unitIsSea())
              .filter(Matches.unitIsNotInfrastructure())
              .toList();
      if (ships.isEmpty()) {
        return;
      }
      if (dice == null) {
        if (ModEcrMineRules.alreadyFired(bridge, player, zone)) {
          return;
        }
        mine =
            zone.getUnits().stream()
                .filter(ModEcrMineRules::isMine)
                .filter(u -> player.isAtWar(u.getOwner()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
          return;
        }
        move.setCantUndo("Movement cannot be undone after a naval mine roll");
        final int roll =
            bridge
                .getRandom(
                    bridge.getData().getDiceSides(),
                    1,
                    player,
                    DiceType.COMBAT,
                    "Naval mine entry in " + zone.getName())[0];
        mineRolled = true;
        final boolean hit = roll < 2;
        dice =
            new DiceRoll(
                List.of(new Die(roll, 2, hit ? Die.DieType.HIT : Die.DieType.MISS)),
                hit ? 1 : 0,
                0.2,
                player.getName());
        bridge
            .getHistoryWriter()
            .addChildToEvent(
                "Naval mine in "
                    + zone.getName()
                    + " rolls "
                    + (roll + 1)
                    + (hit ? ": hit" : ": miss"),
                dice);
      }
      ModEcrMineRules.markFired(bridge, player, zone);
      if (dice.getHits() == 0) {
        resolved = true;
        return;
      }
      final var data = bridge.getData();
      if (casualties == null) {
        casualties =
            CasualtySelector.selectCasualties(
                player,
                ships,
                CombatValueBuilder.mainCombatValue()
                    .friendlyUnits(survivors)
                    .enemyUnits(List.of(mine))
                    .side(BattleState.Side.DEFENSE)
                    .gameSequence(data.getSequence())
                    .gameDiceSides(data.getDiceSides())
                    .lhtrHeavyBombers(Properties.getLhtrHeavyBombers(data.getProperties()))
                    .supportAttachments(data.getUnitTypeList().getSupportRules())
                    .territoryEffects(List.of())
                    .build(),
                zone,
                bridge,
                "Naval mine: choose any moving ship to take one hit",
                dice,
                null,
                false,
                1,
                true);
      }
      final var changes = new CompositeChange();
      changes.add(ChangeFactory.removeUnits(zone, List.of(mine)));
      if (!casualties.getDamaged().isEmpty()) {
        final var damage = new IntegerMap<Unit>();
        for (final var unit : casualties.getDamaged()) {
          damage.put(unit, unit.getHits() + 1);
        }
        changes.add(ChangeFactory.unitsHit(damage, List.of(route.getStart())));
      }
      final List<Unit> removed = new ArrayList<>(casualties.getKilled());
      for (final var ship : casualties.getKilled()) {
        removed.addAll(ship.getTransporting(route.getStart()));
      }
      if (!removed.isEmpty()) {
        ModEcrBountyRules.record(bridge, casualties.getKilled(), List.of(mine.getOwner()));
        changes.add(ChangeFactory.removeUnits(route.getStart(), removed));
      }
      bridge.addChange(changes);
      survivors.removeAll(removed);
      bridge
          .getHistoryWriter()
          .addChildToEvent(
              "Mine destroyed in "
                  + zone.getName()
                  + "; sunk units and cargo: "
                  + games.strategy.triplea.formatter.MyFormatter.unitsToText(removed),
              removed);
      resolved = true;
    }
  }
}
