package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class OffboardReportPhase extends AbstractGamePhase{
    public OffboardReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        gameManager.sendSpecialHexDisplayPackets();
        return Optional.of(GamePhase.PREFIRING);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
