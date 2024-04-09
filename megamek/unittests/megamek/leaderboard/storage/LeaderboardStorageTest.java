package megamek.leaderboard.storage;

import megamek.leaderboard.PlayerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LeaderboardStorageTest {

    private CsvLeaderboardStorage storage;
    private List<PlayerStats> players;

    @BeforeEach
    public void init() {
        initPlayerStats();
        storage = new CsvLeaderboardStorage("./leaderboard");
    }

    private void initPlayerStats() {
        PlayerStats p1 = new PlayerStats("P1", "p1@email.com", 1, 1, 1000);
        PlayerStats p2 = new PlayerStats("P2", "p2@email.com", 2, 3, 1000);
        players = Arrays.asList(p1, p2);
    }

    @Test
    public void testCsvStorage() {
        storage.save(players);

        List<PlayerStats> stats = storage.load();
        assertEquals(2, stats.size());
        assertEquals(1000, stats.get(0).getRanking());
        assertEquals(1000, stats.get(0).getRanking());
    }
}
