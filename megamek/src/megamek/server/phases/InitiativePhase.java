package megamek.server.phases;

import megamek.MegaMek;
import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

import java.util.Iterator;
import java.util.Optional;

public class InitiativePhase extends AbstractGamePhase{
    public InitiativePhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        resolveWhatPlayersCanSeeWhatUnits();
        detectSpacecraft();
        game.addReports(gameManager.getvPhaseReport());
        return Optional.of(GamePhase.INITIATIVE_REPORT);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        // remove the last traces of last round
        game.handleInitiativeCompensation();
        game.resetActions();
        game.resetTagInfo();
        sendTagInfoReset();
        gameManager.clearReports();
        resetEntityRound();
        resetEntityPhase(GamePhase.INITIATIVE);
        checkForObservers();
        gameManager.transmitAllPlayerUpdates();

        // roll 'em
        gameManager.resetActivePlayersDone();
        rollInitiative();
        //Cockpit command consoles that switched crew on the previous round are ineligible for force
        // commander initiative bonus. Now that initiative is rolled, clear the flag.
        game.getEntities().forEachRemaining(e -> e.getCrew().resetActedFlag());

        if (!game.shouldDeployThisRound()) {
            incrementAndSendGameRound();
        }

        // setIneligible(phase);
        gameManager.determineTurnOrder(GamePhase.INITIATIVE);
        gameManager.writeInitiativeReport(false);

        // checks for environmental survival
        checkForConditionDeath();

        checkForBlueShieldDamage();
        if (game.getBoard().inAtmosphere()) {
            checkForAtmosphereDeath();
        }
        if (game.getBoard().inSpace()) {
            checkForSpaceDeath();
        }

        gameManager.bvReports(true);

        LogManager.getLogger().info("Round " + game.getRoundCount() + " memory usage: " + MegaMek.getMemoryUsed());
    }

    private void sendTagInfoReset() {
        gameManager.send(new Packet(PacketCommand.RESET_TAG_INFO));
    }

    private void resetEntityRound() {
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            Entity entity = e.next();
            entity.newRound(game.getRoundCount());
        }
    }

    /**
     * Rolls initiative for all the players.
     */
    private void rollInitiative() {
        if (game.getOptions().booleanOption(OptionsConstants.RPG_INDIVIDUAL_INITIATIVE)) {
            TurnOrdered.rollInitiative(game.getEntitiesVector(), false);
        } else {
            // Roll for initiative on the teams.
            TurnOrdered.rollInitiative(game.getTeams(),
                    game.getOptions().booleanOption(OptionsConstants.INIT_INITIATIVE_STREAK_COMPENSATION)
                            && !game.shouldDeployThisRound());
        }

        gameManager.transmitAllPlayerUpdates();
    }

    /**
     * Increment's the server's game round and send it to all the clients
     */
    private void incrementAndSendGameRound() {
        game.incrementRoundCount();
        gameManager.send(new Packet(PacketCommand.ROUND_UPDATE, game.getRoundCount()));
    }

    /**
     * Check to see if anyone dies due to being in certain planetary conditions.
     */
    private void checkForConditionDeath() {
        Report r;
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity entity = i.next();
            if ((null == entity.getPosition()) && !entity.isOffBoard() || (entity.getTransportId() != Entity.NONE)) {
                // Ignore transported units, and units that don't have a position for some unknown reason
                continue;
            }
            String reason = game.getPlanetaryConditions().whyDoomed(entity, game);
            if (null != reason) {
                r = new Report(6015);
                r.subject = entity.getId();
                r.addDesc(entity);
                r.add(reason);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.destroyEntity(entity, reason, true, true));
            }
        }
    }

    private void checkForBlueShieldDamage() {
        Report r;
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity entity = i.next();
            if (!(entity instanceof Aero) && entity.hasActiveBlueShield()
                    && (entity.getBlueShieldRounds() >= 6)) {
                Roll diceRoll = Compute.rollD6(2);
                int target = (3 + entity.getBlueShieldRounds()) - 6;
                r = new Report(1240);
                r.addDesc(entity);
                r.add(target);
                r.add(diceRoll);

                if (diceRoll.getIntValue() < target) {
                    for (Mounted m : entity.getMisc()) {
                        if (m.getType().hasFlag(MiscType.F_BLUE_SHIELD)) {
                            m.setBreached(true);
                        }
                    }
                    r.choose(true);
                } else {
                    r.choose(false);
                }
                gameManager.addReport(r);
            }
        }
    }

    /**
     * Check to see if anyone dies due to being in atmosphere.
     */
    private void checkForAtmosphereDeath() {
        Report r;
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if ((null == entity.getPosition()) || entity.isOffBoard()) {
                // If it's not on the board - aboard something else, for
                // example...
                continue;
            }
            if (entity.doomedInAtmosphere() && (entity.getAltitude() == 0)) {
                r = new Report(6016);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.destroyEntity(entity,
                        "being in atmosphere where it can't survive", true,
                        true));
            }
        }
    }

    /**
     * Check to see if anyone dies due to being in space.
     */
    private void checkForSpaceDeath() {
        Report r;
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if ((null == entity.getPosition()) || entity.isOffBoard()) {
                // If it's not on the board - aboard something else, for
                // example...
                continue;
            }
            if (entity.doomedInSpace()) {
                r = new Report(6017);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.destroyEntity(entity,
                        "being in space where it can't survive", true, true));
            }
        }
    }
}
