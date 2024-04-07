package megamek.leaderboard.storage;

import megamek.leaderboard.PlayerStats;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvLeaderboardStorage implements ILeaderboardStorage {
    private final String[] HEADERS = {"name", "email", "won", "lost", "ranking"};
    private String storagePath;

    public CsvLeaderboardStorage(String path) {
        storagePath = path + "/rankings.csv";
    }

    @Override
    public boolean save(List<PlayerStats> rankings) {


        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .build();

        try (
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(storagePath), StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(writer, csvFormat)
        ) {

            for (PlayerStats stats : rankings) {
                printer.printRecord(stats.toCsvRecord());
            }

        } catch (IOException e) {
            LogManager.getLogger().error(e.getStackTrace());
            return false;
        }

        return true;
    }

    @Override
    public List<PlayerStats> load() {
        ArrayList<PlayerStats> rankings= new ArrayList<>();

        try {
            Reader in = new FileReader(storagePath);

            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(HEADERS)
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> records = csvFormat.parse(in);

            for (CSVRecord record : records) {
                String name = record.get(HEADERS[0]);
                String email = record.get(HEADERS[1]);
                int gamesWon = Integer.parseInt(record.get(HEADERS[2]));
                int gamesLost = Integer.parseInt(record.get(HEADERS[3]));
                int ranking = Integer.parseInt(record.get(HEADERS[4]));

                rankings.add(new PlayerStats(name, email, gamesWon, gamesLost, ranking));
            }

        } catch (IOException e) {
            LogManager.getLogger().error("Failed to locate/read rankings file");
            return new ArrayList<>();
        }

        return rankings;
    }
}
