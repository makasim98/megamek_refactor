package megamek.server.phases;

import megamek.common.GameTurn;
import megamek.common.Player;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Enumeration;
import java.util.Optional;
import java.util.Vector;

public class DeployMinefieldsPhase extends AbstractGamePhase{
    public DeployMinefieldsPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.of(GamePhase.INITIATIVE);
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
        checkForObservers();
        gameManager.transmitAllPlayerUpdates();
        gameManager.resetActivePlayersDone();
        setIneligible(GamePhase.DEPLOY_MINEFIELDS);

        Enumeration<Player> e = game.getPlayers();
        Vector<GameTurn> turns = new Vector<>();
        while (e.hasMoreElements()) {
            Player p = e.nextElement();
            if (p.hasMinefields() && game.getBoard().onGround()) {
                GameTurn gt = new GameTurn(p.getId());
                turns.addElement(gt);
            }
        }
        game.setTurnVector(turns);
        game.resetTurnIndex();

        // send turns to all players
        gameManager.send(gameManager.createTurnVectorPacket());
    }
}
