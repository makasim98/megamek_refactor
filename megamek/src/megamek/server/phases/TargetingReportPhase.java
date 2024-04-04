package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class TargetingReportPhase extends AbstractGamePhase{
    public TargetingReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.PREMOVEMENT);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
