package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class InitiativeReportPhase extends AbstractGamePhase{
    public InitiativeReportPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        // NOTE: now that aeros can come and go from the battlefield, I
        // need
        game.setupRoundDeployment();
        // boolean doDeploy = game.shouldDeployThisRound() &&
        // (game.getLastPhase() != Game.Phase.DEPLOYMENT);
        return game.shouldDeployThisRound() ? Optional.of(GamePhase.DEPLOYMENT) : Optional.of(GamePhase.TARGETING);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        gameManager.autoSave();
    }
}
