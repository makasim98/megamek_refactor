package megamek.server.phases;

import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;

import java.util.Iterator;
import java.util.Optional;
import java.util.Vector;

public class FiringPhase extends AbstractGamePhase{
    public FiringPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        // write Weapon Attack Phase header
        gameManager.addReport(new Report(3000, Report.PUBLIC));
        resolveWhatPlayersCanSeeWhatUnits();
        resolveAllButWeaponAttacks();
        resolveSelfDestructions();
        reportGhostTargetRolls();
        reportLargeCraftECCMRolls();
        resolveOnlyWeaponAttacks();
        gameManager.assignAMS();
        gameManager.handleAttacks(false);
        resolveScheduledNukes();
        gameManager.applyBuildingDamage();
        checkForPSRFromDamage();
        cleanupDestroyedNarcPods();
        gameManager.addReport(gameManager.resolvePilotingRolls());
        checkForFlawedCooling();
        // check phase report
        GamePhase next;
        if (gameManager.getvPhaseReport().size() > 1) {
            game.addReports(gameManager.getvPhaseReport());
            next = GamePhase.FIRING_REPORT;
        } else {
            // just the header, so we'll add the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            gameManager.sendReport();
            game.addReports(gameManager.getvPhaseReport());
            next = GamePhase.PHYSICAL;
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
        fightPhasePrepareMethod(GamePhase.FIRING);
    }

    /*
     * Called during the weapons firing phase to initiate self destruction.
     */
    private void resolveSelfDestructions() {
        Vector<Report> vDesc = new Vector<>();
        Report r;
        for (Entity e : game.getEntitiesVector()) {
            if (e.getSelfDestructInitiated() && e.hasEngine()) {
                r = new Report(6166, Report.PUBLIC);
                int target = e.getCrew().getPiloting();
                Roll diceRoll = e.getCrew().rollPilotingSkill();
                r.subject = e.getId();
                r.addDesc(e);
                r.indent();
                r.add(target);
                r.add(diceRoll);
                r.choose(diceRoll.getIntValue() >= target);
                vDesc.add(r);

                // Blow it up...
                if (diceRoll.getIntValue() >= target) {
                    int engineRating = e.getEngine().getRating();
                    r = new Report(5400, Report.PUBLIC);
                    r.subject = e.getId();
                    r.indent(2);
                    vDesc.add(r);

                    if (e instanceof Mech) {
                        Mech mech = (Mech) e;
                        if (mech.isAutoEject()
                                && (!game.getOptions().booleanOption(
                                OptionsConstants.RPG_CONDITIONAL_EJECTION) || (game
                                .getOptions().booleanOption(
                                        OptionsConstants.RPG_CONDITIONAL_EJECTION) && mech
                                .isCondEjectEngine()))) {
                            vDesc.addAll(gameManager.ejectEntity(e, true));
                        }
                    }
                    e.setSelfDestructedThisTurn(true);
                    gameManager.doFusionEngineExplosion(engineRating, e.getPosition(),
                            vDesc, null);
                    Report.addNewline(vDesc);
                    r = new Report(5410, Report.PUBLIC);
                    r.subject = e.getId();
                    r.indent(2);
                    Report.addNewline(vDesc);
                    vDesc.add(r);
                }
                e.setSelfDestructInitiated(false);
            }
        }
        gameManager.addReport(vDesc);
    }

    private void reportGhostTargetRolls() {
        // run through an enumeration of deployed game entities. If they have
        // ghost targets, then check the roll
        // and report it
        Report r;
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            Entity ent = e.next();
            if (ent.isDeployed() && ent.hasGhostTargets(false)) {
                r = new Report(3630);
                r.subject = ent.getId();
                r.addDesc(ent);
                // Ghost target mod is +3 per errata
                int target = ent.getCrew().getPiloting() + 3;
                if (ent.hasETypeFlag(Entity.ETYPE_PROTOMECH)) {
                    target = ent.getCrew().getGunnery() + 3;
                }
                r.add(target);
                r.add(ent.getGhostTargetRoll());
                if (ent.getGhostTargetRoll().getIntValue() >= target) {
                    r.choose(true);
                } else {
                    r.choose(false);
                }
                gameManager.addReport(r);
            }
        }
        gameManager.addNewLines();
    }

    private void reportLargeCraftECCMRolls() {
        // run through an enumeration of deployed game entities. If they are
        // large craft in space, then check the roll
        // and report it
        if (!game.getBoard().inSpace()
                || !game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_STRATOPS_ECM)) {
            return;
        }
        Report r;
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            Entity ent = e.next();
            if (ent.isDeployed() && ent.isLargeCraft()) {
                r = new Report(3635);
                r.subject = ent.getId();
                r.addDesc(ent);
                int target = ((Aero) ent).getECCMTarget();
                int roll = ((Aero) ent).getECCMRoll();
                r.add(roll);
                r.add(target);
                int mod = ((Aero) ent).getECCMBonus();
                r.add(mod);
                gameManager.addReport(r);
            }
        }
    }

    /**
     * explode any scheduled nukes
     */
    private void resolveScheduledNukes() {
        for (int[] nuke : gameManager.getScheduledNukes()) {
            if (nuke.length == 3) {
                gameManager.doNuclearExplosion(new Coords(nuke[0] - 1, nuke[1] - 1), nuke[2],
                        gameManager.getvPhaseReport());
            }
            if (nuke.length == 6) {
                gameManager.doNuclearExplosion(new Coords(nuke[0] - 1, nuke[1] - 1), nuke[2], nuke[3],
                        nuke[4], nuke[5], gameManager.getvPhaseReport());
            }
        }
        gameManager.clearScheduledNukes();
    }
}
