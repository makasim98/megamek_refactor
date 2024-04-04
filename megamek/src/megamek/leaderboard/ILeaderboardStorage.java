package megamek.leaderboard;

import java.util.List;

public interface ILeaderboardStorage {
    boolean save(List<PlayerStats> rankings);
    List<PlayerStats> load();
}
