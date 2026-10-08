package games.strategy.triplea.delegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.Constants;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.triplea.java.collections.IntegerMap;

final class AbstractEndTurnDelegateTest {
  @Test
  @DisplayName("ECR convoy rolls count all d6 faces while existing maps retain their formula")
  void convoyDieValues() {
    final var properties = new GameData().getProperties();
    for (int roll = 1; roll <= 6; roll++) {
      assertThat(AbstractEndTurnDelegate.getConvoyDieLoss(roll, properties))
          .isEqualTo(roll <= 3 ? roll : 0);
    }
    properties.set(Constants.CONVOY_BLOCKADES_COUNT_ALL_DICE, true);
    for (int roll = 1; roll <= 6; roll++) {
      assertThat(AbstractEndTurnDelegate.getConvoyDieLoss(roll, properties)).isEqualTo(roll);
    }
  }

  @Nested
  final class FindEstimatedIncomeTest extends AbstractDelegateTestCase {
    @Test
    void testFindEstimatedIncome() {
      final GameData global40Data = TestMapGameData.GLOBAL1940.getGameData();
      final GamePlayer germans = GameDataTestUtil.germans(global40Data);
      final IntegerMap<Resource> results =
          AbstractEndTurnDelegate.findEstimatedIncome(germans, global40Data);
      final int pus = results.getInt(new Resource(Constants.PUS, global40Data));
      assertEquals(40, pus);
    }
  }

  @Nested
  final class GetSingleNeighborBlockadesThenHighestToLowestProductionTest {
    private final GameData gameData = new GameData();
    private final Comparator<Territory> comparator =
        AbstractEndTurnDelegate.getSingleNeighborBlockadesThenHighestToLowestProduction(
            List.of(), gameData.getMap());
    private final Territory territory = new Territory("territoryName", gameData);

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreNull() {
      assertThat(comparator.compare(null, null)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreSame() {
      assertThat(comparator.compare(territory, territory)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreEqual() {
      assertThat(comparator.compare(territory, new Territory(territory.getName(), gameData)))
          .isEqualTo(0);
    }

    @Test
    void shouldReturnLessThanZeroWhenFirstTerritoryIsNonNullAndSecondTerritoryIsNull() {
      assertThat(comparator.compare(territory, null)).isLessThan(0);
    }

    @Test
    void shouldReturnGreaterThanZeroWhenFirstTerritoryIsNullAndSecondTerritoryIsNonNull() {
      assertThat(comparator.compare(null, territory)).isGreaterThan(0);
    }
  }

  @Nested
  final class GetSingleBlockadeThenHighestToLowestBlockadeDamageTest {
    private final GameData gameData = new GameData();
    private final Comparator<Territory> comparator =
        AbstractEndTurnDelegate.getSingleBlockadeThenHighestToLowestBlockadeDamage(Map.of());
    private final Territory territory = new Territory("territoryName", gameData);

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreNull() {
      assertThat(comparator.compare(null, null)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreSame() {
      assertThat(comparator.compare(territory, territory)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenBothTerritoriesAreEqual() {
      assertThat(comparator.compare(territory, new Territory(territory.getName(), gameData)))
          .isEqualTo(0);
    }

    @Test
    void shouldReturnLessThanZeroWhenFirstTerritoryIsNonNullAndSecondTerritoryIsNull() {
      assertThat(comparator.compare(territory, null)).isLessThan(0);
    }

    @Test
    void shouldReturnGreaterThanZeroWhenFirstTerritoryIsNullAndSecondTerritoryIsNonNull() {
      assertThat(comparator.compare(null, territory)).isGreaterThan(0);
    }
  }
}
