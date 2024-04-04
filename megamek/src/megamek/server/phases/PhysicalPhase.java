package megamek.server.phases;

import megamek.common.*;
import megamek.common.actions.*;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Optional;
import java.util.Vector;

public class PhysicalPhase extends AbstractGamePhase{
    public PhysicalPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        resolveWhatPlayersCanSeeWhatUnits();
        resolvePhysicalAttacks();
        gameManager.applyBuildingDamage();
        checkForPSRFromDamage();
        gameManager.addReport(gameManager.resolvePilotingRolls());
        resolveSinkVees();
        cleanupDestroyedNarcPods();
        checkForFlawedCooling();
        checkForChainWhipGrappleChecks();
        // check phase report
        GamePhase next;
        if (gameManager.getvPhaseReport().size() > 1) {
            game.addReports(gameManager.getvPhaseReport());
            next = GamePhase.PHYSICAL_REPORT;
        } else {
            // just the header, so we'll add the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            game.addReports(gameManager.getvPhaseReport());
            gameManager.sendReport();
            next = GamePhase.END;
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
        fightPhasePrepareMethod(GamePhase.PHYSICAL);
    }

    /**
     * Handle all physical attacks for the round
     */
    private void resolvePhysicalAttacks() {
        // Physical phase header
        gameManager.addReport(new Report(4000, Report.PUBLIC));

        // add any pending charges
        for (Enumeration<AttackAction> i = game.getCharges(); i.hasMoreElements(); ) {
            game.addAction(i.nextElement());
        }
        game.resetCharges();

        // add any pending rams
        for (Enumeration<AttackAction> i = game.getRams(); i.hasMoreElements(); ) {
            game.addAction(i.nextElement());
        }
        game.resetRams();

        // add any pending Tele Missile Attacks
        for (Enumeration<AttackAction> i = game.getTeleMissileAttacks(); i.hasMoreElements(); ) {
            game.addAction(i.nextElement());
        }
        game.resetTeleMissileAttacks();

        // remove any duplicate attack declarations
        cleanupPhysicalAttacks();

        // loop thru received attack actions
        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements(); ) {
            Object o = i.nextElement();
            // verify that the attacker is still active
            AttackAction aa = (AttackAction) o;
            if (!game.getEntity(aa.getEntityId()).isActive()
                    && !(o instanceof DfaAttackAction)) {
                continue;
            }
            AbstractAttackAction aaa = (AbstractAttackAction) o;
            // do searchlights immediately
            if (aaa instanceof SearchlightAttackAction) {
                SearchlightAttackAction saa = (SearchlightAttackAction) aaa;
                gameManager.addReport(saa.resolveAction(game));
            } else {
                gameManager.addPhysicalResult(preTreatPhysicalAttack(aaa));
            }
        }
        int cen = Entity.NONE;
        for (PhysicalResult pr : gameManager.getPhysicalResults()) {
            gameManager.resolvePhysicalAttack(pr, cen);
            cen = pr.aaa.getEntityId();
        }
        gameManager.clearPhysicalResults();
    }

    /**
     * Cleans up the attack declarations for the physical phase by removing all
     * attacks past the first for any one mech. Also clears out attacks by dead
     * or disabled mechs.
     */
    private void cleanupPhysicalAttacks() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            Entity entity = i.next();
            removeDuplicateAttacks(entity.getId());
        }
        removeDeadAttacks();
    }

    /**
     * Removes any actions in the attack queue beyond the first by the specified
     * entity, unless that entity has melee master in which case it allows two
     * attacks.
     */
    private void removeDuplicateAttacks(int entityId) {
        int allowed = 1;
        Entity en = game.getEntity(entityId);
        if (null != en) {
            allowed = en.getAllowedPhysicalAttacks();
        }
        Vector<EntityAction> toKeep = new Vector<>();

        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements(); ) {
            EntityAction action = i.nextElement();
            if (action.getEntityId() != entityId) {
                toKeep.addElement(action);
            } else if (allowed > 0) {
                toKeep.addElement(action);
                if (!(action instanceof SearchlightAttackAction)) {
                    allowed--;
                }
            } else {
                LogManager.getLogger().error("Removing duplicate phys attack for id#" + entityId
                        + "\n\t\taction was " + action);
            }
        }

        // reset actions and re-add valid elements
        game.resetActions();
        for (EntityAction entityAction : toKeep) {
            game.addAction(entityAction);
        }
    }

    /**
     * Removes all attacks by any dead entities. It does this by going through
     * all the attacks and only keeping ones from active entities. DFAs are kept
     * even if the pilot is unconscious, so that he can fail.
     */
    private void removeDeadAttacks() {
        Vector<EntityAction> toKeep = new Vector<>(game.actionsSize());

        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements(); ) {
            EntityAction action = i.nextElement();
            Entity entity = game.getEntity(action.getEntityId());
            if ((entity != null) && !entity.isDestroyed()
                    && (entity.isActive() || (action instanceof DfaAttackAction))) {
                toKeep.addElement(action);
            }
        }

        // reset actions and re-add valid elements
        game.resetActions();
        for (EntityAction entityAction : toKeep) {
            game.addAction(entityAction);
        }
    }

    /**
     * destroy all wheeled and tracked Tanks that got displaced into water
     */
    private void resolveSinkVees() {
        Iterator<Entity> sinkableTanks = game.getSelectedEntities(entity -> {
            if (entity.isOffBoard() || (entity.getPosition() == null)
                    || !(entity instanceof Tank)) {
                return false;
            }
            final Hex hex = game.getBoard().getHex(entity.getPosition());
            final boolean onBridge = (hex.terrainLevel(Terrains.BRIDGE) > 0)
                    && (entity.getElevation() == hex.terrainLevel(Terrains.BRIDGE_ELEV));
            return ((entity.getMovementMode() == EntityMovementMode.TRACKED)
                    || (entity.getMovementMode() == EntityMovementMode.WHEELED)
                    || ((entity.getMovementMode() == EntityMovementMode.HOVER)))
                    && entity.isImmobile() && (hex.terrainLevel(Terrains.WATER) > 0)
                    && !onBridge && !(entity.hasWorkingMisc(MiscType.F_FULLY_AMPHIBIOUS))
                    && !(entity.hasWorkingMisc(MiscType.F_FLOTATION_HULL));
        });
        while (sinkableTanks.hasNext()) {
            Entity e = sinkableTanks.next();
            gameManager.addReport(gameManager.destroyEntity(e, "a watery grave", false));
        }
    }

    /**
     * pre-treats a physical attack
     *
     * @param aaa The <code>AbstractAttackAction</code> of the physical attack
     *            to pre-treat
     * @return The <code>PhysicalResult</code> of that action, including
     * possible damage.
     */
    private PhysicalResult preTreatPhysicalAttack(AbstractAttackAction aaa) {
        final Entity ae = game.getEntity(aaa.getEntityId());
        int damage = 0;
        PhysicalResult pr = new PhysicalResult();
        ToHitData toHit = new ToHitData();
        if (aaa instanceof PhysicalAttackAction && ae.getCrew() != null) {
            pr.roll = ae.getCrew().rollPilotingSkill();
        } else {
            pr.roll = Compute.rollD6(2);
        }
        pr.aaa = aaa;
        if (aaa instanceof BrushOffAttackAction) {
            BrushOffAttackAction baa = (BrushOffAttackAction) aaa;
            int arm = baa.getArm();
            baa.setArm(BrushOffAttackAction.LEFT);
            toHit = BrushOffAttackAction.toHit(game, aaa.getEntityId(),
                    aaa.getTarget(game), BrushOffAttackAction.LEFT);
            baa.setArm(BrushOffAttackAction.RIGHT);
            pr.toHitRight = BrushOffAttackAction.toHit(game, aaa.getEntityId(),
                    aaa.getTarget(game), BrushOffAttackAction.RIGHT);
            damage = BrushOffAttackAction.getDamageFor(ae, BrushOffAttackAction.LEFT);
            pr.damageRight = BrushOffAttackAction.getDamageFor(ae, BrushOffAttackAction.RIGHT);
            baa.setArm(arm);
            if (ae.getCrew() != null) {
                pr.rollRight = ae.getCrew().rollPilotingSkill();
            } else {
                pr.rollRight = Compute.rollD6(2);
            }
        } else if (aaa instanceof ChargeAttackAction) {
            ChargeAttackAction caa = (ChargeAttackAction) aaa;
            toHit = caa.toHit(game);
            Entity target = (Entity) caa.getTarget(game);

            if (target != null ) {
                if (caa.getTarget(game) instanceof Entity) {
                    damage = ChargeAttackAction.getDamageFor(ae, target, game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_TACOPS_CHARGE_DAMAGE), toHit.getMoS());
                } else {
                    damage = ChargeAttackAction.getDamageFor(ae);
                }
            }
            else {
                damage = 0;
            }
        } else if (aaa instanceof AirmechRamAttackAction) {
            AirmechRamAttackAction raa = (AirmechRamAttackAction) aaa;
            toHit = raa.toHit(game);
            damage = AirmechRamAttackAction.getDamageFor(ae);
        } else if (aaa instanceof ClubAttackAction) {
            ClubAttackAction caa = (ClubAttackAction) aaa;
            toHit = caa.toHit(game);
            damage = ClubAttackAction.getDamageFor(ae, caa.getClub(),
                    caa.getTarget(game).isConventionalInfantry(), caa.isZweihandering());
            if (caa.getTargetType() == Targetable.TYPE_BUILDING) {
                EquipmentType clubType = caa.getClub().getType();
                if (clubType.hasSubType(MiscType.S_BACKHOE)
                        || clubType.hasSubType(MiscType.S_CHAINSAW)
                        || clubType.hasSubType(MiscType.S_MINING_DRILL)
                        || clubType.hasSubType(MiscType.S_PILE_DRIVER)) {
                    damage += Compute.d6(1);
                } else if (clubType.hasSubType(MiscType.S_DUAL_SAW)) {
                    damage += Compute.d6(2);
                } else if (clubType.hasSubType(MiscType.S_ROCK_CUTTER)) {
                    damage += Compute.d6(3);
                }
                else if (clubType.hasSubType(MiscType.S_WRECKING_BALL)) {
                    damage += Compute.d6(4);
                }
            }
        } else if (aaa instanceof DfaAttackAction) {
            DfaAttackAction daa = (DfaAttackAction) aaa;
            toHit = daa.toHit(game);
            Entity target = (Entity) daa.getTarget(game);

            if (target != null) {
                damage = DfaAttackAction.getDamageFor(ae, daa.getTarget(game).isConventionalInfantry());
            }
            else {
                damage = 0;
            }
        } else if (aaa instanceof KickAttackAction) {
            KickAttackAction kaa = (KickAttackAction) aaa;
            toHit = kaa.toHit(game);
            damage = KickAttackAction.getDamageFor(ae, kaa.getLeg(),
                    kaa.getTarget(game).isConventionalInfantry());
        } else if (aaa instanceof ProtomechPhysicalAttackAction) {
            ProtomechPhysicalAttackAction paa = (ProtomechPhysicalAttackAction) aaa;
            toHit = paa.toHit(game);
            damage = ProtomechPhysicalAttackAction.getDamageFor(ae, paa.getTarget(game));
        } else if (aaa instanceof PunchAttackAction) {
            PunchAttackAction paa = (PunchAttackAction) aaa;
            int arm = paa.getArm();
            int damageRight;
            paa.setArm(PunchAttackAction.LEFT);
            toHit = paa.toHit(game);
            paa.setArm(PunchAttackAction.RIGHT);
            ToHitData toHitRight = paa.toHit(game);
            damage = PunchAttackAction.getDamageFor(ae, PunchAttackAction.LEFT,
                    paa.getTarget(game).isConventionalInfantry(), paa.isZweihandering());
            damageRight = PunchAttackAction.getDamageFor(ae, PunchAttackAction.RIGHT,
                    paa.getTarget(game).isConventionalInfantry(), paa.isZweihandering());
            paa.setArm(arm);
            // If we're punching while prone (at a Tank,
            // duh), then we can only use one arm.
            if (ae.isProne()) {
                double oddsLeft = Compute.oddsAbove(toHit.getValue(),
                        ae.hasAbility(OptionsConstants.PILOT_APTITUDE_PILOTING));
                double oddsRight = Compute.oddsAbove(toHitRight.getValue(),
                        ae.hasAbility(OptionsConstants.PILOT_APTITUDE_PILOTING));
                // Use the best attack.
                if ((oddsLeft * damage) > (oddsRight * damageRight)) {
                    paa.setArm(PunchAttackAction.LEFT);
                } else {
                    paa.setArm(PunchAttackAction.RIGHT);
                }
            }
            pr.damageRight = damageRight;
            pr.toHitRight = toHitRight;
            if (ae.getCrew() != null) {
                pr.rollRight = ae.getCrew().rollPilotingSkill();
            } else {
                pr.rollRight = Compute.rollD6(2);
            }
        } else if (aaa instanceof PushAttackAction) {
            PushAttackAction paa = (PushAttackAction) aaa;
            toHit = paa.toHit(game);
        } else if (aaa instanceof TripAttackAction) {
            TripAttackAction paa = (TripAttackAction) aaa;
            toHit = paa.toHit(game);
        } else if (aaa instanceof LayExplosivesAttackAction) {
            LayExplosivesAttackAction leaa = (LayExplosivesAttackAction) aaa;
            toHit = leaa.toHit(game);
            damage = LayExplosivesAttackAction.getDamageFor(ae);
        } else if (aaa instanceof ThrashAttackAction) {
            ThrashAttackAction taa = (ThrashAttackAction) aaa;
            toHit = taa.toHit(game);
            damage = ThrashAttackAction.getDamageFor(ae);
        } else if (aaa instanceof JumpJetAttackAction) {
            JumpJetAttackAction jaa = (JumpJetAttackAction) aaa;
            toHit = jaa.toHit(game);
            if (jaa.getLeg() == JumpJetAttackAction.BOTH) {
                damage = JumpJetAttackAction.getDamageFor(ae, JumpJetAttackAction.LEFT);
                pr.damageRight = JumpJetAttackAction.getDamageFor(ae, JumpJetAttackAction.LEFT);
            } else {
                damage = JumpJetAttackAction.getDamageFor(ae, jaa.getLeg());
                pr.damageRight = 0;
            }
            ae.heatBuildup += (damage + pr.damageRight) / 3;
        } else if (aaa instanceof GrappleAttackAction) {
            GrappleAttackAction taa = (GrappleAttackAction) aaa;
            toHit = taa.toHit(game);
        } else if (aaa instanceof BreakGrappleAttackAction) {
            BreakGrappleAttackAction taa = (BreakGrappleAttackAction) aaa;
            toHit = taa.toHit(game);
        } else if (aaa instanceof RamAttackAction) {
            RamAttackAction raa = (RamAttackAction) aaa;
            toHit = raa.toHit(game);
            damage = RamAttackAction.getDamageFor((IAero) ae, (Entity) aaa.getTarget(game));
        } else if (aaa instanceof TeleMissileAttackAction) {
            TeleMissileAttackAction taa = (TeleMissileAttackAction) aaa;
            assignTeleMissileAMS(taa);
            taa.calcCounterAV(game, taa.getTarget(game));
            toHit = taa.toHit(game);
            damage = TeleMissileAttackAction.getDamageFor(ae);
        } else if (aaa instanceof BAVibroClawAttackAction) {
            BAVibroClawAttackAction bvca = (BAVibroClawAttackAction) aaa;
            toHit = bvca.toHit(game);
            damage = BAVibroClawAttackAction.getDamageFor(ae);
        }
        pr.toHit = toHit;
        pr.damage = damage;
        return pr;
    }

    /**
     * Determine which telemissile attack actions could be affected by AMS, and assign AMS to those
     * attacks.
     */
    private void assignTeleMissileAMS(final TeleMissileAttackAction taa) {
        final Entity target = (taa.getTargetType() == Targetable.TYPE_ENTITY)
                ? (Entity) taa.getTarget(game) : null;

        // If a telemissile is still on the board and its original target is not, just return.
        if (target == null) {
            LogManager.getLogger().info("Telemissile has no target. AMS not assigned.");
            return;
        }

        target.assignTMAMS(taa);
    }

    /**
     * For chain whip grapples, a roll needs to be made at the end of the
     * physical phase to maintain the grapple.
     */
    private void checkForChainWhipGrappleChecks() {
        for (Entity ae : game.getEntitiesVector()) {
            if ((ae.getGrappled() != Entity.NONE) && ae.isChainWhipGrappled()
                    && ae.isGrappleAttacker() && !ae.isGrappledThisRound()) {
                Entity te = game.getEntity(ae.getGrappled());
                ToHitData grappleHit = GrappleAttackAction.toHit(game,
                        ae.getId(), te, ae.getGrappleSide(), true);
                Roll diceRoll = Compute.rollD6(2);

                Report r = new Report(4317);
                r.subject = ae.getId();
                r.indent();
                r.addDesc(ae);
                r.addDesc(te);
                r.newlines = 0;
                gameManager.addReport(r);

                if (grappleHit.getValue() == TargetRoll.IMPOSSIBLE) {
                    r = new Report(4300);
                    r.subject = ae.getId();
                    r.add(grappleHit.getDesc());
                    gameManager.addReport(r);
                    return;
                }

                // report the roll
                r = new Report(4025);
                r.subject = ae.getId();
                r.add(grappleHit);
                r.add(diceRoll);
                r.newlines = 0;
                gameManager.addReport(r);

                // do we hit?
                if (diceRoll.getIntValue() >= grappleHit.getValue()) {
                    // hit
                    r = new Report(4040);
                    r.subject = ae.getId();
                    gameManager.addReport(r);
                    // Nothing else to do
                    return;
                }

                // miss
                r = new Report(4035);
                r.subject = ae.getId();
                gameManager.addReport(r);

                // Need to break grapple
                ae.setGrappled(Entity.NONE, false);
                te.setGrappled(Entity.NONE, false);
            }
        }
    }
}
