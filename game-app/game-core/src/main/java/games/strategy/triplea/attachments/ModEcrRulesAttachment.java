package games.strategy.triplea.attachments;

import games.strategy.engine.data.Attachable;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MutableProperty;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.data.gameparser.GameParseException;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.triplea.java.collections.IntegerMap;

/** Aggregate MOD ECR objectives that cannot be expressed as per-territory conditions. */
public final class ModEcrRulesAttachment extends RulesAttachment {
  private static final long serialVersionUID = 1L;
  private static final Set<String> AXIS = Set.of("Germans", "Italians", "Japanese");
  private static final Set<String> BALTIC = Set.of("113 Sea Zone", "114 Sea Zone", "115 Sea Zone");
  private String check = "axisEconomy";

  public ModEcrRulesAttachment(
      final String name, final Attachable attachable, final GameData gameData) {
    super(name, attachable, gameData);
  }

  /** Uses the engine's capped, per-territory loss allocation, excluding acquired UK land. */
  public static void recordConvoyLoss(
      final GamePlayer victim, final IntegerMap<Territory> losses, final IDelegateBridge bridge) {
    if (!Properties.getModEcrRules(bridge.getData().getProperties())
        || !victim.getName().equals("British")) {
      return;
    }
    final int originalTerritoryLoss =
        losses.entrySet().stream()
            .filter(
                entry ->
                    TerritoryAttachment.get(entry.getKey())
                        .flatMap(TerritoryAttachment::getOriginalOwner)
                        .filter(victim::equals)
                        .isPresent())
            .mapToInt(Map.Entry::getValue)
            .sum();
    if (originalTerritoryLoss < 12) {
      return;
    }
    final var axis = bridge.getData().getPlayerList().getPlayerId("Germans");
    final var earned = RulesAttachment.get(axis, "conditionAttachment_ECR_Axis_London_Earned");
    if (Boolean.TRUE.equals(earned.getPropertyOrEmpty("switch").orElseThrow().getValue())) {
      return;
    }
    bridge
        .getHistoryWriter()
        .startEvent("Axis earn the London victory token from convoy disruption");
    bridge.addChange(ChangeFactory.attachmentPropertyChange(earned, "true", "switch"));
  }

  @Override
  public boolean isSatisfied(
      final Map<ICondition, Boolean> testedConditions, final IDelegateBridge delegateBridge) {
    if (!super.isSatisfied(testedConditions, delegateBridge)) {
      return false;
    }
    final var territories = delegateBridge.getData().getMap().getTerritories();
    return switch (check) {
      case "axisEconomy" ->
          territories.stream()
                  .filter(territory -> !territory.isWater())
                  .filter(territory -> AXIS.contains(territory.getOwner().getName()))
                  .mapToInt(TerritoryAttachment::getProduction)
                  .sum()
              >= 144;
      case "germanSubmarines" ->
          territories.stream()
                  .filter(territory -> !BALTIC.contains(territory.getName()))
                  .flatMap(territory -> territory.getUnits().stream())
                  .filter(unit -> unit.getOwner().equals(getAttachedTo()))
                  .filter(
                      unit ->
                          Set.of("submarine", "super_submarine").contains(unit.getType().getName()))
                  .count()
              >= 5;
      default -> throw new IllegalStateException("Unknown MOD ECR check: " + check);
    };
  }

  @Override
  public Optional<MutableProperty<?>> getPropertyOrEmpty(final String propertyName) {
    if (propertyName.equals("check")) {
      return Optional.of(
          MutableProperty.ofString(this::setCheck, () -> check, () -> check = "axisEconomy"));
    }
    return super.getPropertyOrEmpty(propertyName);
  }

  private void setCheck(final String value) throws GameParseException {
    if (!Set.of("axisEconomy", "germanSubmarines").contains(value)) {
      throw new GameParseException("Unknown MOD ECR check: " + value);
    }
    check = value;
  }
}
