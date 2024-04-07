package megamek.leaderboard;

import megamek.leaderboard.ranking.EloRankingStrategy;
import megamek.leaderboard.ranking.IRankingStrategy;
import megamek.leaderboard.ranking.RankingStrategy;
import megamek.leaderboard.storage.CsvLeaderboardStorage;
import megamek.leaderboard.storage.ILeaderboardStorage;
import megamek.leaderboard.storage.LeaderboardStorage;
import megamek.server.victory.VictoryResult;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardManager implements ILeaderboardManager {

    ILeaderboardStorage leaderboardStorage;
    IRankingStrategy rankingStrategy;

    private ArrayList<PlayerStats> playerRankings;

    public LeaderboardManager(LeaderboardStorage storageType, RankingStrategy rankingType) {
        leaderboardStorage = initStorage(storageType);
        rankingStrategy = initRankingAlgorithm(rankingType);

        playerRankings = new ArrayList<>(leaderboardStorage.load());
    }

    @Override
    public List<PlayerStats> getRankings() {
        if (playerRankings == null) {
            playerRankings = new ArrayList<>(leaderboardStorage.load());
        }

        return playerRankings;
    }

    @Override
    public List<PlayerStats> updateRankings(VictoryResult vr) {
        List<PlayerStats> newRankings = rankingStrategy.calcRankingUpdate(getRankings(), vr);

        if(!leaderboardStorage.save(newRankings)) {
            LogManager.getLogger().warn("Failed to persist changes in ranking. Retry the operation manually!");
        }

        playerRankings = new ArrayList<>(newRankings);

        return getRankings();
    }

    private ILeaderboardStorage initStorage(LeaderboardStorage type) {
        switch(type) {
            case CSV:
                return new CsvLeaderboardStorage("./leaderboard");
            case DB:
            case ExternalAPI:
            default:
                throw new NotImplementedException(String.format("Storage type %s is not yet implemented", type));
        }
    }

    private IRankingStrategy initRankingAlgorithm(RankingStrategy type) {
        switch(type) {
            case ELO:
                return new EloRankingStrategy();
            case TRUE_SKILL:
            default:
                throw new NotImplementedException(String.format("Ranking system %s is not yet implemented", type));
        }
    }
}
