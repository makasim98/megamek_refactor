package megamek.leaderboard;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class PlayerStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String email;
    private int ranking;
    private int wins;
    private int loss;

    public String getName() {
        return name;
    }
    public String getEmail() {
        return email;
    }
    public int getRanking()
    {
        return ranking;
    }
    public void setRanking(int ranking) { this.ranking = ranking;}
    public int getWins()
    {
        return wins;
    }
    public void incrementWins() { this.wins += 1;}
    public int getLoss()
    {
        return loss;
    }
    public void incrementLosses() { this.loss += 1;}

    public PlayerStats(String name, String email, int wins, int loss, int ranking) {
        this.name = name;
        this.email = email;
        this.ranking = ranking;
        this.wins = wins;
        this.loss = loss;
    }

    public List<Object> toCsvRecord() {
        return Arrays.asList(name, email, wins, loss, ranking);
    }
}
