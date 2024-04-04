package megamek.server.phases;

import megamek.common.MapSettings;
import megamek.common.enums.GamePhase;
import megamek.server.GameManager;
import megamek.server.ServerBoardHelper;

import java.util.Optional;

import static megamek.server.GameManager.DEFAULT_BOARD;

public class LoungePhase extends AbstractGamePhase {


    public LoungePhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        game.addReports(gameManager.getvPhaseReport());
        return Optional.of(GamePhase.EXCHANGE);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        gameManager.clearReports();
        MapSettings mapSettings = game.getMapSettings();
        mapSettings.setBoardsAvailableVector(ServerBoardHelper.scanForBoards(mapSettings));
        mapSettings.setNullBoards(DEFAULT_BOARD);
        gameManager.send(gameManager.createMapSettingsPacket());
        gameManager.send(gameManager.createMapSizesPacket());
        checkForObservers();
        gameManager.transmitAllPlayerUpdates();
    }
}
