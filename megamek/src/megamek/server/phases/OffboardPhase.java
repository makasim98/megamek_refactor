package megamek.server.phases;

import megamek.common.Player;
import megamek.common.Report;
import megamek.common.enums.GamePhase;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Enumeration;
import java.util.Optional;

public class OffboardPhase extends AbstractGamePhase{
    public OffboardPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        // write Offboard Attack Phase header
        gameManager.addReport(new Report(1100, Report.PUBLIC));
        resolveAllButWeaponAttacks(); // torso twist or flip arms
        // possible
        resolveOnlyWeaponAttacks(); // should only be TAG at this point
        gameManager.handleAttacks(false);
        for (Enumeration<Player> i = game.getPlayers(); i.hasMoreElements(); ) {
            Player player = i.nextElement();
            int connId = player.getId();
            gameManager.send(connId, gameManager.createArtilleryPacket(player));
        }
        gameManager.applyBuildingDamage();
        checkForPSRFromDamage();
        gameManager.addReport(gameManager.resolvePilotingRolls());

        cleanupDestroyedNarcPods();
        checkForFlawedCooling();

        gameManager.sendSpecialHexDisplayPackets();
        sendTagInfoUpdates();

        // check reports
        GamePhase next;
        if (gameManager.getvPhaseReport().size() > 1) {
            game.addReports(gameManager.getvPhaseReport());
            next = GamePhase.OFFBOARD_REPORT;
        } else {
            // just the header, so we'll add the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            game.addReports(gameManager.getvPhaseReport());
            gameManager.sendReport();
            next = GamePhase.PREFIRING;
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
        fightPhasePrepareMethod(GamePhase.OFFBOARD);
    }

    private void sendTagInfoUpdates() {
        gameManager.send(new Packet(PacketCommand.SENDING_TAG_INFO, game.getTagInfo()));
    }
}
