package megamek.leaderboard.storage;

import megamek.leaderboard.PlayerStats;

import java.util.List;

public interface ILeaderboardStorage {
    boolean save(List<PlayerStats> rankings);
    List<PlayerStats> load();
}
