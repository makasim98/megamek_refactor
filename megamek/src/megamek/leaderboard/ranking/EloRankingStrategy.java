package megamek.leaderboard.ranking;

import megamek.common.Player;
import megamek.common.Team;
import megamek.leaderboard.PlayerStats;
import megamek.leaderboard.ranking.IRankingStrategy;
import megamek.server.Server;
import megamek.server.victory.VictoryResult;
import org.apache.logging.log4j.LogManager;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class EloRankingStrategy implements IRankingStrategy {

    private int START_ELO_RANKING = 1000;
    private int K = 32;

    @Override
    public List<PlayerStats> calcRankingUpdate(List<PlayerStats> currRanking, VictoryResult gameResult) {
        List<Team> teams = Server.getServerInstance().getGame().getTeams();

        if (teams.size() != 2) {
            LogManager.getLogger().info("Algorithm does not currently support ranking for games with more than 2 teams");
            return currRanking;
        }
        int team1id = teams.get(0).getId();
        List<Player> team1Players = teams.get(0).players();
        List<Player> team2Players = teams.get(1).players();
        int team1elo = 0;
        int team2elo = 0;

        HashMap<String, PlayerStats> playerStatsMap = new HashMap<>();
        for (PlayerStats stats: currRanking) {
            playerStatsMap.put(stats.getName(), stats);
        }

        boolean didTeam1Win = gameResult.getWinningTeam() == team1id;
        boolean didTeam2Win = !didTeam1Win;
        int sValueTeam1 = didTeam1Win ? 1 : 0;
        int sValueTeam2 = didTeam2Win ? 1 : 0;
        for (Player p: team1Players) {
            if (playerStatsMap.containsKey(p.getName())) {
                team1elo += playerStatsMap.get(p.getName()).getRanking();
            }
            else {
                team1elo += START_ELO_RANKING;
                int win = didTeam1Win ? 1 : 0;
                int loss = didTeam1Win ? 0 : 1;
                playerStatsMap.put(p.getName(), new PlayerStats(p.getName(), p.getEmail(), win, loss, START_ELO_RANKING));
            }
        }
        for (Player p: team2Players) {
            if (playerStatsMap.containsKey(p.getName())) {
                team2elo += playerStatsMap.get(p.getName()).getRanking();
            }
            else {
                team2elo += START_ELO_RANKING;
                int win = didTeam2Win ? 1 : 0;
                int loss = didTeam2Win ? 0 : 1;
                playerStatsMap.put(p.getName(), new PlayerStats(p.getName(), p.getEmail(), win, loss, START_ELO_RANKING));
            }
        }
        team1elo /= team1Players.size();
        team2elo /= team2Players.size();

        double team1Exponent = ((team2elo - team1elo)/400.0);
        double team2Exponent = ((team1elo - team2elo)/400.0);
        double probTeam1 = 1 / (1 + Math.pow(10, team1Exponent));
        double probTeam2 = 1 / (1 + Math.pow(10, team2Exponent));

        for (Player p: team1Players) {
            PlayerStats currentPlayerStats = playerStatsMap.get(p.getName());
            int newRanking = (int) (currentPlayerStats.getRanking() + K*(sValueTeam1 - probTeam1));
            currentPlayerStats.setRanking(newRanking);
        }
        for (Player p: team2Players) {
            PlayerStats currentPlayerStats = playerStatsMap.get(p.getName());
            int newRanking = (int) (currentPlayerStats.getRanking() + K*(sValueTeam2 - probTeam2));
            currentPlayerStats.setRanking(newRanking);
        }
        // Update each loser's score based on winner
        return currRanking;
    }
}
