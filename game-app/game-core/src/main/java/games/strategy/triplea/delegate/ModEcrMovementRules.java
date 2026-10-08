package games.strategy.triplea.delegate;

import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.TerritoryAttachment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Movement and deployment rules enabled only for the MOD ECR variant. */
public final class ModEcrMovementRules {
  public enum MovementPhase {
    COMBAT,
    NONCOMBAT
  }

  private ModEcrMovementRules() {}

  public static int stackingLimit(final Territory territory) {
    return 10
        + TerritoryAttachment.get(territory).map(TerritoryAttachment::getProduction).orElse(0);
  }

  public static boolean countsTowardsStacking(final Unit unit) {
    return !unit.getUnitAttachment().isInfrastructure()
        && !unit.getUnitAttachment().isSea()
        && !unit.getType().getName().equals("aaGun")
        && !unit.getType().getName().equals("radar_aa")
        && !unit.getType().getName().equals("truck");
  }

  /** Existing defenders are not added to the attacking side's stack. */
  public static boolean fits(
      final Territory territory, final Collection<Unit> incoming, final GamePlayer player) {
    if (territory.isWater() || !Properties.getModEcrRules(player.getData().getProperties())) {
      return true;
    }
    final Set<Unit> occupants = new HashSet<>(territory.getUnits());
    occupants.addAll(incoming);
    return occupants.stream()
            .filter(unit -> unit.getOwner().isAllied(player))
            .filter(ModEcrMovementRules::countsTowardsStacking)
            .count()
        <= stackingLimit(territory);
  }

  public static Optional<String> validateStacking(
      final MoveDescription move, final GamePlayer player) {
    return validateStacking(
        move,
        player,
        GameStepPropertiesHelper.isCombatMove(player.getData(), true)
            ? MovementPhase.COMBAT
            : MovementPhase.NONCOMBAT);
  }

  public static Optional<String> validateStacking(
      final MoveDescription move, final GamePlayer player, final MovementPhase phase) {
    final var end = move.getRoute().getEnd();
    if (!Properties.getModEcrRules(player.getData().getProperties()) || end.isWater()) {
      return Optional.empty();
    }
    final boolean attacking =
        phase == MovementPhase.COMBAT
            && (Matches.isTerritoryEnemy(player).test(end)
                || end.anyUnitsMatch(Matches.enemyUnit(player))
                || AbstractMoveDelegate.getBattleTracker(player.getData()).wasConquered(end));
    final List<Unit> incoming = new ArrayList<>(move.getUnits());
    move.getAirTransportsDependents().values().forEach(incoming::addAll);
    if (attacking) {
      incoming.removeIf(Matches.unitIsAir());
    }
    final List<Unit> existingAttackingAir =
        attacking ? end.getMatches(Matches.unitIsAir().and(Matches.alliedUnit(player))) : List.of();
    // fits() normally includes aircraft already there; attacking aircraft are exempt.
    final Set<Unit> occupants = new HashSet<>(end.getUnits());
    occupants.removeAll(existingAttackingAir);
    occupants.addAll(incoming);
    final long count =
        occupants.stream()
            .filter(Matches.alliedUnit(player))
            .filter(ModEcrMovementRules::countsTowardsStacking)
            .count();
    return count > stackingLimit(end)
        ? Optional.of("Land stacking limit in " + end.getName() + " is " + stackingLimit(end))
        : Optional.empty();
  }

  public static List<Territory> overStackedTerritories(
      final GameData data, final GamePlayer player) {
    if (!Properties.getModEcrRules(data.getProperties())) {
      return List.of();
    }
    return data.getMap().getTerritories().stream()
        .filter(Matches.territoryIsLand())
        .filter(
            t ->
                t.anyUnitsMatch(
                    Matches.unitIsOwnedBy(player).and(ModEcrMovementRules::countsTowardsStacking)))
        .filter(t -> !fits(t, List.of(), player))
        .filter(t -> !AbstractMoveDelegate.getBattleTracker(data).hasPendingNonBombingBattle(t))
        .toList();
  }

