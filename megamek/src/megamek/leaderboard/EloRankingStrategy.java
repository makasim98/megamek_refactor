package megamek.leaderboard;

import megamek.server.victory.VictoryResult;
import org.apache.logging.log4j.LogManager;

import java.util.List;

public class EloRankingStrategy implements IRankingStrategy {
    @Override
    public List<PlayerStats> calcRankingUpdate(List<PlayerStats> currRanking, VictoryResult gameResult) {
        LogManager.getLogger().warn("Elo ranking update not yet implemented");

        return currRanking;
    }
}
