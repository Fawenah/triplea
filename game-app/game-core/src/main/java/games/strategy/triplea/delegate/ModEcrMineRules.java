package games.strategy.triplea.delegate;

import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Constants;
import games.strategy.triplea.Properties;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Purchase-phase placement and persistent movement-phase mine limits. */
public final class ModEcrMineRules {
  private static final String PURCHASE = "MOD ECR mine purchase zones ";
  private static final String FIRED = "MOD ECR mine entry zones ";

  private ModEcrMineRules() {}

  public static boolean isMine(final Unit unit) {
    final var type = unit.getType();
    return type != null && "naval_mine".equals(type.getName());
  }

  public static void offerPurchase(final IDelegateBridge bridge, final GamePlayer player) {
    final var data = bridge.getData();
    if (!Properties.getModEcrRules(data.getProperties())
        || player.isAi()
        || !games.strategy.triplea.attachments.TerritoryAttachment.doWeHaveEnoughCapitalsToProduce(
            player, data.getMap())) {
      return;
    }
    for (final var zone :
        data.getMap().getTerritories().stream()
            .filter(Territory::isWater)
            .sorted(Comparator.comparing(Territory::getName))
            .toList()) {
      if (savedZones(bridge, PURCHASE + player.getName()).contains(zone.getName())
          || allowance(bridge, player) == 0
          || player.getResources().getQuantity(Constants.PUS) < 2
          || hasFriendlyMine(zone, player)) {
        continue;
      }
      final var destroyers =
          zone.getUnits().stream()
              .filter(u -> u.isOwnedBy(player))
              .filter(Matches.unitIsDestroyer())
              .toList();
      if (destroyers.isEmpty()) {
        continue;
      }
      final var selected =
          bridge
              .getRemotePlayer(player)
              .selectUnitsQuery(
                  zone,
                  List.of(destroyers.get(0)),
                  "Lay one naval mine in "
                      + zone.getName()
                      + " for 2 IPC? Select the destroyer to lay it, or None to skip. Remaining allowance: "
                      + allowance(bridge, player));
      final var zones = new HashSet<>(savedZones(bridge, PURCHASE + player.getName()));
      zones.add(zone.getName());
      final var changes = new CompositeChange();
      if (selected != null && selected.contains(destroyers.get(0))) {
        final var mine = data.getUnitTypeList().getUnitTypeOrThrow("naval_mine").create(player);
        changes.add(
            ChangeFactory.changeResourcesChange(
                player, data.getResourceList().getResourceOrThrow(Constants.PUS), -2));
        changes.add(ChangeFactory.addUnits(zone, List.of(mine)));
        bridge
            .getHistoryWriter()
            .startEvent(
                player.getName() + " lays a naval mine in " + zone.getName() + " for 2 IPC", mine);
      }
      changes.add(ChangeFactory.setProperty(PURCHASE + player.getName(), zones, data));
      bridge.addChange(changes);
    }
  }

  public static int allowance(final IDelegateBridge bridge, final GamePlayer player) {
    int destroyers = 0;
    int mines = 0;
    for (final var zone : bridge.getData().getMap().getTerritories()) {
      for (final var unit : zone.getUnits()) {
        if (!unit.isOwnedBy(player)) {
          continue;
        }
        if (Matches.unitIsDestroyer().test(unit)) {
          destroyers++;
        }
        if (isMine(unit)) {
          mines++;
        }
      }
    }
    return Math.max(0, destroyers - mines);
  }

  private static boolean hasFriendlyMine(final Territory zone, final GamePlayer player) {
    return zone.getUnits().stream()
        .anyMatch(u -> isMine(u) && (u.isOwnedBy(player) || player.isAllied(u.getOwner())));
  }

  public static boolean alreadyFired(
      final IDelegateBridge bridge, final GamePlayer player, final Territory zone) {
    return savedZones(bridge, FIRED + player.getName()).contains(zone.getName());
  }

  public static void markFired(
      final IDelegateBridge bridge, final GamePlayer player, final Territory zone) {
    final var zones = new HashSet<>(savedZones(bridge, FIRED + player.getName()));
    zones.add(zone.getName());
    bridge.addChange(ChangeFactory.setProperty(FIRED + player.getName(), zones, bridge.getData()));
  }

  public static void finishMovement(final IDelegateBridge bridge, final GamePlayer player) {
    clearZones(bridge, FIRED + player.getName());
  }

  public static void finishPurchase(final IDelegateBridge bridge, final GamePlayer player) {
    clearZones(bridge, PURCHASE + player.getName());
  }

  private static void clearZones(final IDelegateBridge bridge, final String key) {
    if (!savedZones(bridge, key).isEmpty()) {
      bridge.addChange(ChangeFactory.setProperty(key, new HashSet<String>(), bridge.getData()));
    }
  }

  @SuppressWarnings("unchecked")
  private static Set<String> savedZones(final IDelegateBridge bridge, final String key) {
    final var value = bridge.getData().getProperties().get(key);
    return value == null ? Set.of() : (Set<String>) value;
  }
}
