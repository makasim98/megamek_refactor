package megamek.leaderboard.ranking;

import megamek.common.Game;
import megamek.common.Player;
import megamek.common.Team;
import megamek.leaderboard.PlayerStats;
import megamek.server.Server;
import megamek.server.victory.VictoryResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LeaderboardRankingTest {

    private EloRankingStrategy strategy;
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
        strategy = new EloRankingStrategy();
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
    public void testLeaderboardRanking() {
        List<PlayerStats> result = strategy.calcRankingUpdate(players, victoryResult);
        PlayerStats winner = result.stream().filter(ps -> Objects.equals(ps.getName(), "P1")).collect(Collectors.toList()).get(0);
        PlayerStats loser = result.stream().filter(ps -> Objects.equals(ps.getName(), "P2")).collect(Collectors.toList()).get(0);
        assertTrue(winner.getRanking() > 1000);
        assertEquals(2, winner.getWins());
        assertTrue(loser.getRanking() < 1000);
        assertEquals(4, loser.getLoss());
    }
}
