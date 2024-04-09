package megamek.leaderboard;

import megamek.common.Game;
import megamek.common.Player;
import megamek.common.Team;
import megamek.leaderboard.ranking.RankingStrategy;
import megamek.leaderboard.storage.CsvLeaderboardStorage;
import megamek.leaderboard.storage.LeaderboardStorage;
import megamek.server.Server;
import megamek.server.victory.VictoryResult;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LeaderboardManagerTest {

    private LeaderboardManager manager;
    private List<PlayerStats> players;
    private List<Team> teams;
    private Server server;
    private MockedStatic<Server> staticMock;
    private Game game;
    private VictoryResult victoryResult;

    @BeforeEach
    public void init() {
        initPlayerStats();
        initTeams();
        initGame();
        prepareCsvFile();
        manager = new LeaderboardManager(LeaderboardStorage.CSV, RankingStrategy.ELO);
        server = Mockito.mock(Server.class);
        staticMock = Mockito.mockStatic(Server.class);
        staticMock.when(Server::getServerInstance).thenReturn(server);
        when(server.getGame()).thenReturn(game);
        victoryResult = Mockito.mock(VictoryResult.class);
        when(victoryResult.getWinningTeam()).thenReturn(1);
    }

    @AfterAll
    public void cleanup() {
        staticMock.close();
    }

    private void prepareCsvFile() {
        CsvLeaderboardStorage storage = new CsvLeaderboardStorage("./leaderboard");
        storage.save(players);
    }

    private void initPlayerStats() {
        PlayerStats p1 = new PlayerStats("P1", "p1@email.com", 1, 1, 1000);
        PlayerStats p2 = new PlayerStats("P2", "p2@email.com", 2, 3, 1000);
        players = Arrays.asList(p1, p2);
    }
    private void initTeams() {
        Player p1 = new Player(1, "P1");
        p1.setTeam(1);
        Player p2 = new Player(2, "P2");
        p2.setTeam(2);
        Team t1 = new Team(1);
        t1.addPlayer(p1);
        Team t2 = new Team(2);
        t2.addPlayer(p2);
        teams = Arrays.asList(t1, t2);
    }

    private void initGame() {
        game = Mockito.mock(Game.class);
        when(game.getTeams()).thenReturn(teams);
    }


    @Test
    public void testLeaderboardManager() {
        List<PlayerStats> result = manager.updateRankings(victoryResult);

        assertEquals(2, result.size());
        PlayerStats winner = result.stream().filter(ps -> Objects.equals(ps.getName(), "P1")).collect(Collectors.toList()).get(0);
        PlayerStats loser = result.stream().filter(ps -> Objects.equals(ps.getName(), "P2")).collect(Collectors.toList()).get(0);
        assertTrue(winner.getRanking() > 1000);
        assertEquals(2, winner.getWins());
        assertTrue(loser.getRanking() < 1000);
        assertEquals(4, loser.getLoss());

        result = manager.getRankings();

        assertEquals(2, result.size());
        winner = result.stream().filter(ps -> Objects.equals(ps.getName(), "P1")).collect(Collectors.toList()).get(0);
        loser = result.stream().filter(ps -> Objects.equals(ps.getName(), "P2")).collect(Collectors.toList()).get(0);
        assertTrue(winner.getRanking() > 1000);
        assertEquals(2, winner.getWins());
        assertTrue(loser.getRanking() < 1000);
        assertEquals(4, loser.getLoss());
    }
}
