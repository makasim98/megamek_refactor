package megamek.server.phases;

import megamek.common.Entity;
import megamek.common.EntitySelector;
import megamek.common.GameTurn;
import megamek.common.Player;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Enumeration;
import java.util.Optional;
import java.util.Vector;

public class SetArtilleryAutoHitHexesPhase extends AbstractGamePhase{
    public SetArtilleryAutoHitHexesPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        gameManager.sendSpecialHexDisplayPackets();
        Enumeration<Player> e = game.getPlayers();
        boolean mines = false;
        while (e.hasMoreElements() && !mines) {
            Player p = e.nextElement();
            if (p.hasMinefields()) {
                mines = true;
            }
        }
        game.addReports(gameManager.getvPhaseReport());
        return mines ? Optional.of(GamePhase.DEPLOY_MINEFIELDS) : Optional.of(GamePhase.INITIATIVE);
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
        deployOffBoardEntities();
        checkForObservers();
        gameManager.transmitAllPlayerUpdates();
        gameManager.resetActivePlayersDone();
        setIneligible(GamePhase.SET_ARTILLERY_AUTOHIT_HEXES);

        Enumeration<Player> players = game.getPlayers();
        Vector<GameTurn> turn = new Vector<>();

        // Walk through the players of the game, and add
        // a turn for all players with artillery weapons.
        while (players.hasMoreElements()) {
            // Get the next player.
            final Player p = players.nextElement();

            // Does the player have any artillery-equipped units?
            EntitySelector playerArtySelector = new EntitySelector() {
                private Player owner = p;

                @Override
                public boolean accept(Entity entity) {
                    return owner.equals(entity.getOwner()) && entity.isEligibleForArtyAutoHitHexes();
                }
            };

            if (game.getSelectedEntities(playerArtySelector).hasNext()) {
                // Yes, the player has arty-equipped units.
                GameTurn gt = new GameTurn(p.getId());
                turn.addElement(gt);
            }
        }
        game.setTurnVector(turn);
        game.resetTurnIndex();

        // send turns to all players
        gameManager.send(gameManager.createTurnVectorPacket());
    }
}
