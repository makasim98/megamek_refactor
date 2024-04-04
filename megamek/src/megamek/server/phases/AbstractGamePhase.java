package megamek.server.phases;

import megamek.common.*;
import megamek.common.actions.*;
import megamek.common.enums.GamePhase;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.common.preference.PreferenceManager;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.Weapon;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

import java.util.*;

public abstract class AbstractGamePhase {

    protected Game game;
    protected GameManager gameManager;

    public AbstractGamePhase(GameManager manager) {
        this.game = manager.getGame();
        this.gameManager = manager;
    }

    public abstract Optional<GamePhase> endPhase();

    public void executePhase() {

    }

    public void preparePhase() {

    }

    protected void fightPhasePrepareMethod(GamePhase phase) {
        deployOffBoardEntities();

        // Check for activating hidden units
        if (game.getOptions().booleanOption(OptionsConstants.ADVANCED_HIDDEN_UNITS)) {
            for (Entity ent : game.getEntitiesVector()) {
                if (ent.getHiddenActivationPhase() == phase) {
                    ent.setHidden(false);
                }
            }
        }
        // Update visibility indications if using double blind.
        if (gameManager.doBlind()) {
            gameManager.updateVisibilityIndicator(null);
        }
        resetEntityPhase(phase);
        checkForObservers();
        gameManager.transmitAllPlayerUpdates();
        gameManager.resetActivePlayersDone();
        setIneligible(phase);
        gameManager.determineTurnOrder(phase);
        gameManager.entityAllUpdate();
        gameManager.clearReports();
        doTryUnstuck();
    }

    protected void preparePhaseReportMethod() {
        gameManager.resetActivePlayersDone();
        gameManager.sendReport();
        gameManager.entityAllUpdate();
        if (game.getOptions().booleanOption(OptionsConstants.BASE_PARANOID_AUTOSAVE)) {
            gameManager.autoSave();
        }
    }

    /**
     * let all Entities make their "break-free-of-swamp-stickyness" PSR
     */
    private void doTryUnstuck() {
        if (!game.getPhase().isMovement()) {
            return;
        }

        Report r;

        Iterator<Entity> stuckEntities = game.getSelectedEntities(Entity::isStuck);
        PilotingRollData rollTarget;
        while (stuckEntities.hasNext()) {
            Entity entity = stuckEntities.next();
            if (entity.getPosition() == null) {
                if (entity.isDeployed()) {
                    LogManager.getLogger().info("Entity #" + entity.getId() + " does not know its position.");
                } else { // If the Entity isn't deployed, then something goofy
                    // happened.  We'll just unstuck the Entity
                    entity.setStuck(false);
                    LogManager.getLogger().info("Entity #" + entity.getId() + " was stuck in a swamp, but not deployed. Stuck state reset");
                }
                continue;
            }
            rollTarget = entity.getBasePilotingRoll();
            entity.addPilotingModifierForTerrain(rollTarget);
            // apart from swamp & liquid magma, -1 modifier
            Hex hex = game.getBoard().getHex(entity.getPosition());
            hex.getUnstuckModifier(entity.getElevation(), rollTarget);
            // okay, print the info
            r = new Report(2340);
            r.subject = entity.getId();
            r.addDesc(entity);
            gameManager.addReport(r);

            // roll
            final Roll diceRoll = entity.getCrew().rollPilotingSkill();
            r = new Report(2190);
            r.subject = entity.getId();
            r.add(rollTarget.getValueAsString());
            r.add(rollTarget.getDesc());
            r.add(diceRoll);

            if (diceRoll.getIntValue() < rollTarget.getValue()) {
                r.choose(false);
            } else {
                r.choose(true);
                entity.setStuck(false);
                entity.setCanUnstickByJumping(false);
                entity.setElevation(0);
                gameManager.entityUpdate(entity.getId());
            }
            gameManager.addReport(r);
        }
    }

    /**
     * Called to what players can see what units. This is used to determine who
     * can see what in double blind reports.
     */
    protected void resolveWhatPlayersCanSeeWhatUnits() {
        List<ECMInfo> allECMInfo = null;
        if (game.getOptions().booleanOption(OptionsConstants.ADVANCED_TACOPS_SENSORS)) {
            allECMInfo = ComputeECM.computeAllEntitiesECMInfo(game
                    .getEntitiesVector());
        }
        Map<GameManager.EntityTargetPair, LosEffects> losCache = new HashMap<>();
        for (Entity entity : game.getEntitiesVector()) {
            // We are hidden once again!
            entity.clearSeenBy();
            entity.clearDetectedBy();
            // Handle visual spotting
            for (Player p : gameManager.whoCanSee(entity, false, losCache)) {
                entity.addBeenSeenBy(p);
            }
            // Handle detection by sensors
            for (Player p : gameManager.whoCanDetect(entity, allECMInfo, losCache)) {
                entity.addBeenDetectedBy(p);
            }
        }
    }

