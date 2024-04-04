package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class StartingScenarioPhase extends AbstractGamePhase{
    public StartingScenarioPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        game.addReports(gameManager.getvPhaseReport());
        return Optional.of(GamePhase.SET_ARTILLERY_AUTOHIT_HEXES);
    }
}
