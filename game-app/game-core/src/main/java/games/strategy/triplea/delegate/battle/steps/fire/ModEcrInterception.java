package games.strategy.triplea.delegate.battle.steps.fire;

import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.ModEcrDice;
import games.strategy.triplea.delegate.battle.MustFightBattle;
import games.strategy.triplea.delegate.battle.casualty.CasualtySelector;
import games.strategy.triplea.delegate.battle.steps.BattleStep;
import games.strategy.triplea.delegate.battle.steps.change.ClearAaCasualties;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import games.strategy.triplea.delegate.power.calculator.CombatValueBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;

/** Both sides roll interception before either side selects or removes casualties. */
@AllArgsConstructor
public final class ModEcrInterception implements BattleStep {
  private static final long serialVersionUID = 1L;
  private final BattleState battleState;
  private final BattleActions battleActions;

  @Override
  public Order getOrder() {
    return Order.AA_REMOVE_CASUALTIES;
  }

  @Override
  public List<StepDetails> getAllStepDetails() {
    return steps().stream().flatMap(step -> step.getAllStepDetails().stream()).toList();
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
    final var steps = steps();
    Collections.reverse(steps);
    steps.forEach(stack::push);
  }

  private List<BattleStep> steps() {
    final List<BattleStep> rolls = new ArrayList<>();
    if (!Properties.getModEcrRules(battleState.getGameData().getProperties())
        || !battleState.getStatus().isFirstRound()) {
      return rolls;
    }
    final List<BattleStep> choices = new ArrayList<>();
    final List<BattleStep> marks = new ArrayList<>();
    for (final var side : BattleState.Side.values()) {
      final var fighters =
          battleState.filterUnits(BattleState.UnitBattleFilter.ALIVE, side).stream()
              .filter(u -> Set.of("fighter", "jet_fighter").contains(u.getType().getName()))
              .toList();
      final var planes =
          battleState.filterUnits(BattleState.UnitBattleFilter.ALIVE, side.getOpposite()).stream()
              .filter(Matches.unitIsAir())
              .toList();
      if (fighters.isEmpty() || planes.isEmpty()) {
        continue;
      }
      final var group =
          FiringGroup.groupBySuicideOnHit("interception", fighters, new ArrayList<>(planes)).get(0);
      final var fire = new FireRoundState();
      rolls.add(new RollDiceStep(battleState, side, group, fire, new InterceptionRoller()));
      choices.add(
          new SelectCasualties(battleState, side, group, fire, new InterceptionCasualties()));
      marks.add(
          new MarkCasualties(
              battleState, battleActions, side, group, fire, MustFightBattle.ReturnFire.ALL));
    }
    if (!rolls.isEmpty()) {
      rolls.addAll(choices);
      rolls.addAll(marks);
      rolls.add(new ClearAaCasualties(battleState, battleActions));
    }
    return rolls;
  }

  private static final class InterceptionRoller
      implements BiFunction<IDelegateBridge, RollDiceStep, DiceRoll>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public DiceRoll apply(final IDelegateBridge bridge, final RollDiceStep step) {
      return ModEcrDice.roll(
          step.getFiringGroup().getFiringUnits(),
          step.getBattleState(),
          step.getSide(),
          bridge,
          "Fighter interception in " + step.getBattleState().getBattleSite().getName(),
          u -> u.getType().getName().equals("jet_fighter") ? 3 : 1);
    }
  }

  private static final class InterceptionCasualties
      implements BiFunction<IDelegateBridge, SelectCasualties, CasualtyDetails>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public CasualtyDetails apply(final IDelegateBridge bridge, final SelectCasualties step) {
      final var state = step.getBattleState();
      final var side = step.getSide().getOpposite();
      final var targets = step.getFiringGroup().getTargetUnits();
      return CasualtySelector.selectCasualties(
          state.getPlayer(side),
          targets,
          CombatValueBuilder.mainCombatValue()
              .friendlyUnits(state.filterUnits(BattleState.UnitBattleFilter.ALIVE, side))
              .enemyUnits(state.filterUnits(BattleState.UnitBattleFilter.ALIVE, step.getSide()))
              .side(side)
              .gameSequence(state.getGameData().getSequence())
              .gameDiceSides(state.getGameData().getDiceSides())
              .lhtrHeavyBombers(Properties.getLhtrHeavyBombers(state.getGameData().getProperties()))
              .supportAttachments(state.getGameData().getUnitTypeList().getSupportRules())
              .territoryEffects(state.getTerritoryEffects())
              .build(),
          state.getBattleSite(),
          bridge,
          "Fighter interception",
          step.getFireRoundState().getDice(),
          state.getBattleId(),
          state.getStatus().isHeadless(),
          Math.min(targets.size(), step.getFireRoundState().getDice().getHits()),
          false);
    }
  }
}
