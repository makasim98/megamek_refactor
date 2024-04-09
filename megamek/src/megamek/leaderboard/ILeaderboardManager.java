package megamek.leaderboard;

import megamek.common.Team;
import megamek.server.victory.VictoryResult;

import java.util.List;

public interface ILeaderboardManager {
    List<PlayerStats> getRankings();
    List<PlayerStats> updateRankings(VictoryResult vr);
}
