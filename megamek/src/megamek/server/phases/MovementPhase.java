package megamek.server.phases;

import megamek.common.*;
import megamek.common.actions.TeleMissileAttackAction;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;
import megamek.server.ServerHelper;

import java.util.Iterator;
import java.util.Optional;
import java.util.Vector;

public class MovementPhase extends AbstractGamePhase{
    public MovementPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        gameManager.detectHiddenUnits();
        ServerHelper.detectMinefields(game, gameManager.getvPhaseReport(), gameManager);
        updateSpacecraftDetection();
        detectSpacecraft();
        resolveWhatPlayersCanSeeWhatUnits();
        doAllAssaultDrops();
        addMovementHeat();
        gameManager.applyBuildingDamage();
        checkForPSRFromDamage();
        gameManager.addReport(gameManager.resolvePilotingRolls()); // Skids cause damage in
        // movement phase
        gameManager.checkForFlamingDamage();
        checkForTeleMissileAttacks();
        cleanupDestroyedNarcPods();
        checkForFlawedCooling();
        gameManager.resolveCallSupport();
        // check phase report
        GamePhase next;
        Vector<Report> vPhaseReport = gameManager.getvPhaseReport();
        if (vPhaseReport.size() > 1) {
            game.addReports(vPhaseReport);
            next = GamePhase.MOVEMENT_REPORT;
        } else {
            // just the header, so we'll add the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            game.addReports(vPhaseReport);
            gameManager.sendReport();
            next = GamePhase.OFFBOARD;
        }
        return Optional.of(next);
    }

    @Override
    public void executePhase() {
        super.executePhase();
        gameManager.addReport(new Report(2000, Report.PUBLIC));
        gameManager.changeToNextTurn(-1);
        if (game.getOptions().booleanOption(OptionsConstants.BASE_PARANOID_AUTOSAVE)) {
            gameManager.autoSave();
        }
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        fightPhasePrepareMethod(GamePhase.MOVEMENT);
    }

    /**
     * Called at the end of movement. Determines if an entity
     * has moved beyond sensor range
     */
    private void updateSpacecraftDetection() {
        // Don't bother if we're not in space or if the game option isn't on
        if (!game.getBoard().inSpace()
                || !game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_STRATOPS_ADVANCED_SENSORS)) {
            return;
        }
        //Run through our list of units and remove any entities from the plotting board that have moved out of range
        for (Entity detector : game.getEntitiesVector()) {
            Compute.updateFiringSolutions(game, detector);
            Compute.updateSensorContacts(game, detector);
        }
    }

    /**
     * resolve assault drops for all entities
     */
    private void doAllAssaultDrops() {
        for (Entity e : game.getEntitiesVector()) {
            if (e.isAssaultDropInProgress() && e.isDeployed()) {
                gameManager.doAssaultDrop(e);
                e.setLandedAssaultDrop();
            }
        }
    }

    /**
     * Add heat from the movement phase
     */
    private void addMovementHeat() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            Entity entity = i.next();

            if (entity.hasDamagedRHS()) {
                entity.heatBuildup += 1;
            }

            if ((entity.getMovementMode() == EntityMovementMode.BIPED_SWIM)
                    || (entity.getMovementMode() == EntityMovementMode.QUAD_SWIM)) {
                // UMU heat
                entity.heatBuildup += 1;
                continue;
            }

            // build up heat from movement
            if (entity.isEvading() && !entity.isAero()) {
                entity.heatBuildup += entity.getRunHeat() + 2;
            } else if (entity.moved == EntityMovementType.MOVE_NONE) {
                entity.heatBuildup += entity.getStandingHeat();
            } else if ((entity.moved == EntityMovementType.MOVE_WALK)
                    || (entity.moved == EntityMovementType.MOVE_VTOL_WALK)
                    || (entity.moved == EntityMovementType.MOVE_CAREFUL_STAND)) {
                entity.heatBuildup += entity.getWalkHeat();
            } else if ((entity.moved == EntityMovementType.MOVE_RUN)
                    || (entity.moved == EntityMovementType.MOVE_VTOL_RUN)
                    || (entity.moved == EntityMovementType.MOVE_SKID)) {
                entity.heatBuildup += entity.getRunHeat();
            } else if (entity.moved == EntityMovementType.MOVE_JUMP) {
                entity.heatBuildup += entity.getJumpHeat(entity.delta_distance);
            } else if (entity.moved == EntityMovementType.MOVE_SPRINT
                    || entity.moved == EntityMovementType.MOVE_VTOL_SPRINT) {
                entity.heatBuildup += entity.getSprintHeat();
            }
        }
    }

    /**
     * Checks to see if any telemissiles are in a hex with enemy units. If so,
     * then attack one.
     */
    private void checkForTeleMissileAttacks() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if (entity instanceof TeleMissile) {
                // check for enemy units
                Vector<Integer> potTargets = new Vector<>();
                for (Entity te : game.getEntitiesVector(entity.getPosition())) {
                    //Telemissiles cannot target fighters or other telemissiles
                    //Fighters don't have a distinctive Etype flag, so we have to do
                    //this by exclusion.
                    if (!(te.hasETypeFlag(Entity.ETYPE_DROPSHIP)
                            || te.hasETypeFlag(Entity.ETYPE_SMALL_CRAFT)
                            || te.hasETypeFlag(Entity.ETYPE_JUMPSHIP)
                            || te.hasETypeFlag(Entity.ETYPE_WARSHIP)
                            || te.hasETypeFlag(Entity.ETYPE_SPACE_STATION))) {
                        continue;
                    }
                    if (te.isEnemyOf(entity)) {
                        // then add it to a vector of potential targets
                        potTargets.add(te.getId());
                    }
                }
                if (!potTargets.isEmpty()) {
                    // determine randomly
                    Entity target = game.getEntity(potTargets.get(Compute
                            .randomInt(potTargets.size())));
                    // report this and add a new TeleMissileAttackAction
                    Report r = new Report(9085);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.addDesc(target);
                    gameManager.addReport(r);
                    game.addTeleMissileAttack(new TeleMissileAttackAction(entity, target));
                }
            }
        }
    }
}
