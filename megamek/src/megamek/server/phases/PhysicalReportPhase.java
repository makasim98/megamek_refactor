package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class PhysicalReportPhase extends AbstractGamePhase{
    public PhysicalReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.END);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
