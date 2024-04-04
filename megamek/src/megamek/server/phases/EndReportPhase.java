package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class EndReportPhase extends AbstractGamePhase{
    public EndReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        if (gameManager.canPlayerChangeTeam()) {
            gameManager.processTeamChangeRequest();
        }
        GamePhase next = gameManager.victory() ? GamePhase.VICTORY : GamePhase.INITIATIVE;
        return Optional.of(next);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        preparePhaseReportMethod();
    }
}
