package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class FiringReportPhase extends AbstractGamePhase{
    public FiringReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.PHYSICAL);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
