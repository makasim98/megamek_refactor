package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Optional;

public class PremovementPhase extends AbstractGamePhase{
    public PremovementPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.MOVEMENT);
    }

    @Override
    public void executePhase() {
        super.executePhase();
        gameManager.changeToNextTurn(-1);
        if (game.getOptions().booleanOption(OptionsConstants.BASE_PARANOID_AUTOSAVE)) {
            gameManager.autoSave();
        }
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        fightPhasePrepareMethod(GamePhase.PREMOVEMENT);
    }
}