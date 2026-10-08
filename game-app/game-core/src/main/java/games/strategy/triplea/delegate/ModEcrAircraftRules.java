package games.strategy.triplea.delegate;

import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.UnitUtils;
import java.util.List;
import java.util.Map;

/** End-of-phase reversion of free bomber reclassifications. */
public final class ModEcrAircraftRules {
  private ModEcrAircraftRules() {}

  public static void revertAircraft(final IDelegateBridge bridge, final GamePlayer player) {
    final var data = bridge.getData();
    if (!Properties.getModEcrRules(data.getProperties())) {
      return;
    }
    final var types = Map.of("transport_plane", "bomber", "cargo_plane", "heavy_bomber");
    for (final var territory : data.getMap().getTerritories()) {
      final var changes = new CompositeChange();
      for (final var unit : List.copyOf(territory.getUnits())) {
        final var replacement = types.get(unit.getType().getName());
        if (!unit.getOwner().equals(player) || replacement == null) {
          continue;
        }
        final var bomber = data.getUnitTypeList().getUnitTypeOrThrow(replacement).create(player);
        changes.add(UnitUtils.translateAttributesToOtherUnits(unit, List.of(bomber), territory));
        changes.add(
            ChangeFactory.unitPropertyChange(
                bomber, unit.getAlreadyMoved(), Unit.PropertyName.ALREADY_MOVED));
        changes.add(
            ChangeFactory.unitPropertyChange(
                bomber, unit.getBonusMovement(), Unit.PropertyName.BONUS_MOVEMENT));
        changes.add(
            ChangeFactory.unitPropertyChange(
                bomber, unit.getWasInCombat(), Unit.PropertyName.WAS_IN_COMBAT));
        changes.add(ChangeFactory.removeUnits(territory, List.of(unit)));
        changes.add(ChangeFactory.addUnits(territory, List.of(bomber)));
      }
      if (!changes.isEmpty()) {
        bridge
            .getHistoryWriter()
            .startEvent("Transport/cargo aircraft revert to bombers in " + territory.getName());
        bridge.addChange(changes);
      }
    }
  }
}
