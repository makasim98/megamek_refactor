package megamek.leaderboard;

import megamek.server.victory.VictoryResult;

import java.util.List;

public interface IRankingStrategy {
    List<PlayerStats> calcRankingUpdate(List<PlayerStats> currRanking, VictoryResult gameResult);
}