  public static CompositeChange giveRailroadMovement(final GameData data, final GamePlayer player) {
    final var changes = new CompositeChange();
    if (!Properties.getModEcrRules(data.getProperties())) {
      return changes;
    }
    for (final var territory : data.getMap().getTerritories()) {
      if (!territory.isWater()
          && territory.anyUnitsMatch(
              u ->
                  u.getType().getName().equals("factory_major") && u.getOwner().isAllied(player))) {
        for (final var unit : territory.getUnits()) {
          if (unit.getOwner().equals(player)
              && unit.getType().getName().equals("infantry")
              && !unit.hasMoved()
              && unit.getTransportedBy() == null) {
            changes.add(
                ChangeFactory.unitPropertyChange(unit, 1, Unit.PropertyName.BONUS_MOVEMENT));
          }
        }
      }
    }
    return changes;
  }

  /** Truck seats take precedence over railroad allowances for a mixed move. */
  public static List<Unit> truckPassengers(final MoveDescription move) {
    if (!Properties.getModEcrRules(move.getRoute().getStart().getData().getProperties())) {
      return List.of();
    }
    if (move.getRoute().anyMatch(Matches.territoryIsWater())) {
      return List.of();
    }
    final long seats =
        3
            * move.getUnits().stream()
                .filter(u -> u.getType().getName().equals("truck") && !u.hasMoved())
                .count();
    return move.getUnits().stream()
        .filter(u -> u.getType().getName().equals("infantry"))
        .filter(u -> !u.hasMoved())
        .filter(u -> move.getRoute().getMovementCost(u).compareTo(BigDecimal.ONE) > 0)
        .limit(seats)
        .toList();
  }

  public static Optional<String> validateRailroad(
      final MoveDescription move, final List<UndoableMove> moves) {
    return validateRailroad(
        move,
        moves,
        GameStepPropertiesHelper.isNonCombatMove(move.getRoute().getStart().getData(), true)
            ? MovementPhase.NONCOMBAT
            : MovementPhase.COMBAT);
  }

  public static Optional<String> validateRailroad(
      final MoveDescription move, final List<UndoableMove> moves, final MovementPhase phase) {
    final var data = move.getRoute().getStart().getData();
    if (!Properties.getModEcrRules(data.getProperties()) || phase != MovementPhase.NONCOMBAT) {
      return Optional.empty();
    }
    final var passengers = truckPassengers(move);
    final Set<Unit> airPassengers = new HashSet<>();
    move.getAirTransportsDependents().values().forEach(airPassengers::addAll);
    final Set<Unit> checked = new HashSet<>();
    for (final var unit : move.getUnits()) {
      if (isRailroadInfantry(unit)
          && !passengers.contains(unit)
          && !airPassengers.contains(unit)
          && unit.getAlreadyMoved()
                  .add(move.getRoute().getMovementCost(unit))
                  .compareTo(BigDecimal.ONE)
              > 0) {
        final var origin = origin(unit, moves, move.getRoute().getStart());
        final Set<Unit> used = new HashSet<>();
        for (final var previous : moves) {
          for (final var candidate : previous.getUnits()) {
            if (isRailroadInfantry(candidate)
                && candidate.getAlreadyMoved().compareTo(BigDecimal.ONE) > 0
                && origin(candidate, moves, previous.getRoute().getStart()).equals(origin)) {
              used.add(candidate);
            }
          }
        }
        checked.add(unit);
        used.addAll(
            checked.stream()
                .filter(u -> origin(u, moves, move.getRoute().getStart()).equals(origin))
                .toList());
        if (used.size() > stackingLimit(origin) - 10) {
          return Optional.of(
              "Railroad allowance in "
                  + origin.getName()
                  + " is "
                  + (stackingLimit(origin) - 10)
                  + " infantry per turn");
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isRailroadInfantry(final Unit unit) {
    return unit.getType().getName().equals("infantry") && unit.getBonusMovement() == 1;
  }

  private static Territory origin(
      final Unit unit, final List<UndoableMove> moves, final Territory fallback) {
    return moves.stream()
        .filter(m -> m.getUnits().contains(unit))
        .findFirst()
        .map(m -> m.getRoute().getStart())
        .orElse(fallback);
  }
}
