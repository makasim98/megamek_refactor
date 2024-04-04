package megamek.server.phases;

import megamek.common.Game;
import megamek.common.Player;
import megamek.common.Report;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Enumeration;
import java.util.Optional;

public class TargetingPhase extends AbstractGamePhase{
    public TargetingPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        gameManager.addReport(new Report(1035, Report.PUBLIC));
        resolveAllButWeaponAttacks();
        resolveOnlyWeaponAttacks();
        gameManager.handleAttacks(false); //False as parameter
        // check reports
        GamePhase next;
        if (gameManager.getvPhaseReport().size() > 1) {
            game.addReports(gameManager.getvPhaseReport());
            next = GamePhase.TARGETING_REPORT;
        } else {
            // just the header, so we'll add the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            game.addReports(gameManager.getvPhaseReport());
            gameManager.sendReport();
            next = GamePhase.PREMOVEMENT;
        }

        gameManager.sendSpecialHexDisplayPackets();
        for (Enumeration<Player> i = game.getPlayers(); i.hasMoreElements(); ) {
            Player player = i.nextElement();
            int connId = player.getId();
            gameManager.send(connId, gameManager.createArtilleryPacket(player));
        }
        return Optional.of(next);
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
        fightPhasePrepareMethod(GamePhase.TARGETING);
    }
}
