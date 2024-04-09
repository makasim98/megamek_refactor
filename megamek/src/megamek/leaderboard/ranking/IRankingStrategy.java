package megamek.leaderboard.ranking;

import megamek.common.Team;
import megamek.leaderboard.PlayerStats;
import megamek.server.victory.VictoryResult;

import java.util.List;

public interface IRankingStrategy {
    List<PlayerStats> calcRankingUpdate(List<PlayerStats> currRanking, VictoryResult gameResult);
}
