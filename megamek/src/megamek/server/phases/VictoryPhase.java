package megamek.server.phases;

import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.event.GameVictoryEvent;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.common.util.EmailService;
import megamek.server.GameManager;
import megamek.server.Server;
import megamek.server.UnitStatusFormatter;
import org.apache.logging.log4j.LogManager;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Optional;
import java.util.Vector;

public class VictoryPhase extends AbstractGamePhase{
    public VictoryPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        GameVictoryEvent gve = new GameVictoryEvent(gameManager, game);
        game.processGameEvent(gve);
        transmitGameVictoryEventToAll();
        gameManager.resetGame();
        return Optional.empty();
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        gameManager.resetPlayersDone();
        gameManager.clearReports();
        gameManager.send(createAllReportsPacket());
        prepareVictoryReport();
        game.addReports(gameManager.getvPhaseReport());
        // Before we send the full entities packet we need to loop
        // through the fighters in squadrons and damage them.
        for (Iterator<Entity> ents = game.getEntities(); ents.hasNext(); ) {
            Entity entity = ents.next();
            if ((entity.isFighter()) && !(entity instanceof FighterSquadron)) {
                if (entity.isPartOfFighterSquadron() || entity.isCapitalFighter()) {
                    ((IAero) entity).doDisbandDamage();
                }
            }
            // fix the armor and SI of aeros if using aero sanity rules for
            // the MUL
            if (game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_AERO_SANITY)
                    && (entity instanceof Aero)) {
                // need to rescale SI and armor
                int scale = 1;
                if (entity.isCapitalScale()) {
                    scale = 10;
                }
                Aero a = (Aero) entity;
                int currentSI = a.getSI() / (2 * scale);
                a.set0SI(a.get0SI() / (2 * scale));
                if (currentSI > 0) {
                    a.setSI(currentSI);
                }
                //Fix for #587. MHQ tracks fighters at standard scale and doesn't (currently)
                //track squadrons. Squadrons don't save to MUL either, so... only convert armor for JS/WS/SS?
                //Do we ever need to save capital fighter armor to the final MUL or entityStatus?
                if (!entity.hasETypeFlag(Entity.ETYPE_JUMPSHIP)) {
                    scale = 1;
                }
                if (scale > 1) {
                    for (int loc = 0; loc < entity.locations(); loc++) {
                        int currentArmor = entity.getArmor(loc) / scale;
                        if (entity.getOArmor(loc) > 0) {
                            entity.initializeArmor(entity.getOArmor(loc) / scale, loc);
                        }
                        if (entity.getArmor(loc) > 0) {
                            entity.setArmor(currentArmor, loc);
                        }
                    }
                }
            }
        }
        EmailService mailer = Server.getServerInstance().getEmailService();
        if (mailer != null) {
            for (var player: mailer.getEmailablePlayers(game)) {
                try {
                    var message = mailer.newReportMessage(
                            game, gameManager.getvPhaseReport(), player
                    );
                    mailer.send(message);
                } catch (Exception ex) {
                    LogManager.getLogger().error("Error sending email" + ex);
                }
            }
        }
        gameManager.send(gameManager.createFullEntitiesPacket());
        gameManager.send(gameManager.createReportPacket(null));
        gameManager.send(createEndOfGamePacket());
    }

    /**
     * Creates a packet indicating end of game, including detailed unit status
     */
    private Packet createEndOfGamePacket() {
        return new Packet(PacketCommand.END_OF_GAME, getDetailedVictoryReport(),
                game.getVictoryPlayerId(), game.getVictoryTeam());
    }

    /**
     * Generates a detailed report for campaign use
     */
    private String getDetailedVictoryReport() {
        StringBuilder sb = new StringBuilder();

        Vector<Entity> vAllUnits = new Vector<>();
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            vAllUnits.addElement(i.next());
        }

        for (Enumeration<Entity> i = game.getRetreatedEntities(); i.hasMoreElements(); ) {
            vAllUnits.addElement(i.nextElement());
        }

        for (Enumeration<Entity> i = game.getGraveyardEntities(); i.hasMoreElements(); ) {
            vAllUnits.addElement(i.nextElement());
        }

        for (Enumeration<Player> i = game.getPlayers(); i.hasMoreElements(); ) {
            // Record the player.
            Player p = i.nextElement();
            sb.append("++++++++++ ").append(p.getName()).append(" ++++++++++\n");

            // Record the player's alive, retreated, or salvageable units.
            for (int x = 0; x < vAllUnits.size(); x++) {
                Entity e = vAllUnits.elementAt(x);
                if (e.getOwner() == p) {
                    sb.append(UnitStatusFormatter.format(e));
                }
            }

            // Record the player's devastated units.
            Enumeration<Entity> devastated = game.getDevastatedEntities();
            if (devastated.hasMoreElements()) {
                sb.append("=============================================================\n");
                sb.append("The following utterly destroyed units are not available for salvage:\n");
                while (devastated.hasMoreElements()) {
                    Entity e = devastated.nextElement();
                    if (e.getOwner() == p) {
                        sb.append(e.getShortName());
                        for (int pos = 0; pos < e.getCrew().getSlotCount(); pos++) {
                            sb.append(", ").append(e.getCrew().getNameAndRole(pos)).append(" (")
                                    .append(e.getCrew().getGunnery()).append('/')
                                    .append(e.getCrew().getPiloting()).append(")\n");
                        }
                    }
                }
                sb.append("=============================================================\n");
            }
        }

        return sb.toString();
    }

    /**
     * Sends out the game victory event to all connections
     */
    private void transmitGameVictoryEventToAll() {
        gameManager.send(new Packet(PacketCommand.GAME_VICTORY_EVENT));
    }

    /**
     * Creates a packet containing all the round reports unfiltered
     */
    private Packet createAllReportsPacket() {
        return new Packet(PacketCommand.SENDING_REPORTS_ALL, game.getAllReports());
    }

    /**
     * Writes the victory report
     */
    private void prepareVictoryReport() {
        // remove carcasses to the graveyard
        Vector<Entity> toRemove = new Vector<>();
        for (Entity e : game.getEntitiesVector()) {
            if (e.isCarcass() && !e.isDestroyed()) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            gameManager.destroyEntity(e, "crew death", false, true);
            game.removeEntity(e.getId(), IEntityRemovalConditions.REMOVE_SALVAGEABLE);
            e.setDestroyed(true);
        }

        gameManager.addReport(new Report(7000, Report.PUBLIC));

        // Declare the victor
        Report r = new Report(1210);
        r.type = Report.PUBLIC;
        if (game.getVictoryTeam() == Player.TEAM_NONE) {
            Player player = game.getPlayer(game.getVictoryPlayerId());
            if (null == player) {
                r.messageId = 7005;
            } else {
                r.messageId = 7010;
                r.add(player.getColorForPlayer());
            }
        } else {
            // Team victory
            r.messageId = 7015;
            r.add(game.getVictoryTeam());
        }
        gameManager.addReport(r);

        gameManager.bvReports(false);

        // List the survivors
        Iterator<Entity> survivors = game.getEntities();
        if (survivors.hasNext()) {
            gameManager.addReport(new Report(7023, Report.PUBLIC));
            while (survivors.hasNext()) {
                Entity entity = survivors.next();

                if (!entity.isDeployed()) {
                    continue;
                }

                gameManager.addReport(entity.victoryReport());
            }
        }
        // List units that never deployed
        Iterator<Entity> undeployed = game.getEntities();
        if (undeployed.hasNext()) {
            boolean wroteHeader = false;

            while (undeployed.hasNext()) {
                Entity entity = undeployed.next();

                if (entity.isDeployed()) {
                    continue;
                }

                if (!wroteHeader) {
                    gameManager.addReport(new Report(7075, Report.PUBLIC));
                    wroteHeader = true;
                }

                gameManager.addReport(entity.victoryReport());
            }
        }
        // List units that retreated
        Enumeration<Entity> retreat = game.getRetreatedEntities();
        if (retreat.hasMoreElements()) {
            gameManager.addReport(new Report(7080, Report.PUBLIC));
            while (retreat.hasMoreElements()) {
                Entity entity = retreat.nextElement();
                gameManager.addReport(entity.victoryReport());
            }
        }
        // List destroyed units
        Enumeration<Entity> graveyard = game.getGraveyardEntities();
        if (graveyard.hasMoreElements()) {
            gameManager.addReport(new Report(7085, Report.PUBLIC));
            while (graveyard.hasMoreElements()) {
                Entity entity = graveyard.nextElement();
                gameManager.addReport(entity.victoryReport());
            }
        }
        // List devastated units (not salvageable)
        Enumeration<Entity> devastated = game.getDevastatedEntities();
        if (devastated.hasMoreElements()) {
            gameManager.addReport(new Report(7090, Report.PUBLIC));

            while (devastated.hasMoreElements()) {
                Entity entity = devastated.nextElement();
                gameManager.addReport(entity.victoryReport());
            }
        }
        // Let player know about entitystatus.txt file
        gameManager.addReport(new Report(7095, Report.PUBLIC));
    }
}
