package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class MovementReportPhase extends AbstractGamePhase{
    public MovementReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.OFFBOARD);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
