package megamek.server.phases;

import megamek.common.Player;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Enumeration;
import java.util.Optional;

public class DeploymentPhase extends AbstractGamePhase{
    public DeploymentPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        game.clearDeploymentThisRound();
        game.checkForCompleteDeployment();
        Enumeration<Player> pls = game.getPlayers();
        while (pls.hasMoreElements()) {
            Player p = pls.nextElement();
            p.adjustStartingPosForReinforcements();
        }
        return game.getRoundCount() < 1 ? Optional.of(GamePhase.INITIATIVE) : Optional.of(GamePhase.TARGETING);
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
        fightPhasePrepareMethod(GamePhase.DEPLOYMENT);
    }
}