    /**
     * Called at the start and end of movement. Determines if an entity
     * has been detected and/or had a firing solution calculated
     */
    protected void detectSpacecraft() {
        // Don't bother if we're not in space or if the game option isn't on
        if (!game.getBoard().inSpace()
                || !game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_STRATOPS_ADVANCED_SENSORS)) {
            return;
        }

        //Now, run the detection rolls
        for (Entity detector : game.getEntitiesVector()) {
            //Don't process for invalid units
            //in the case of squadrons and transports, we want the 'host'
            //unit, not the component entities
            if (detector.getPosition() == null
                    || detector.isDestroyed()
                    || detector.isDoomed()
                    || detector.isOffBoard()
                    || detector.isPartOfFighterSquadron()
                    || detector.getTransportId() != Entity.NONE) {
                continue;
            }
            for (Entity target : game.getEntitiesVector()) {
                //Once a target is detected, we don't need to detect it again
                if (detector.hasSensorContactFor(target.getId())) {
                    continue;
                }
                //Don't process for invalid units
                //in the case of squadrons and transports, we want the 'host'
                //unit, not the component entities
                if (target.getPosition() == null
                        || target.isDestroyed()
                        || target.isDoomed()
                        || target.isOffBoard()
                        || target.isPartOfFighterSquadron()
                        || target.getTransportId() != Entity.NONE) {
                    continue;
                }
                // Only process for enemy units
                if (!detector.isEnemyOf(target)) {
                    continue;
                }
                //If we successfully detect the enemy, add it to the appropriate detector's sensor contacts list
                if (Compute.calcSensorContact(game, detector, target)) {
                    game.getEntity(detector.getId()).addSensorContact(target.getId());
                    //If detector is part of a C3 network, share the contact
                    if (detector.hasNavalC3()) {
                        for (Entity c3NetMate : game.getC3NetworkMembers(detector)) {
                            game.getEntity(c3NetMate.getId()).addSensorContact(target.getId());
                        }
                    }
                }
            }
        }
        //Now, run the firing solution calculations
        for (Entity detector : game.getEntitiesVector()) {
            //Don't process for invalid units
            //in the case of squadrons and transports, we want the 'host'
            //unit, not the component entities
            if (detector.getPosition() == null
                    || detector.isDestroyed()
                    || detector.isDoomed()
                    || detector.isOffBoard()
                    || detector.isPartOfFighterSquadron()
                    || detector.getTransportId() != Entity.NONE) {
                continue;
            }
            for (int targetId : detector.getSensorContacts()) {
                Entity target = game.getEntity(targetId);
                //if we already have a firing solution, no need to process a new one
                if (detector.hasFiringSolutionFor(targetId)) {
                    continue;
                }
                //Don't process for invalid units
                //in the case of squadrons and transports, we want the 'host'
                //unit, not the component entities
                if (target == null
                        || target.getPosition() == null
                        || target.isDestroyed()
                        || target.isDoomed()
                        || target.isOffBoard()
                        || target.isPartOfFighterSquadron()
                        || target.getTransportId() != Entity.NONE) {
                    continue;
                }
                // Only process for enemy units
                if (!detector.isEnemyOf(target)) {
                    continue;
                }
                //If we successfully lock up the enemy, add it to the appropriate detector's firing solutions list
                if (Compute.calcFiringSolution(game, detector, target)) {
                    game.getEntity(detector.getId()).addFiringSolution(targetId);
                }
            }
        }
    }

    /**
     * Checks to see if any entity takes enough damage that requires them to make a piloting roll
     */
    protected void checkForPSRFromDamage() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity entity = i.next();
            if (entity.canFall()) {
                if (entity.isAirborne()) {
                    // You can't fall over when you are combat dropping because you are already
                    // falling!
                    continue;
                }
                // If this mek has 20+ damage, add another roll to the list.
                // Hulldown meks ignore this rule, TO Errata
                int psrThreshold = 20;
                if ((((Mech) entity).getCockpitType() == Mech.COCKPIT_DUAL)
                        && entity.getCrew().hasDedicatedPilot()) {
                    psrThreshold = 30;
                }
                if ((entity.damageThisPhase >= psrThreshold) && !entity.isHullDown()) {
                    if (game.getOptions().booleanOption(OptionsConstants.ADVGRNDMOV_TACOPS_TAKING_DAMAGE)) {
                        PilotingRollData damPRD = new PilotingRollData(entity.getId());
                        int damMod = entity.damageThisPhase / psrThreshold;
                        damPRD.addModifier(damMod, (damMod * psrThreshold) + "+ damage");
                        int weightMod = 0;
                        if (game.getOptions().booleanOption(OptionsConstants.ADVGRNDMOV_TACOPS_PHYSICAL_PSR)) {
                            switch (entity.getWeightClass()) {
                                case EntityWeightClass.WEIGHT_LIGHT:
                                    weightMod = 1;
                                    break;
                                case EntityWeightClass.WEIGHT_MEDIUM:
                                    weightMod = 0;
                                    break;
                                case EntityWeightClass.WEIGHT_HEAVY:
                                    weightMod = -1;
                                    break;
                                case EntityWeightClass.WEIGHT_ASSAULT:
                                    weightMod = -2;
                                    break;
                            }
                            if (entity.isSuperHeavy()) {
                                weightMod = -4;
                            }
                            // the weight class PSR modifier is not cumulative
                            damPRD.addModifier(weightMod, "weight class modifier", false);
                        }

                        if (entity.hasQuirk(OptionsConstants.QUIRK_POS_EASY_PILOT)
                                && (entity.getCrew().getPiloting() > 3)) {
                            damPRD.addModifier(-1, "easy to pilot");
                        }
                        game.addPSR(damPRD);
                    } else {
                        PilotingRollData damPRD = new PilotingRollData(entity.getId(), 1,
                                psrThreshold + "+ damage");
                        if (entity.hasQuirk(OptionsConstants.QUIRK_POS_EASY_PILOT)
                                && (entity.getCrew().getPiloting() > 3)) {
                            damPRD.addModifier(-1, "easy to pilot");
                        }
                        game.addPSR(damPRD);
                    }
                }
            }
            if (entity.isAero() && entity.isAirborne() && !game.getBoard().inSpace()) {
                // if this aero has any damage, add another roll to the list.
                if (entity.damageThisPhase > 0) {
                    if (!game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_ATMOSPHERIC_CONTROL)) {
                        int damMod = entity.damageThisPhase / 20;
                        PilotingRollData damPRD = new PilotingRollData(entity.getId(), damMod,
                                entity.damageThisPhase + " damage +" + damMod);
                        if (entity.hasQuirk(OptionsConstants.QUIRK_POS_EASY_PILOT)
                                && (entity.getCrew().getPiloting() > 3)) {
                            damPRD.addModifier(-1, "easy to pilot");
                        }
                        game.addControlRoll(damPRD);
                    } else {
                        // was the damage threshold exceeded this round?
                        if (((IAero) entity).wasCritThresh()) {
                            PilotingRollData damThresh = new PilotingRollData(entity.getId(), 0,
                                    "damage threshold exceeded");
                            if (entity.hasQuirk(OptionsConstants.QUIRK_POS_EASY_PILOT)
                                    && (entity.getCrew().getPiloting() > 3)) {
                                damThresh.addModifier(-1, "easy to pilot");
                            }
                            game.addControlRoll(damThresh);
                        }
                    }
                }
            }
            // Airborne AirMechs that take 20+ damage make a control roll instead of a PSR.
            if ((entity instanceof LandAirMech) && entity.isAirborneVTOLorWIGE()
                    && (entity.damageThisPhase >= 20)) {
                PilotingRollData damPRD = new PilotingRollData(entity.getId());
                int damMod = entity.damageThisPhase / 20;
                damPRD.addModifier(damMod, (damMod * 20) + "+ damage");
                game.addControlRoll(damPRD);
            }
        }
    }

    /**
     * Iterates over all entities and gets rid of Narc pods attached to destroyed
     * or lost locations.
     */
    protected void cleanupDestroyedNarcPods() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            i.next().clearDestroyedNarcPods();
        }
    }

    protected void checkForFlawedCooling() {

        // If we're not using quirks, no need to do this check.
        if (!game.getOptions().booleanOption(OptionsConstants.ADVANCED_STRATOPS_QUIRKS)) {
            return;
        }

        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity entity = i.next();

            // Only applies to Mechs.
            if (!(entity instanceof Mech)) {
                continue;
            }

            // Check for existence of flawed cooling quirk.
            if (!entity.hasQuirk(OptionsConstants.QUIRK_NEG_FLAWED_COOLING)) {
                continue;
            }

            // Check for active Cooling Flaw
            if (((Mech) entity).isCoolingFlawActive()) {
                continue;
            }

            // Perform the check.
            if (entity.damageThisPhase >= 20) {
                gameManager.addReport(doFlawedCoolingCheck("20+ damage", entity));
            }
            if (entity.hasFallen()) {
                gameManager.addReport(doFlawedCoolingCheck("fall", entity));
            }
            if (entity.wasStruck()) {
                gameManager.addReport(doFlawedCoolingCheck("being struck", entity));
            }
            clearFlawedCoolingFlags(entity);
        }
    }

    /**
     * Checks to see if Flawed Cooling is triggered and generates a report of
     * the result.
     *
     * @param reason
     * @param entity
     * @return
     */
    private Vector<Report> doFlawedCoolingCheck(String reason, Entity entity) {
        Vector<Report> out = new Vector<>();
        Report r = new Report(9800);
        r.addDesc(entity);
        r.add(reason);
        Roll diceRoll = Compute.rollD6(2);
        r.add(diceRoll);
        out.add(r);

        if (diceRoll.getIntValue() >= 10) {
            Report s = new Report(9805);
            ((Mech) entity).setCoolingFlawActive(true);
            out.add(s);
        }

        return out;
    }

    private void clearFlawedCoolingFlags(Entity entity) {
        // If we're not using quirks, no need to do this check.
        if (!game.getOptions().booleanOption(OptionsConstants.ADVANCED_STRATOPS_QUIRKS)) {
            return;
        }
        // Only applies to Mechs.
        if (!(entity instanceof Mech)) {
            return;
        }

        // Check for existence of flawed cooling quirk.
        if (!entity.hasQuirk(OptionsConstants.QUIRK_NEG_FLAWED_COOLING)) {
            return;
        }
        entity.setFallen(false);
        entity.setStruck(false);
    }

    /**
     * Called during the weapons fire phase. Resolves anything other than
     * weapons fire that happens. Torso twists, for example.
     */
    protected void resolveAllButWeaponAttacks() {
        Vector<EntityAction> triggerPodActions = new Vector<>();
        // loop through actions and handle everything we expect except attacks
        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements(); ) {
            EntityAction ea = i.nextElement();
            Entity entity = game.getEntity(ea.getEntityId());
            if (ea instanceof TorsoTwistAction) {
                TorsoTwistAction tta = (TorsoTwistAction) ea;
                if (entity.canChangeSecondaryFacing()) {
                    entity.setSecondaryFacing(tta.getFacing());
                    entity.postProcessFacingChange();
                }
            } else if (ea instanceof FlipArmsAction) {
                FlipArmsAction faa = (FlipArmsAction) ea;
                entity.setArmsFlipped(faa.getIsFlipped());
            } else if (ea instanceof FindClubAction) {
                resolveFindClub(entity);
            } else if (ea instanceof UnjamAction) {
                resolveUnjam(entity);
            } else if (ea instanceof ClearMinefieldAction) {
                resolveClearMinefield(entity, ((ClearMinefieldAction) ea).getMinefield());
            } else if (ea instanceof TriggerAPPodAction) {
                TriggerAPPodAction tapa = (TriggerAPPodAction) ea;

                // Don't trigger the same pod twice.
                if (!triggerPodActions.contains(tapa)) {
                    triggerAPPod(entity, tapa.getPodId());
                    triggerPodActions.addElement(tapa);
                } else {
                    LogManager.getLogger().error("AP Pod #" + tapa.getPodId() + " on "
                            + entity.getDisplayName() + " was already triggered this round!!");
                }
            } else if (ea instanceof TriggerBPodAction) {
                TriggerBPodAction tba = (TriggerBPodAction) ea;

                // Don't trigger the same pod twice.
                if (!triggerPodActions.contains(tba)) {
                    triggerBPod(entity, tba.getPodId(), game.getEntity(tba.getTargetId()));
                    triggerPodActions.addElement(tba);
                } else {
                    LogManager.getLogger().error("B Pod #" + tba.getPodId() + " on "
                            + entity.getDisplayName() + " was already triggered this round!!");
                }
            } else if (ea instanceof SearchlightAttackAction) {
                SearchlightAttackAction saa = (SearchlightAttackAction) ea;
                gameManager.addReport(saa.resolveAction(game));
            } else if (ea instanceof UnjamTurretAction) {
                if (entity instanceof Tank) {
                    ((Tank) entity).unjamTurret(((Tank) entity).getLocTurret());
                    ((Tank) entity).unjamTurret(((Tank) entity).getLocTurret2());
                    Report r = new Report(3033);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    gameManager.addReport(r);
                } else {
                    LogManager.getLogger().error("Non-Tank tried to unjam turret");
                }
            } else if (ea instanceof RepairWeaponMalfunctionAction) {
                if (entity instanceof Tank) {
                    Mounted m = entity.getEquipment(((RepairWeaponMalfunctionAction) ea).getWeaponId());
                    m.setJammed(false);
                    ((Tank) entity).getJammedWeapons().remove(m);
                    Report r = new Report(3034);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.add(m.getName());
                    gameManager.addReport(r);
                } else {
                    LogManager.getLogger().error("Non-Tank tried to repair weapon malfunction");
                }
            } else if (ea instanceof DisengageAction) {
                MovePath path = new MovePath(game, entity);
                path.addStep(MovePath.MoveStepType.FLEE);
                gameManager.addReport(gameManager.processLeaveMap(path, false, -1));
            } else if (ea instanceof ActivateBloodStalkerAction) {
                ActivateBloodStalkerAction bloodStalkerAction = (ActivateBloodStalkerAction) ea;
                Entity target = game.getEntity(bloodStalkerAction.getTargetID());

                if ((entity != null) && (target != null)) {
                    game.getEntity(bloodStalkerAction.getEntityId())
                            .setBloodStalkerTarget(bloodStalkerAction.getTargetID());
                    Report r = new Report(10000);
                    r.subject = entity.getId();
                    r.add(entity.getDisplayName());
                    r.add(target.getDisplayName());
                    gameManager.addReport(r);
                }
            }
        }
    }

    /**
     * Trigger the indicated AP Pod of the entity.
     *
     * @param entity the <code>Entity</code> triggering the AP Pod.
     * @param podId  the <code>int</code> ID of the AP Pod.
     */
    private void triggerAPPod(Entity entity, int podId) {
        // Get the mount for this pod.
        Mounted mount = entity.getEquipment(podId);

        // Confirm that this is, indeed, an AP Pod.
        if (null == mount) {
            LogManager.getLogger().error("Expecting to find an AP Pod at " + podId + " on the unit, " + entity.getDisplayName()
                    + " but found NO equipment at all!!!");
            return;
        }
        EquipmentType equip = mount.getType();
        if (!(equip instanceof MiscType) || !equip.hasFlag(MiscType.F_AP_POD)) {
            LogManager.getLogger().error("Expecting to find an AP Pod at " + podId + " on the unit, "+ entity.getDisplayName()
                    + " but found " + equip.getName() + " instead!!!");
            return;
        }

        // Now confirm that the entity can trigger the pod.
        // Ignore the "used this round" flag.
        boolean oldFired = mount.isUsedThisRound();
        mount.setUsedThisRound(false);
        boolean canFire = mount.canFire();
        mount.setUsedThisRound(oldFired);
        if (!canFire) {
            LogManager.getLogger().error("Can not trigger the AP Pod at " + podId + " on the unit, "
                    + entity.getDisplayName() + "!!!");
            return;
        }

        Report r;

        // Mark the pod as fired and log the action.
        mount.setFired(true);
        r = new Report(3010);
        r.newlines = 0;
        r.subject = entity.getId();
        r.addDesc(entity);
        gameManager.addReport(r);

        // Walk through ALL entities in the triggering entity's hex.
        for (Entity target : game.getEntitiesVector(entity.getPosition())) {
            // Is this an unarmored infantry platoon?
            if (target.isConventionalInfantry()) {
                // Roll d6-1 for damage.
                final int damage = Math.max(1, Compute.d6() - 1);

                // Damage the platoon.
                gameManager.addReport(gameManager.damageEntity(target, new HitData(Infantry.LOC_INFANTRY), damage));

                // Damage from AP Pods is applied immediately.
                target.applyDamage();
            } // End target-is-unarmored

            // Nope, the target is immune.
            // Don't make a log entry for the triggering entity.
            else if (!entity.equals(target)) {
                r = new Report(3020);
                r.indent(2);
                r.subject = target.getId();
                r.addDesc(target);
                gameManager.addReport(r);
            }

        } // Check the next entity in the triggering entity's hex.
    }

    /**
     * Trigger the indicated B Pod of the entity.
     *
     * @param entity the <code>Entity</code> triggering the B Pod.
     * @param podId  the <code>int</code> ID of the B Pod.
     */
    private void triggerBPod(Entity entity, int podId, Entity target) {
        // Get the mount for this pod.
        Mounted mount = entity.getEquipment(podId);

        // Confirm that this is, indeed, an Anti-BA Pod.
        if (null == mount) {
            LogManager.getLogger().error("Expecting to find an B Pod at " + podId + " on the unit, "
                    + entity.getDisplayName() + " but found NO equipment at all!!!");
            return;
        }
        EquipmentType equip = mount.getType();
        if (!(equip instanceof WeaponType) || !equip.hasFlag(WeaponType.F_B_POD)) {
            LogManager.getLogger().error("Expecting to find an B Pod at " + podId + " on the unit, "
                    + entity.getDisplayName() + " but found " + equip.getName() + " instead!!!");
            return;
        }

        // Now confirm that the entity can trigger the pod.
        // Ignore the "used this round" flag.
        boolean oldFired = mount.isUsedThisRound();
        mount.setUsedThisRound(false);
        boolean canFire = mount.canFire();
        mount.setUsedThisRound(oldFired);
        if (!canFire) {
            LogManager.getLogger().error("Can not trigger the B Pod at " + podId + " on the unit, "
                    + entity.getDisplayName() + "!!!");
            return;
        }

        Report r;

        // Mark the pod as fired and log the action.
        mount.setFired(true);
        r = new Report(3011);
        r.newlines = 0;
        r.subject = entity.getId();
        r.addDesc(entity);
        gameManager.addReport(r);

        // Is this an unarmored infantry platoon?
        if (target.isConventionalInfantry()) {
            // Roll d6 for damage.
            final int damage = Compute.d6();

            // Damage the platoon.
            gameManager.addReport(gameManager.damageEntity(target, new HitData(Infantry.LOC_INFANTRY), damage));

            // Damage from AP Pods is applied immediately.
            target.applyDamage();
        } else if (target instanceof BattleArmor) {
            // 20 damage in 5 point clusters
            final int damage = 5;

            // Damage the squad.
            gameManager.addReport(gameManager.damageEntity(target, target.rollHitLocation(0, 0), damage));
            gameManager.addReport(gameManager.damageEntity(target, target.rollHitLocation(0, 0), damage));
            gameManager.addReport(gameManager.damageEntity(target, target.rollHitLocation(0, 0), damage));
            gameManager.addReport(gameManager.damageEntity(target, target.rollHitLocation(0, 0), damage));

            // Damage from B Pods is applied immediately.
            target.applyDamage();
        } else if (!entity.equals(target)) {
            // Nope, the target is immune.
            // Don't make a log entry for the triggering entity.
            r = new Report(3020);
            r.indent(2);
            r.subject = target.getId();
            r.addDesc(target);
            gameManager.addReport(r);
        }
    }

    private void resolveFindClub(Entity entity) {
        EquipmentType clubType = null;

        entity.setFindingClub(true);

        // Get the entity's current hex.
        Coords coords = entity.getPosition();
        Hex curHex = game.getBoard().getHex(coords);

        Report r;

        // Is there a blown off arm in the hex?
        if (curHex.terrainLevel(Terrains.ARMS) > 0) {
            clubType = EquipmentType.get(EquipmentTypeLookup.LIMB_CLUB);
            curHex.addTerrain(new Terrain(Terrains.ARMS, curHex.terrainLevel(Terrains.ARMS) - 1));
            gameManager.sendChangedHex(entity.getPosition());
            r = new Report(3035);
            r.subject = entity.getId();
            r.addDesc(entity);
            gameManager.addReport(r);
        }
        // Is there a blown off leg in the hex?
        else if (curHex.terrainLevel(Terrains.LEGS) > 0) {
            clubType = EquipmentType.get(EquipmentTypeLookup.LIMB_CLUB);
            curHex.addTerrain(new Terrain(Terrains.LEGS, curHex.terrainLevel(Terrains.LEGS) - 1));
            gameManager.sendChangedHex(entity.getPosition());
            r = new Report(3040);
            r.subject = entity.getId();
            r.addDesc(entity);
            gameManager.addReport(r);
        }

        // Is there the rubble of a medium, heavy,
        // or hardened building in the hex?
        else if (Building.LIGHT < curHex.terrainLevel(Terrains.RUBBLE)) {

            // Finding a club is not guaranteed. The chances are
            // based on the type of building that produced the
            // rubble.
            boolean found = false;
            int roll = Compute.d6(2);
            switch (curHex.terrainLevel(Terrains.RUBBLE)) {
                case Building.MEDIUM:
                    if (roll >= 7) {
                        found = true;
                    }
                    break;
                case Building.HEAVY:
                    if (roll >= 6) {
                        found = true;
                    }
                    break;
                case Building.HARDENED:
                    if (roll >= 5) {
                        found = true;
                    }
                    break;
                case Building.WALL:
                    if (roll >= 13) {
                        found = true;
                    }
                    break;
                default:
                    // we must be in ultra
                    if (roll >= 4) {
                        found = true;
                    }
            }

            // Let the player know if they found a club.
            if (found) {
                clubType = EquipmentType.get(EquipmentTypeLookup.GIRDER_CLUB);
                r = new Report(3045);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
            } else {
                // Sorry, no club for you.
                r = new Report(3050);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
            }
        }

        // Are there woods in the hex?
        else if (curHex.containsTerrain(Terrains.WOODS)
                || curHex.containsTerrain(Terrains.JUNGLE)) {
            clubType = EquipmentType.get(EquipmentTypeLookup.TREE_CLUB);
            r = new Report(3055);
            r.subject = entity.getId();
            r.addDesc(entity);
            gameManager.addReport(r);
        }

        // add the club
        try {
            if (clubType != null) {
                entity.addEquipment(clubType, Entity.LOC_NONE);
            }
        } catch (LocationFullException ex) {
            // unlikely...
            r = new Report(3060);
            r.subject = entity.getId();
            r.addDesc(entity);
            gameManager.addReport(r);
        }
    }

    /**
     * Resolve an Unjam Action object
     */
    private void resolveUnjam(Entity entity) {
        Report r;
        final int TN = entity.getCrew().getGunnery() + 3;
        if (game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_UNJAM_UAC)) {
            r = new Report(3026);
        } else {
            r = new Report(3025);
        }
        r.subject = entity.getId();
        r.addDesc(entity);
        gameManager.addReport(r);
        for (Mounted mounted : entity.getTotalWeaponList()) {
            if (mounted.isJammed() && !mounted.isDestroyed()) {
                WeaponType wtype = (WeaponType) mounted.getType();
                if (wtype.getAmmoType() == AmmoType.T_AC_ROTARY) {
                    Roll diceRoll = Compute.rollD6(2);
                    r = new Report(3030);
                    r.indent();
                    r.subject = entity.getId();
                    r.add(wtype.getName());
                    r.add(TN);
                    r.add(diceRoll);

                    if (diceRoll.getIntValue() >= TN) {
                        r.choose(true);
                        mounted.setJammed(false);
                    } else {
                        r.choose(false);
                    }
                    gameManager.addReport(r);
                }
                // Unofficial option to unjam UACs, ACs, and LACs like Rotary
                // Autocannons
                if (((wtype.getAmmoType() == AmmoType.T_AC_ULTRA)
                        || (wtype.getAmmoType() == AmmoType.T_AC_ULTRA_THB)
                        || (wtype.getAmmoType() == AmmoType.T_AC)
                        || (wtype.getAmmoType() == AmmoType.T_AC_IMP)
                        || (wtype.getAmmoType() == AmmoType.T_PAC)
                        || (wtype.getAmmoType() == AmmoType.T_LAC))
                        && game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_UNJAM_UAC)) {
                    Roll diceRoll = Compute.rollD6(2);
                    r = new Report(3030);
                    r.indent();
                    r.subject = entity.getId();
                    r.add(wtype.getName());
                    r.add(TN);
                    r.add(diceRoll);

                    if (diceRoll.getIntValue() >= TN) {
                        r.choose(true);
                        mounted.setJammed(false);
                    } else {
                        r.choose(false);
                    }
                    gameManager.addReport(r);
                }
            }
        }
    }

    private void resolveClearMinefield(Entity ent, Minefield mf) {

        if ((null == mf) || (null == ent) || ent.isDoomed()
                || ent.isDestroyed()) {
            return;
        }

        Coords pos = mf.getCoords();
        int clear = Minefield.CLEAR_NUMBER_INFANTRY;
        int boom = Minefield.CLEAR_NUMBER_INFANTRY_ACCIDENT;

        Report r = new Report(2245);
        // Does the entity has a minesweeper?
        if ((ent instanceof BattleArmor)) {
            BattleArmor ba = (BattleArmor) ent;
            String mcmName = BattleArmor.MANIPULATOR_TYPE_STRINGS
                    [BattleArmor.MANIPULATOR_BASIC_MINE_CLEARANCE];
            if (ba.getLeftManipulatorName().equals(mcmName)) {
                clear = Minefield.CLEAR_NUMBER_BA_SWEEPER;
                boom = Minefield.CLEAR_NUMBER_BA_SWEEPER_ACCIDENT;
                r = new Report(2246);
            }
        } else if (ent instanceof Infantry) { // Check Minesweeping Engineers
            Infantry inf = (Infantry) ent;
            if (inf.hasSpecialization(Infantry.MINE_ENGINEERS)) {
                clear = Minefield.CLEAR_NUMBER_INF_ENG;
                boom = Minefield.CLEAR_NUMBER_INF_ENG_ACCIDENT;
                r = new Report(2247);
            }
        }
        // mine clearing roll
        r.subject = ent.getId();
        r.add(ent.getShortName(), true);
        r.add(Minefield.getDisplayableName(mf.getType()));
        r.add(pos.getBoardNum(), true);
        gameManager.addReport(r);

        if (gameManager.clearMinefield(mf, ent, clear, boom, gameManager.getvPhaseReport())) {
            gameManager.removeMinefield(mf);
        }
        // some mines might have blown up
        gameManager.resetMines();

        gameManager.addNewLines();
    }

    /**
     * Called during the fire phase to resolve all (and only) weapon attacks
     */
    protected void resolveOnlyWeaponAttacks() {
        // loop through received attack actions, getting attack handlers
        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements(); ) {
            EntityAction ea = i.nextElement();
            if (ea instanceof WeaponAttackAction) {
                WeaponAttackAction waa = (WeaponAttackAction) ea;
                Entity ae = game.getEntity(waa.getEntityId());
                Mounted m = ae.getEquipment(waa.getWeaponId());
                Weapon w = (Weapon) m.getType();
                // Track attacks original target, for things like swarm LRMs
                waa.setOriginalTargetId(waa.getTargetId());
                waa.setOriginalTargetType(waa.getTargetType());
                AttackHandler ah = w.fire(waa, game, gameManager);
                if (ah != null) {
                    ah.setStrafing(waa.isStrafing());
                    ah.setStrafingFirstShot(waa.isStrafingFirstShot());
                    game.addAttack(ah);
                }
            }
        }
        // and clear the attacks Vector
        game.resetActions();
    }

    /**
     * Called at the beginning of each phase. Sets and resets any entity
     * parameters that need to be reset.
     */
    protected void resetEntityPhase(GamePhase phase) {
        // first, mark doomed entities as destroyed and flag them
        Vector<Entity> toRemove = new Vector<>(0, 10);

        for (Entity entity : game.getEntitiesVector()) {
            entity.newPhase(phase);
            if (entity.isDoomed()) {
                entity.setDestroyed(true);

                // Is this unit swarming somebody? Better let go before it's too late.
                final int swarmedId = entity.getSwarmTargetId();
                if (Entity.NONE != swarmedId) {
                    final Entity swarmed = game.getEntity(swarmedId);
                    swarmed.setSwarmAttackerId(Entity.NONE);
                    entity.setSwarmTargetId(Entity.NONE);
                    Report r = new Report(5165);
                    r.subject = swarmedId;
                    r.addDesc(swarmed);
                    gameManager.addReport(r);
                    gameManager.entityUpdate(swarmedId);
                }
            }

            if (entity.isDestroyed()) {
                if (game.getEntity(entity.getTransportId()) != null
                        && game.getEntity(entity.getTransportId()).isLargeCraft()) {
                    // Leaving destroyed entities in DropShip bays alone here
                } else {
                    toRemove.addElement(entity);
                }
            }
        }

        // actually remove all flagged entities
        for (Entity entity : toRemove) {
            int condition = IEntityRemovalConditions.REMOVE_SALVAGEABLE;
            if (!entity.isSalvage()) {
                condition = IEntityRemovalConditions.REMOVE_DEVASTATED;
            }

            gameManager.entityUpdate(entity.getId());
            game.removeEntity(entity.getId(), condition);
            gameManager.send(gameManager.createRemoveEntityPacket(entity.getId(), condition));
        }

        // do some housekeeping on all the remaining
        for (Entity entity : game.getEntitiesVector()) {
            entity.applyDamage();
            entity.reloadEmptyWeapons();

            // reset damage this phase
            // telemissiles need a record of damage last phase
            entity.damageThisRound += entity.damageThisPhase;
            entity.damageThisPhase = 0;
            entity.engineHitsThisPhase = 0;
            entity.rolledForEngineExplosion = false;
            entity.dodging = false;
            entity.setShutDownThisPhase(false);
            entity.setStartupThisPhase(false);

            // reset done to false
            if (phase.isDeployment()) {
                entity.setDone(!entity.shouldDeploy(game.getRoundCount()));
            } else {
                entity.setDone(false);
            }

            // reset spotlights
            // If deployment phase, set Searchlight state based on startSearchLightsOn;
            if (phase.isDeployment()) {
                boolean startSLOn = PreferenceManager.getClientPreferences().getStartSearchlightsOn()
                        && game.getPlanetaryConditions().isIlluminationEffective();
                entity.setSearchlightState(startSLOn);
                entity.setIlluminated(startSLOn);
            }
            entity.setIlluminated(false);
            entity.setUsedSearchlight(false);

            entity.setCarefulStand(false);

            // this flag is relevant only within the context of a single phase, but not between phases
            entity.setTurnInterrupted(false);

            if (entity instanceof MechWarrior) {
                ((MechWarrior) entity).setLanded(true);
            }
        }

        // flag deployed and doomed, but not destroyed out of game enities
        for (Entity entity : game.getOutOfGameEntitiesVector()) {
            if (entity.isDeployed() && entity.isDoomed() && !entity.isDestroyed()) {
                entity.setDestroyed(true);
            }
        }

        game.clearIlluminatedPositions();
        gameManager.send(new Packet(PacketCommand.CLEAR_ILLUM_HEXES));
    }

    /**
     * Checks each player to see if he has no entities, and if true, sets the
     * observer flag for that player. An exception is that there are no
     * observers during the lounge phase.
     */
    protected void checkForObservers() {
        for (Enumeration<Player> e = game.getPlayers(); e.hasMoreElements(); ) {
            Player p = e.nextElement();
            p.setObserver((!p.isGameMaster()) && (game.getEntitiesOwnedBy(p) < 1) && !game.getPhase().isLounge());
        }
    }

    /**
     * Marks ineligible entities as not ready for this phase
     */
    protected void setIneligible(GamePhase phase) {
        Vector<Entity> assistants = new Vector<>();
        boolean assistable = false;

        if (isPlayerForcedVictory()) {
            assistants.addAll(game.getEntitiesVector());
        } else {
            for (Entity entity : game.getEntitiesVector()) {
                if (entity.isEligibleFor(phase)) {
                    assistable = true;
                } else {
                    assistants.addElement(entity);
                }
            }
        }
        for (Entity assistant : assistants) {
            if (!assistable || !assistant.canAssist(phase)) {
                assistant.setDone(true);
            }
        }
    }

    private boolean isPlayerForcedVictory() {
        // check game options
        if (!game.getOptions().booleanOption(OptionsConstants.VICTORY_SKIP_FORCED_VICTORY)) {
            return false;
        }

        if (!game.isForceVictory()) {
            return false;
        }

        for (Player player : game.getPlayersVector()) {
            if ((player.getId() == game.getVictoryPlayerId()) || ((player.getTeam() == game.getVictoryTeam())
                    && (game.getVictoryTeam() != Player.TEAM_NONE))) {
                continue;
            }

            if (!player.admitsDefeat()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Deploys elligible offboard entities.
     */
    protected void deployOffBoardEntities() {
        // place off board entities actually off-board
        Iterator<Entity> entities = game.getEntities();
        while (entities.hasNext()) {
            Entity en = entities.next();
            if (en.isOffBoard() && !en.isDeployed()) {
                en.deployOffBoard(game.getRoundCount());
            }
        }
    }
}
