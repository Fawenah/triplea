package games.strategy.triplea.delegate;

import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Constants;
import games.strategy.triplea.Properties;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saved, synchronized bounty accounting, paid at the end of the current power's turn. */
public final class ModEcrBountyRules {
  private static final String PENDING = "MOD ECR pending capital-ship bounties";
  private static final String CREDITED = "MOD ECR credited capital ships";

  private ModEcrBountyRules() {}

  public static void record(
      final IDelegateBridge bridge,
      final Collection<Unit> sunk,
      final Collection<GamePlayer> participants) {
    final var data = bridge.getData();
    if (!Properties.getModEcrRules(data.getProperties())) {
      return;
    }
    final var credited = new HashSet<>(credited(bridge));
    final var pending = new HashMap<>(pending(bridge));
    for (final var ship : sunk) {
      if (!Set.of("battleship", "carrier", "super_battleship", "super_carrier")
              .contains(ship.getType().getName())
          || credited.contains(ship.getId().toString())) {
        continue;
      }
      for (final var step : data.getSequence()) {
        final var candidate = step.getPlayerId();
        if (candidate != null
            && participants.contains(candidate)
            && candidate.isAtWar(ship.getOwner())) {
          pending.merge(candidate.getName(), 1, Integer::sum);
          credited.add(ship.getId().toString());
          bridge
              .getHistoryWriter()
              .addChildToEvent(
                  candidate.getName() + " earns a capital-ship bounty, payable at end turn", ship);
          break;
        }
      }
    }
    if (!credited.equals(credited(bridge))) {
      bridge.addChange(
          new CompositeChange(
              ChangeFactory.setProperty(PENDING, pending, data),
              ChangeFactory.setProperty(CREDITED, credited, data)));
    }
  }

  public static String pay(final IDelegateBridge bridge) {
    final var data = bridge.getData();
    if (!Properties.getModEcrRules(data.getProperties()) || pending(bridge).isEmpty()) {
      return "";
    }
    final var changes = new CompositeChange();
    final var report = new StringBuilder();
    final var pus = data.getResourceList().getResourceOrThrow(Constants.PUS);
    pending(bridge).entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              final var player = data.getPlayerList().getPlayerId(entry.getKey());
              changes.add(ChangeFactory.changeResourcesChange(player, pus, entry.getValue()));
              final var text =
                  player.getName()
                      + " receives "
                      + entry.getValue()
                      + " IPC in capital-ship bounties";
              bridge.getHistoryWriter().startEvent(text);
              report.append(text).append("<br />");
            });
    changes.add(ChangeFactory.setProperty(PENDING, new HashMap<String, Integer>(), data));
    bridge.addChange(changes);
    return report.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Integer> pending(final IDelegateBridge bridge) {
    final var value = bridge.getData().getProperties().get(PENDING);
    return value == null ? Map.of() : (Map<String, Integer>) value;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> credited(final IDelegateBridge bridge) {
    final var value = bridge.getData().getProperties().get(CREDITED);
    return value == null ? Set.of() : (Set<String>) value;
  }
}
