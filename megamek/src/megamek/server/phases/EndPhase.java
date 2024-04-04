package megamek.server.phases;

import megamek.MMConstants;
import megamek.client.ui.swing.GUIPreferences;
import megamek.client.ui.swing.tooltip.UnitToolTip;
import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.common.weapons.DamageType;
import megamek.server.DynamicTerrainProcessor;
import megamek.server.GameManager;
import megamek.server.ServerHelper;
import org.apache.logging.log4j.LogManager;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class EndPhase extends AbstractGamePhase{
    public EndPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        // remove any entities that died in the heat/end phase before
        // checking for victory
        resetEntityPhase(GamePhase.END);
        boolean victory = gameManager.victory(); // note this may add reports
        // check phase report
        // HACK: hardcoded message ID check
        GamePhase next;
        Vector<Report> vPhaseReport = gameManager.getvPhaseReport();
        if ((vPhaseReport.size() > 3) || ((vPhaseReport.size() > 1)
                && (vPhaseReport.elementAt(1).messageId != 1205))) {
            game.addReports(vPhaseReport);
            next = GamePhase.END_REPORT;
        } else {
            // just the heat and end headers, so we'll add
            // the <nothing> label
            gameManager.addReport(new Report(1205, Report.PUBLIC));
            game.addReports(vPhaseReport);
            gameManager.sendReport();
            next = victory ? GamePhase.VICTORY : GamePhase.INITIATIVE;
        }
        // Decrement the ASEWAffected counter
        decrementASEWTurns();
        return Optional.of(next);
    }

    @Override
    public void preparePhase() {
        super.preparePhase();
        resetEntityPhase(GamePhase.END);
        gameManager.clearReports();
        resolveHeat();
        if (game.getPlanetaryConditions().isSandBlowing()
                && (game.getPlanetaryConditions().getWindStrength() > PlanetaryConditions.WI_LIGHT_GALE)) {
            gameManager.addReport(resolveBlowingSandDamage());
        }
        gameManager.addReport(resolveControlRolls());
        gameManager.addReport(checkForTraitors());
        // write End Phase header
        gameManager.addReport(new Report(5005, Report.PUBLIC));
        gameManager.addReport(resolveInternalBombHits());
        checkLayExplosives();
        resolveHarJelRepairs();
        resolveEmergencyCoolantSystem();
        checkForSuffocation();
        game.getPlanetaryConditions().determineWind();
        gameManager.send(gameManager.createPlanetaryConditionsPacket());

        gameManager.applyBuildingDamage();
        gameManager.addReport(game.ageFlares());
        gameManager.send(gameManager.createFlarePacket());
        resolveAmmoDumps();
        resolveCrewWakeUp();
        resolveConsoleCrewSwaps();
        resolveSelfDestruct();
        resolveShutdownCrashes();
        checkForIndustrialEndOfTurn();
        resolveMechWarriorPickUp();
        resolveVeeINarcPodRemoval();
        resolveFortify();

        entityStatusReport();

        // Moved this to the very end because it makes it difficult to see
        // more important updates when you have 300+ messages of smoke filling
        // whatever hex. Please don't move it above the other things again.
        // Thanks! Ralgith - 2018/03/15
        gameManager.clearHexUpdateSet();
        for (DynamicTerrainProcessor tp : gameManager.getTerrainProcessors()) {
            tp.doEndPhaseChanges(gameManager.getvPhaseReport());
        }
        gameManager.sendChangedHexes(gameManager.getHexUpdateSet());

        checkForObservers();
        gameManager.transmitAllPlayerUpdates();
        gameManager.entityAllUpdate();
    }

    /**
     * Called during the end phase. Checks each entity for ASEW effects counters and decrements them by 1 if greater than 0
     */
    private void decrementASEWTurns() {
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            final Entity entity = e.next();
            // Decrement ASEW effects
            if ((entity.getEntityType() & Entity.ETYPE_DROPSHIP) == Entity.ETYPE_DROPSHIP) {
                Dropship d = (Dropship) entity;
                for (int loc = 0; loc < d.locations(); loc++) {
                    if (d.getASEWAffected(loc) > 0) {
                        d.setASEWAffected(loc, d.getASEWAffected(loc) - 1);
                    }
                }
            } else if ((entity.getEntityType() & Entity.ETYPE_JUMPSHIP) != 0) {
                Jumpship j = (Jumpship) entity;
                for (int loc = 0; loc < j.locations(); loc++) {
                    if (j.getASEWAffected(loc) > 0) {
                        j.setASEWAffected(loc, j.getASEWAffected(loc) - 1);
                    }
                }
            } else {
                if (entity.getASEWAffected() > 0) {
                    entity.setASEWAffected(entity.getASEWAffected() - 1);
                }
            }
        }
    }

    /**
     * Each mech sinks the amount of heat appropriate to its current heat
     * capacity.
     */
    private void resolveHeat() {
        Report r;
        // Heat phase header
        gameManager.addReport(new Report(5000, Report.PUBLIC));
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            Entity entity = i.next();
            if ((null == entity.getPosition()) && !entity.isAero()) {
                continue;
            }
            Hex entityHex = game.getBoard().getHex(entity.getPosition());

            int hotDogMod = 0;
            if (entity.hasAbility(OptionsConstants.PILOT_HOT_DOG)) {
                hotDogMod = 1;
            }
            if (entity.getTaserInterferenceHeat()) {
                entity.heatBuildup += 5;
            }
            if (entity.hasDamagedRHS() && entity.weaponFired()) {
                entity.heatBuildup += 1;
            }
            if ((entity instanceof Mech) && ((Mech) entity).hasDamagedCoolantSystem() && entity.weaponFired()) {
                entity.heatBuildup += 1;
            }

            int radicalHSBonus = 0;
            Vector<Report> rhsReports = new Vector<>();
            Vector<Report> heatEffectsReports = new Vector<>();
            if (entity.hasActivatedRadicalHS()) {
                if (entity instanceof Mech) {
                    radicalHSBonus = ((Mech) entity).getActiveSinks();
                } else if (entity instanceof Aero) {
                    radicalHSBonus = ((Aero) entity).getHeatSinks();
                } else {
                    LogManager.getLogger().error("Radical heat sinks mounted on non-mech, non-aero Entity!");
                }

                // RHS activation report
                r = new Report(5540);
                r.subject = entity.getId();
                r.indent();
                r.addDesc(entity);
                r.add(radicalHSBonus);
                rhsReports.add(r);

                Roll diceRoll = Compute.rollD6(2);
                entity.setConsecutiveRHSUses(entity.getConsecutiveRHSUses() + 1);
                int targetNumber = ServerHelper.radicalHeatSinkSuccessTarget(entity.getConsecutiveRHSUses());
                boolean rhsFailure = diceRoll.getIntValue() < targetNumber;

                r = new Report(5541);
                r.indent(2);
                r.subject = entity.getId();
                r.add(targetNumber);
                r.add(diceRoll);
                r.choose(rhsFailure);
                rhsReports.add(r);

                if (rhsFailure) {
                    entity.setHasDamagedRHS(true);
                    int loc = Entity.LOC_NONE;
                    for (Mounted m : entity.getEquipment()) {
                        if (m.getType().hasFlag(MiscType.F_RADICAL_HEATSINK)) {
                            loc = m.getLocation();
                            m.setDestroyed(true);
                            break;
                        }
                    }
                    if (loc == Entity.LOC_NONE) {
                        throw new IllegalStateException("Server.resolveHeat(): " +
                                "Could not find Radical Heat Sink mount on unit that used RHS!");
                    }
                    for (int s = 0; s < entity.getNumberOfCriticals(loc); s++) {
                        CriticalSlot slot = entity.getCritical(loc, s);
                        if ((slot.getType() == CriticalSlot.TYPE_EQUIPMENT)
                                && slot.getMount().getType().hasFlag(MiscType.F_RADICAL_HEATSINK)) {
                            slot.setHit(true);
                            break;
                        }
                    }
                }
            }

            if (entity.tracksHeat() && (entityHex != null) && entityHex.containsTerrain(Terrains.FIRE) && (entityHex.getFireTurn() > 0)
                    && (entity.getElevation() <= 1) && (entity.getAltitude() == 0)) {
                int heatToAdd = 5;
                boolean isMekWithHeatDissipatingArmor = (entity instanceof Mech) && ((Mech) entity).hasIntactHeatDissipatingArmor();
                if (isMekWithHeatDissipatingArmor) {
                    heatToAdd /= 2;
                }
                entity.heatFromExternal += heatToAdd;
                r = new Report(5030);
                r.add(heatToAdd);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
                if (isMekWithHeatDissipatingArmor) {
                    r = new Report(5550);
                    heatEffectsReports.add(r);
                }
            }

            // put in ASF heat build-up first because there are few differences
            if (entity instanceof Aero && !(entity instanceof ConvFighter)) {
                ServerHelper.resolveAeroHeat(game, entity, gameManager.getvPhaseReport(), rhsReports, radicalHSBonus, hotDogMod, gameManager);
                continue;
            }

            // heat doesn't matter for non-mechs
            if (!(entity instanceof Mech)) {
                entity.heat = 0;
                entity.heatBuildup = 0;
                entity.heatFromExternal = 0;
                entity.coolFromExternal = 0;

                if (entity.infernos.isStillBurning()) {
                    gameManager.doFlamingDamage(entity, entity.getPosition());
                }
                if (entity.getTaserShutdownRounds() == 0) {
                    entity.setBATaserShutdown(false);
                    if (entity.isShutDown() && !entity.isManualShutdown()
                            && (entity.getTsempEffect() != MMConstants.TSEMP_EFFECT_SHUTDOWN)) {
                        entity.setShutDown(false);
                        r = new Report(5045);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        heatEffectsReports.add(r);
                    }
                } else if (entity.isBATaserShutdown()) {
                    // if we're shutdown by a BA taser, we might activate again
                    int roll = Compute.d6(2);
                    if (roll >= 8) {
                        entity.setTaserShutdownRounds(0);
                        if (!(entity.isManualShutdown())) {
                            entity.setShutDown(false);
                        }
                        entity.setBATaserShutdown(false);
                    }
                }

                continue;
            }

            // Only Mechs after this point

            // Meks gain heat from inferno hits.
            if (entity.infernos.isStillBurning()) {
                int infernoHeat = entity.infernos.getHeat();
                entity.heatFromExternal += infernoHeat;
                r = new Report(5010);
                r.subject = entity.getId();
                r.add(infernoHeat);
                heatEffectsReports.add(r);
            }

            // should we even bother for this mech?
            if (entity.isDestroyed() || entity.isDoomed() || entity.getCrew().isDoomed()
                    || entity.getCrew().isDead()) {
                continue;
            }

            // engine hits add a lot of heat, provided the engine is on
            entity.heatBuildup += entity.getEngineCritHeat();

            // If a Mek had an active Stealth suite, add 10 heat.
            if (entity.isStealthOn()) {
                entity.heatBuildup += 10;
                r = new Report(5015);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
            }

            // Greg: Nova CEWS If a Mek had an active Nova suite, add 2 heat.
            if (entity.hasActiveNovaCEWS()) {
                entity.heatBuildup += 2;
                r = new Report(5013);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
            }

            // void sig adds 10 heat
            if (entity.isVoidSigOn()) {
                entity.heatBuildup += 10;
                r = new Report(5016);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
            }

            // null sig adds 10 heat
            if (entity.isNullSigOn()) {
                entity.heatBuildup += 10;
                r = new Report(5017);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
            }

            // chameleon polarization field adds 6
            if (entity.isChameleonShieldOn()) {
                entity.heatBuildup += 6;
                r = new Report(5014);
                r.subject = entity.getId();
                heatEffectsReports.add(r);
            }

            // If a Mek is in extreme Temperatures, add or subtract one
            // heat per 10 degrees (or fraction of 10 degrees) above or
            // below 50 or -30 degrees Celsius
            ServerHelper.adjustHeatExtremeTemp(game, entity, gameManager.getvPhaseReport());

            // Add +5 Heat if the hex you're in is on fire
            // and was on fire for the full round.
            if (entityHex != null) {
                int magma = entityHex.terrainLevel(Terrains.MAGMA);
                if ((magma > 0) && (entity.getElevation() == 0)) {
                    int heatToAdd = 5 * magma;
                    if (((Mech) entity).hasIntactHeatDissipatingArmor()) {
                        heatToAdd /= 2;
                    }
                    entity.heatFromExternal += heatToAdd;
                    r = new Report(5032);
                    r.subject = entity.getId();
                    r.add(heatToAdd);
                    heatEffectsReports.add(r);
                    if (((Mech) entity).hasIntactHeatDissipatingArmor()) {
                        r = new Report(5550);
                        heatEffectsReports.add(r);
                    }
                }
            }

            // Check the mech for vibroblades if so then check to see if any
            // are active and what heat they will produce.
            if (entity.hasVibroblades()) {
                int vibroHeat;

                vibroHeat = entity.getActiveVibrobladeHeat(Mech.LOC_RARM);
                vibroHeat += entity.getActiveVibrobladeHeat(Mech.LOC_LARM);

                if (vibroHeat > 0) {
                    r = new Report(5018);
                    r.subject = entity.getId();
                    r.add(vibroHeat);
                    heatEffectsReports.add(r);
                    entity.heatBuildup += vibroHeat;
                }
            }

            int capHeat = 0;
            for (Mounted m : entity.getEquipment()) {
                if ((m.hasChargedOrChargingCapacitor() == 1) && !m.isUsedThisRound()) {
                    capHeat += 5;
                }
                if ((m.hasChargedOrChargingCapacitor() == 2) && !m.isUsedThisRound()) {
                    capHeat += 10;
                }
            }
            if (capHeat > 0) {
                r = new Report(5019);
                r.subject = entity.getId();
                r.add(capHeat);
                heatEffectsReports.add(r);
                entity.heatBuildup += capHeat;
            }

            // Add heat from external sources to the heat buildup
            int max_ext_heat = game.getOptions().intOption(OptionsConstants.ADVCOMBAT_MAX_EXTERNAL_HEAT);
            // Check Game Options
            if (max_ext_heat < 0) {
                max_ext_heat = 15; // standard value specified in TW p.159
            }
            entity.heatBuildup += Math.min(max_ext_heat, entity.heatFromExternal);
            entity.heatFromExternal = 0;
            // remove heat we cooled down
            entity.heatBuildup -= Math.min(9, entity.coolFromExternal);
            entity.coolFromExternal = 0;

            // Combat computers help manage heat
            if (entity.hasQuirk(OptionsConstants.QUIRK_POS_COMBAT_COMPUTER)) {
                int reduce = Math.min(entity.heatBuildup, 4);
                r = new Report(5026);
                r.subject = entity.getId();
                r.add(reduce);
                heatEffectsReports.add(r);
                entity.heatBuildup -= reduce;
            }

            if (entity.hasQuirk(OptionsConstants.QUIRK_NEG_FLAWED_COOLING)
                    && ((Mech) entity).isCoolingFlawActive()) {
                int flaw = 5;
                r = new Report(5021);
                r.subject = entity.getId();
                r.add(flaw);
                heatEffectsReports.add(r);
                entity.heatBuildup += flaw;
            }
            // if heat build up is negative due to temperature, set it to 0
            // for prettier turn reports
            if (entity.heatBuildup < 0) {
                entity.heatBuildup = 0;
            }

            // add the heat we've built up so far.
            entity.heat += entity.heatBuildup;

            // how much heat can we sink?
            int toSink = entity.getHeatCapacityWithWater() + radicalHSBonus;

            if (entity.getCoolantFailureAmount() > 0) {
                int failureAmount = entity.getCoolantFailureAmount();
                r = new Report(5520);
                r.subject = entity.getId();
                r.add(failureAmount);
                heatEffectsReports.add(r);
                toSink -= failureAmount;
            }

            // should we use a coolant pod?
            int safeHeat = entity.hasInfernoAmmo() ? 9 : 13;
            int possibleSinkage = ((Mech) entity).getNumberOfSinks() - entity.getCoolantFailureAmount();
            for (Mounted m : entity.getEquipment()) {
                if (m.getType() instanceof AmmoType) {
                    AmmoType at = (AmmoType) m.getType();
                    if ((at.getAmmoType() == AmmoType.T_COOLANT_POD) && m.isAmmoUsable()) {
                        EquipmentMode mode = m.curMode();
                        if (mode.equals("dump")) {
                            r = new Report(5260);
                            r.subject = entity.getId();
                            heatEffectsReports.add(r);
                            m.setShotsLeft(0);
                            toSink += possibleSinkage;
                            break;
                        }
                        if (mode.equals("safe") && ((entity.heat - toSink) > safeHeat)) {
                            r = new Report(5265);
                            r.subject = entity.getId();
                            heatEffectsReports.add(r);
                            m.setShotsLeft(0);
                            toSink += possibleSinkage;
                            break;
                        }
                        if (mode.equals("efficient") && ((entity.heat - toSink) >= possibleSinkage)) {
                            r = new Report(5270);
                            r.subject = entity.getId();
                            heatEffectsReports.add(r);
                            m.setShotsLeft(0);
                            toSink += possibleSinkage;
                            break;
                        }
                    }
                }
            }

            toSink = Math.min(toSink, entity.heat);
            entity.heat -= toSink;
            r = new Report(5035);
            r.subject = entity.getId();
            r.addDesc(entity);
            r.add(entity.heatBuildup);
            r.add(toSink);
            Color color = GUIPreferences.getInstance().getColorForHeat(entity.heat, Color.BLACK);
            r.add(r.bold(r.fgColor(color, String.valueOf(entity.heat))));
            gameManager.addReport(r);
            entity.heatBuildup = 0;
            gameManager.addReport(rhsReports);
            gameManager.addReport(heatEffectsReports);

            // Does the unit have inferno ammo?
            if (entity.hasInfernoAmmo()) {

                // Roll for possible inferno ammo explosion.
                if (entity.heat >= 10) {
                    int boom = (4 + (entity.heat >= 14 ? 2 : 0) + (entity.heat >= 19 ? 2 : 0)
                            + (entity.heat >= 23 ? 2 : 0) + (entity.heat >= 28 ? 2 : 0))
                            - hotDogMod;
                    Roll diceRoll = Compute.rollD6(2);
                    int rollValue = diceRoll.getIntValue();
                    r = new Report(5040);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.add(boom);
                    if (entity.getCrew().hasActiveTechOfficer()) {
                        rollValue += 2;
                        String rollCalc = rollValue + " [" + diceRoll.getIntValue() + " + 2]";
                        r.addDataWithTooltip(rollCalc, diceRoll.getReport());
                    } else {
                        r.add(diceRoll);
                    }

                    if (rollValue >= boom) {
                        // avoided
                        r.choose(true);
                        gameManager.addReport(r);
                    } else {
                        r.choose(false);
                        gameManager.addReport(r);
                        gameManager.addReport(explodeInfernoAmmoFromHeat(entity));
                    }
                }
            } // End avoid-inferno-explosion
            int autoShutDownHeat;
            boolean mtHeat;

            if (game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_TACOPS_HEAT)) {
                autoShutDownHeat = 50;
                mtHeat = true;
            } else {
                autoShutDownHeat = 30;
                mtHeat = false;
            }
            // heat effects: start up
            if ((entity.heat < autoShutDownHeat) && entity.isShutDown() && !entity.isStalled()) {
                if ((entity.getTaserShutdownRounds() == 0)
                        && (entity.getTsempEffect() != MMConstants.TSEMP_EFFECT_SHUTDOWN)) {
                    if ((entity.heat < 14) && !(entity.isManualShutdown())) {
                        // automatically starts up again
                        entity.setShutDown(false);
                        r = new Report(5045);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        gameManager.addReport(r);
                    } else if (!(entity.isManualShutdown())) {
                        // If the pilot is KO and we need to roll, auto-fail.
                        if (!entity.getCrew().isActive()) {
                            r = new Report(5049);
                            r.subject = entity.getId();
                            r.addDesc(entity);
                        } else {
                            // roll for startup
                            int startup = (4 + (((entity.heat - 14) / 4) * 2)) - hotDogMod;
                            if (mtHeat) {
                                startup -= 5;
                                switch (entity.getCrew().getPiloting()) {
                                    case 0:
                                    case 1:
                                        startup -= 2;
                                        break;
                                    case 2:
                                    case 3:
                                        startup -= 1;
                                        break;
                                    case 6:
                                    case 7:
                                        startup += 1;
                                }
                            }
                            Roll diceRoll = Compute.rollD6(2);
                            r = new Report(5050);
                            r.subject = entity.getId();
                            r.addDesc(entity);
                            r.add(startup);
                            r.add(diceRoll);

                            if (diceRoll.getIntValue() >= startup) {
                                // start 'er back up
                                entity.setShutDown(false);
                                r.choose(true);
                            } else {
                                r.choose(false);
                            }
                        }
                        gameManager.addReport(r);
                    }
                } else {
                    // if we're shutdown by a BA taser, we might activate
                    // again
                    if (entity.isBATaserShutdown()) {
                        int roll = Compute.d6(2);
                        if (roll >= 7) {
                            entity.setTaserShutdownRounds(0);
                            if (!(entity.isManualShutdown())) {
                                entity.setShutDown(false);
                            }
                            entity.setBATaserShutdown(false);
                        }
                    }
                }
            }

            // heat effects: shutdown!
            // Don't shut down if you just restarted.
            else if ((entity.heat >= 14) && !entity.isShutDown()) {
                if (entity.heat >= autoShutDownHeat) {
                    r = new Report(5055);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    gameManager.addReport(r);
                    // add a piloting roll and resolve immediately
                    if (entity.canFall()) {
                        game.addPSR(new PilotingRollData(entity.getId(), 3, "reactor shutdown"));
                        gameManager.addReport(gameManager.resolvePilotingRolls());
                    }
                    // okay, now mark shut down
                    entity.setShutDown(true);
                } else {
                    // Again, pilot KO means shutdown is automatic.
                    if (!entity.getCrew().isActive()) {
                        r = new Report(5056);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        gameManager.addReport(r);
                        entity.setShutDown(true);
                    } else {
                        int shutdown = (4 + (((entity.heat - 14) / 4) * 2)) - hotDogMod;
                        if (mtHeat) {
                            shutdown -= 5;
                            switch (entity.getCrew().getPiloting()) {
                                case 0:
                                case 1:
                                    shutdown -= 2;
                                    break;
                                case 2:
                                case 3:
                                    shutdown -= 1;
                                    break;
                                case 6:
                                case 7:
                                    shutdown += 1;
                            }
                        }
                        Roll diceRoll = Compute.rollD6(2);
                        int rollValue = diceRoll.getIntValue();
                        r = new Report(5060);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        r.add(shutdown);

                        if (entity.getCrew().hasActiveTechOfficer()) {
                            rollValue += 2;
                            String rollCalc = rollValue + " [" + diceRoll.getIntValue() + "]";
                            r.addDataWithTooltip(rollCalc, diceRoll.getReport());
                        } else {
                            r.add(diceRoll);
                        }
                        if (rollValue >= shutdown) {
                            // avoided
                            r.choose(true);
                            gameManager.addReport(r);
                        } else {
                            // shutting down...
                            r.choose(false);
                            gameManager.addReport(r);
                            // add a piloting roll and resolve immediately
                            if (entity.canFall()) {
                                game.addPSR(new PilotingRollData(entity.getId(), 3, "reactor shutdown"));
                                gameManager.addReport(gameManager.resolvePilotingRolls());
                            }
                            // okay, now mark shut down
                            entity.setShutDown(true);
                        }
                    }
                }
            }

            // LAMs in fighter mode need to check for random movement due to heat
            gameManager.checkRandomAeroMovement(entity, hotDogMod);

            // heat effects: ammo explosion!
            if (entity.heat >= 19) {
                int boom = (4 + (entity.heat >= 23 ? 2 : 0) + (entity.heat >= 28 ? 2 : 0))
                        - hotDogMod;
                if (mtHeat) {
                    boom += (entity.heat >= 35 ? 2 : 0)
                            + (entity.heat >= 40 ? 2 : 0)
                            + (entity.heat >= 45 ? 2 : 0);
                    // Last line is a crutch; 45 heat should be no roll
                    // but automatic explosion.
                }
                if (((Mech) entity).hasLaserHeatSinks()) {
                    boom--;
                }
                Roll diceRoll = Compute.rollD6(2);
                int rollValue = diceRoll.getIntValue();
                r = new Report(5065);
                r.subject = entity.getId();
                r.addDesc(entity);
                r.add(boom);
                if (entity.getCrew().hasActiveTechOfficer()) {
                    rollValue += 2;
                    String rollCalc = rollValue + " [" + diceRoll.getIntValue() + " + 2]";;
                    r.addDataWithTooltip(rollCalc, diceRoll.getReport());
                } else {
                    r.add(diceRoll);
                }
                if (rollValue >= boom) {
                    // mech is ok
                    r.choose(true);
                    gameManager.addReport(r);
                } else {
                    // boom!
                    r.choose(false);
                    gameManager.addReport(r);
                    gameManager.addReport(gameManager.explodeAmmoFromHeat(entity));
                }
            }

            // heat effects: mechwarrior damage
            // N.B. The pilot may already be dead.
            int lifeSupportCritCount;
            boolean torsoMountedCockpit = ((Mech) entity).getCockpitType() == Mech.COCKPIT_TORSO_MOUNTED;
            if (torsoMountedCockpit) {
                lifeSupportCritCount = entity.getHitCriticals(CriticalSlot.TYPE_SYSTEM,
                        Mech.SYSTEM_LIFE_SUPPORT, Mech.LOC_RT);
                lifeSupportCritCount += entity.getHitCriticals(CriticalSlot.TYPE_SYSTEM,
                        Mech.SYSTEM_LIFE_SUPPORT, Mech.LOC_LT);
            } else {
                lifeSupportCritCount = entity.getHitCriticals(CriticalSlot.TYPE_SYSTEM,
                        Mech.SYSTEM_LIFE_SUPPORT, Mech.LOC_HEAD);
            }
            int damageHeat = entity.heat;
            if (entity.hasQuirk(OptionsConstants.QUIRK_POS_IMP_LIFE_SUPPORT)) {
                damageHeat -= 5;
            }
            if (entity.hasQuirk(OptionsConstants.QUIRK_NEG_POOR_LIFE_SUPPORT)) {
                damageHeat += 5;
            }
            if ((lifeSupportCritCount > 0)
                    && ((damageHeat >= 15) || (torsoMountedCockpit && (damageHeat > 0)))
                    && !entity.getCrew().isDead() && !entity.getCrew().isDoomed()
                    && !entity.getCrew().isEjected()) {
                int heatLimitDesc = 1;
                int damageToCrew = 0;
                if ((damageHeat >= 47) && mtHeat) {
                    // mechwarrior takes 5 damage
                    heatLimitDesc = 47;
                    damageToCrew = 5;
                } else if ((damageHeat >= 39) && mtHeat) {
                    // mechwarrior takes 4 damage
                    heatLimitDesc = 39;
                    damageToCrew = 4;
                } else if ((damageHeat >= 32) && mtHeat) {
                    // mechwarrior takes 3 damage
                    heatLimitDesc = 32;
                    damageToCrew = 3;
                } else if (damageHeat >= 25) {
                    // mechwarrior takes 2 damage
                    heatLimitDesc = 25;
                    damageToCrew = 2;
                } else if (damageHeat >= 15) {
                    // mechwarrior takes 1 damage
                    heatLimitDesc = 15;
                    damageToCrew = 1;
                }
                if ((((Mech) entity).getCockpitType() == Mech.COCKPIT_TORSO_MOUNTED)
                        && !entity.hasAbility(OptionsConstants.MD_PAIN_SHUNT)) {
                    damageToCrew += 1;
                }
                r = new Report(5070);
                r.subject = entity.getId();
                r.addDesc(entity);
                r.add(heatLimitDesc);
                r.add(damageToCrew);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.damageCrew(entity, damageToCrew));
            } else if (mtHeat && (entity.heat >= 32) && !entity.getCrew().isDead()
                    && !entity.getCrew().isDoomed()
                    && !entity.hasAbility(OptionsConstants.MD_PAIN_SHUNT)) {
                // Crew may take damage from heat if MaxTech option is set
                Roll diceRoll = Compute.rollD6(2);
                int avoidNumber;
                if (entity.heat >= 47) {
                    avoidNumber = 12;
                } else if (entity.heat >= 39) {
                    avoidNumber = 10;
                } else {
                    avoidNumber = 8;
                }
                avoidNumber -= hotDogMod;
                r = new Report(5075);
                r.subject = entity.getId();
                r.addDesc(entity);
                r.add(avoidNumber);
                r.add(diceRoll);

                if (diceRoll.getIntValue() >= avoidNumber) {
                    // damage avoided
                    r.choose(true);
                    gameManager.addReport(r);
                } else {
                    r.choose(false);
                    gameManager.addReport(r);
                    gameManager.addReport(gameManager.damageCrew(entity, 1));
                }
            }

            // The pilot may have just expired.
            if ((entity.getCrew().isDead() || entity.getCrew().isDoomed())
                    && !entity.getCrew().isEjected()) {
                r = new Report(5080);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.destroyEntity(entity, "crew death", true));
            }

            // With MaxTech Heat Scale, there may occur critical damage
            if (mtHeat) {
                if (entity.heat >= 36) {
                    Roll diceRoll = Compute.rollD6(2);
                    int damageNumber;
                    if (entity.heat >= 44) {
                        damageNumber = 10;
                    } else {
                        damageNumber = 8;
                    }
                    damageNumber -= hotDogMod;
                    r = new Report(5085);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.add(damageNumber);
                    r.add(diceRoll);
                    r.newlines = 0;

                    if (diceRoll.getIntValue() >= damageNumber) {
                        r.choose(true);
                    } else {
                        r.choose(false);
                        gameManager.addReport(r);
                        gameManager.addReport(gameManager.oneCriticalEntity(entity, Compute.randomInt(8), false, 0));
                        // add an empty report, for line breaking
                        r = new Report(1210, Report.PUBLIC);
                    }
                    gameManager.addReport(r);
                }
            }

            if (game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_TACOPS_COOLANT_FAILURE)
                    && (entity.getHeatCapacity() > entity.getCoolantFailureAmount())
                    && (entity.heat >= 5)) {
                Roll diceRoll = Compute.rollD6(2);
                int hitNumber = 10;
                hitNumber -= Math.max(0, (int) Math.ceil(entity.heat / 5.0) - 2);
                r = new Report(5525);
                r.subject = entity.getId();
                r.add(entity.getShortName());
                r.add(hitNumber);
                r.add(diceRoll);
                r.newlines = 0;
                gameManager.addReport(r);

                if (diceRoll.getIntValue() >= hitNumber) {
                    r = new Report(5052);
                    r.subject = entity.getId();
                    gameManager.addReport(r);
                    r = new Report(5526);
                    r.subject = entity.getId();
                    r.add(entity.getShortNameRaw());
                    gameManager.addReport(r);
                    entity.addCoolantFailureAmount(1);
                } else {
                    r = new Report(5041);
                    r.subject = entity.getId();
                    gameManager.addReport(r);
                }
            }
        }

        if (gameManager.getvPhaseReport().size() == 1) {
            // I guess nothing happened...
            gameManager.addReport(new Report(1205, Report.PUBLIC));
        }
    }

    /**
     * Makes one slot of inferno ammo, determined by certain rules, explode on a
     * mech.
     *
     * @param entity
     *            The <code>Entity</code> that should suffer an inferno ammo
     *            explosion.
     */
    private Vector<Report> explodeInfernoAmmoFromHeat(Entity entity) {
        int damage = 0;
        int rack = 0;
        int boomloc = -1;
        int boomslot = -1;
        Vector<Report> vDesc = new Vector<>();
        Report r;

        // Find the most destructive Inferno ammo.
        for (int j = 0; j < entity.locations(); j++) {
            for (int k = 0; k < entity.getNumberOfCriticals(j); k++) {
                CriticalSlot cs = entity.getCritical(j, k);
                // Ignore empty, destroyed, hit, and structure slots.
                if ((cs == null) || cs.isDestroyed() || cs.isHit()
                        || (cs.getType() != CriticalSlot.TYPE_EQUIPMENT)) {
                    continue;
                }
                // Ignore everything but ammo or LAM bomb bay slots.
                Mounted mounted = cs.getMount();
                int newRack;
                int newDamage;
                if (mounted.getType() instanceof AmmoType) {
                    AmmoType atype = (AmmoType) mounted.getType();
                    if (!atype.isExplosive(mounted)
                            || (!(atype.getMunitionType().contains(AmmoType.Munitions.M_INFERNO))
                            && !(atype.getMunitionType().contains(AmmoType.Munitions.M_IATM_IIW)))) {
                        continue;
                    }
                    // ignore empty, destroyed, or missing bins
                    if (mounted.getHittableShotsLeft() == 0) {
                        continue;
                    }
                    // Find the most destructive undamaged ammo.
                    // TW page 160, compare one rack's
                    // damage. Ties go to most rounds.
                    newRack = atype.getDamagePerShot() * atype.getRackSize();
                    newDamage = mounted.getExplosionDamage();
                    Mounted mount2 = cs.getMount2();
                    if ((mount2 != null) && (mount2.getType() instanceof AmmoType)
                            && (mount2.getHittableShotsLeft() > 0)) {
                        // must be for same weaponType, so rackSize stays
                        atype = (AmmoType) mount2.getType();
                        newRack += atype.getDamagePerShot() * atype.getRackSize();
                        newDamage += mount2.getExplosionDamage();
                    }
                } else if ((mounted.getType() instanceof MiscType)
                        && mounted.getType().hasFlag(MiscType.F_BOMB_BAY)) {
                    while (mounted.getLinked() != null) {
                        mounted = mounted.getLinked();
                    }
                    if (mounted.getExplosionDamage() == 0) {
                        continue;
                    }
                    newRack = 1;
                    newDamage = mounted.getExplosionDamage();
                } else {
                    continue;
                }

                if (!mounted.isHit()
                        && ((rack < newRack) || ((rack == newRack) && (damage < newDamage)))) {
                    rack = newRack;
                    damage = newDamage;
                    boomloc = j;
                    boomslot = k;
                }
            }
        }
        // Did we find anything to explode?
        if ((boomloc != -1) && (boomslot != -1)) {
            CriticalSlot slot = entity.getCritical(boomloc, boomslot);
            slot.setHit(true);
            Mounted equip = slot.getMount();
            equip.setHit(true);
            // We've allocated heatBuildup to heat in resolveHeat(),
            // so need to add to the entity's heat instead.
            if ((equip.getType() instanceof AmmoType)
                    || (equip.getLinked() != null
                    && equip.getLinked().getType() instanceof BombType
                    && ((BombType) equip.getLinked().getType()).getBombType() == BombType.B_INFERNO)) {
                entity.heat += Math.min(equip.getExplosionDamage(), 30);
            }
            vDesc.addAll(gameManager.explodeEquipment(entity, boomloc, boomslot));
            r = new Report(5155);
            r.indent();
            r.subject = entity.getId();
            r.add(entity.heat);
            vDesc.addElement(r);
            entity.heatBuildup = 0;
        } else { // no ammo to explode
            r = new Report(5160);
            r.indent();
            r.subject = entity.getId();
            vDesc.addElement(r);
        }
        return vDesc;
    }

    /**
     * Check to see if blowing sand caused damage to airborne VTOL/WIGEs
     */
    private Vector<Report> resolveBlowingSandDamage() {
        Vector<Report> vFullReport = new Vector<>();
        vFullReport.add(new Report(5002, Report.PUBLIC));
        int damage_bonus = Math.max(0, game.getPlanetaryConditions().getWindStrength()
                - PlanetaryConditions.WI_MOD_GALE);
        // cycle through each team and damage 1d6 airborne VTOL/WiGE
        for (Team team : game.getTeams()) {
            Vector<Integer> airborne = gameManager.getAirborneVTOL(team);
            if (!airborne.isEmpty()) {
                // how many units are affected
                int unitsAffected = Math.min(Compute.d6(), airborne.size());
                while ((unitsAffected > 0) && !airborne.isEmpty()) {
                    int loc = Compute.randomInt(airborne.size());
                    Entity en = game.getEntity(airborne.get(loc));
                    int damage = Math.max(1, Compute.d6() / 2) + damage_bonus;
                    while (damage > 0) {
                        HitData hit = en.rollHitLocation(ToHitData.HIT_NORMAL, ToHitData.SIDE_RANDOM);
                        vFullReport.addAll(gameManager.damageEntity(en, hit, 1));
                        damage--;
                    }
                    unitsAffected--;
                    airborne.remove(loc);
                }
            }
        }
        Report.addNewline(gameManager.getvPhaseReport());
        return vFullReport;
    }

    /**
     * Resolves all built up control rolls. Used only during end phase
     */
    private Vector<Report> resolveControlRolls() {
        Vector<Report> vFullReport = new Vector<>();
        vFullReport.add(new Report(5001, Report.PUBLIC));
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            vFullReport.addAll(resolveControl(i.next()));
        }
        game.resetControlRolls();
        return vFullReport;
    }

    /**
     * Resolves and reports all control skill rolls for a single aero or airborne LAM in airmech mode.
     */
    private Vector<Report> resolveControl(Entity e) {
        Vector<Report> vReport = new Vector<>();
        if (e.isDoomed() || e.isDestroyed() || e.isOffBoard() || !e.isDeployed()) {
            return vReport;
        }
        Report r;

        /*
         * See forum answers on OOC
         * http://forums.classicbattletech.com/index.php/topic,20424.0.html
         */

        IAero a = null;
        boolean canRecover = false;
        if (e.isAero() && (e.isAirborne() || e.isSpaceborne())) {
            a = (IAero) e;
            // they should get a shot at a recovery roll at the end of all this
            // if they are already out of control
            canRecover = a.isOutControl();
        } else if (!(e instanceof LandAirMech) || !e.isAirborneVTOLorWIGE()) {
            return vReport;
        }

        // if the unit already is moving randomly then it can't get any worse
        if (a == null || !a.isRandomMove()) {
            // find control rolls and make them
            Vector<PilotingRollData> rolls = new Vector<>();
            StringBuilder reasons = new StringBuilder();
            PilotingRollData target = e.getBasePilotingRoll();
            // maneuvering ace
            // TODO : pending rules query
            // http://www.classicbattletech.com/forums/index.php/topic,63552.new.html#new
            // for now I am assuming Man Ace applies to all out-of-control
            // rolls, but not other
            // uses of control rolls (thus it doesn't go in
            // Entity#addEntityBonuses) and
            // furthermore it doesn't apply to recovery rolls
            if (e.isUsingManAce()) {
                target.addModifier(-1, "maneuvering ace");
            }
            for (Enumeration<PilotingRollData> j = game.getControlRolls(); j.hasMoreElements(); ) {
                final PilotingRollData modifier = j.nextElement();
                if (modifier.getEntityId() != e.getId()) {
                    continue;
                }
                // found a roll, add it
                rolls.addElement(modifier);
                if (reasons.length() > 0) {
                    reasons.append("; ");
                }
                reasons.append(modifier.getCumulativePlainDesc());
                target.append(modifier);
            }
            // any rolls needed?
            if (!rolls.isEmpty()) {
                // loop through rolls we do have to make...
                r = new Report(9310);
                r.subject = e.getId();
                r.addDesc(e);
                r.add(rolls.size());
                r.add(reasons.toString());
                vReport.add(r);
                r = new Report(2285);
                r.subject = e.getId();
                r.add(target);
                vReport.add(r);
                for (int j = 0; j < rolls.size(); j++) {
                    PilotingRollData modifier = rolls.elementAt(j);
                    r = new Report(2290);
                    r.subject = e.getId();
                    r.indent();
                    r.newlines = 0;
                    r.add(j + 1);
                    r.add(modifier.getPlainDesc());
                    vReport.add(r);
                    Roll diceRoll = Compute.rollD6(2);

                    // different reports depending on out-of-control status
                    if (a != null && a.isOutControl()) {
                        r = new Report(9360);
                        r.subject = e.getId();
                        r.add(target);
                        r.add(diceRoll);
                        if (diceRoll.getIntValue() < (target.getValue() - 5)) {
                            r.choose(false);
                            vReport.add(r);
                            a.setRandomMove(true);
                        } else {
                            r.choose(true);
                            vReport.add(r);
                        }
                    } else {
                        r = new Report(9315);
                        r.subject = e.getId();
                        r.add(target);
                        r.add(diceRoll);
                        r.newlines = 1;
                        if (diceRoll.getIntValue() < target.getValue()) {
                            r.choose(false);
                            vReport.add(r);
                            if (a != null) {
                                a.setOutControl(true);
                                // do we have random movement?
                                if ((target.getValue() - diceRoll.getIntValue()) > 5) {
                                    r = new Report(9365);
                                    r.newlines = 0;
                                    r.subject = e.getId();
                                    vReport.add(r);
                                    a.setRandomMove(true);
                                }
                                // if on the atmospheric map, then lose altitude
                                // and check
                                // for crash
                                if (!a.isSpaceborne() && a.isAirborne()) {
                                    Roll diceRoll2 = Compute.rollD6(1);

                                    int origAltitude = e.getAltitude();
                                    e.setAltitude(e.getAltitude() - diceRoll2.getIntValue());
                                    // Reroll altitude loss with edge if the new altitude would result in a crash
                                    if (e.getAltitude() <= 0
                                            // Don't waste the edge if it won't help
                                            && origAltitude > 1
                                            && e.getCrew().hasEdgeRemaining()
                                            && e.getCrew().getOptions().booleanOption(OptionsConstants.EDGE_WHEN_AERO_ALT_LOSS)) {
                                        Roll diceRoll3 = Compute.rollD6(1);
                                        int rollValue3 = diceRoll3.getIntValue();
                                        String rollReport3 = diceRoll3.getReport();

                                        // Report the edge use
                                        r = new Report(9367);
                                        r.newlines = 1;
                                        r.subject = e.getId();
                                        vReport.add(r);
                                        e.setAltitude(origAltitude - rollValue3);
                                        // and spend the edge point
                                        e.getCrew().decreaseEdge();
                                        diceRoll2 = diceRoll3;
                                    }
                                    //Report the altitude loss
                                    r = new Report(9366);
                                    r.newlines = 0;
                                    r.subject = e.getId();
                                    r.addDesc(e);
                                    r.add(diceRoll2);
                                    vReport.add(r);
                                    // check for crash
                                    if (gameManager.checkCrash(e, e.getPosition(), e.getAltitude())) {
                                        vReport.addAll(gameManager.processCrash(e, a.getCurrentVelocity(),
                                                e.getPosition()));
                                        break;
                                    }
                                }
                            } else if (e.isAirborneVTOLorWIGE()) {
                                int loss = target.getValue() - diceRoll.getIntValue();
                                r = new Report(9366);
                                r.subject = e.getId();
                                r.addDesc(e);
                                r.add(loss);
                                vReport.add(r);
                                Hex hex = game.getBoard().getHex(e.getPosition());
                                int elevation = Math.max(0, hex.terrainLevel(Terrains.BLDG_ELEV));
                                if (e.getElevation() - loss <= elevation) {
                                    gameManager.crashAirMech(e, target, vReport);
                                } else {
                                    e.setElevation(e.getElevation() - loss);
                                }
                            }
                        } else {
                            r.choose(true);
                            vReport.add(r);
                        }
                    }
                }
            }
        }

        // if they were out-of-control to start with, give them a chance to regain control
        if (canRecover) {
            PilotingRollData base = e.getBasePilotingRoll();
            // is our base roll impossible?
            if ((base.getValue() == TargetRoll.AUTOMATIC_FAIL)
                    || (base.getValue() == TargetRoll.IMPOSSIBLE)) {
                // report something
                r = new Report(9340);
                r.subject = e.getId();
                r.addDesc(e);
                r.add(base.getDesc());
                vReport.add(r);
                return vReport;
            }
            r = new Report(9345);
            r.subject = e.getId();
            r.addDesc(e);
            r.add(base.getDesc());
            vReport.add(r);
            Roll diceRoll = Compute.rollD6(2);
            r = new Report(9350);
            r.subject = e.getId();
            r.add(base);
            r.add(diceRoll);

            if (diceRoll.getIntValue() < base.getValue()) {
                r.choose(false);
                vReport.add(r);
            } else {
                r.choose(true);
                vReport.add(r);
                a.setOutControl(false);
                a.setOutCtrlHeat(false);
                a.setRandomMove(false);
            }
        }
        return vReport;
    }

    private Vector<Report> checkForTraitors() {
        Vector<Report> vFullReport = new Vector<>();
        // check for traitors
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            Entity entity = i.next();
            if (entity.isDoomed() || entity.isDestroyed() || entity.isOffBoard()
                    || !entity.isDeployed()) {
                continue;
            }
            if ((entity.getTraitorId() != -1) && (entity.getOwnerId() != entity.getTraitorId())) {
                final Player oldPlayer = game.getPlayer(entity.getOwnerId());
                final Player newPlayer = game.getPlayer(entity.getTraitorId());
                if (newPlayer != null) {
                    Report r = new Report(7305);
                    r.subject = entity.getId();
                    r.add(entity.getDisplayName());
                    r.add(newPlayer.getName());
                    entity.setOwner(newPlayer);
                    gameManager.entityUpdate(entity.getId());
                    vFullReport.add(r);

                    // Move the initial count and BV to their new player
                    newPlayer.changeInitialEntityCount(1);
                    newPlayer.changeInitialBV(entity.calculateBattleValue());

                    // And remove it from their old player, if they exist
                    if (oldPlayer != null) {
                        oldPlayer.changeInitialEntityCount(-1);
                        // Note: I don't remove the full initial BV if I'm damaged, but that
                        // actually makes sense
                        oldPlayer.changeInitialBV(-1 * entity.calculateBattleValue());
                    }
                }
                entity.setTraitorId(-1);
            }
        }

        if (!vFullReport.isEmpty()) {
            vFullReport.add(0, new Report(7300));
        }

        return vFullReport;
    }

    /**
     * Check all aircraft that may have used internal bomb bays for incidental explosions
     * caused by ground fire.
     * @return
     */
    private Vector<Report> resolveInternalBombHits() {
        Vector<Report> vFullReport = new Vector<>();
        vFullReport.add(new Report(5600, Report.PUBLIC));
        for (Entity e : game.getEntitiesVector()) {
            Vector<Report> interim = resolveInternalBombHit(e);
            if (!interim.isEmpty()) {
                vFullReport.addAll(interim);
            }
        }
        // Return empty Vector if no reports (besides the header) are added.
        return (vFullReport.size() == 1) ? new Vector<>() : vFullReport;
    }

    /**
     * Resolves and reports all control skill rolls for a single aero or airborne LAM in airmech mode.
     */
    private Vector<Report> resolveInternalBombHit(Entity e) {
        Vector<Report> vReport = new Vector<>();
        // Only applies to surviving bombing craft that took damage this last round
        if (!e.isBomber() || e.damageThisRound <= 0 || e.isDoomed() || e.isDestroyed() || !e.isDeployed()) {
            return vReport;
        }

        //
        if (e.isAero() && !(e instanceof LandAirMech)) {
            // Only ground fire can hit internal bombs
            if (e.getGroundAttackedByThisTurn().isEmpty()) {
                return vReport;
            }

            Aero b = (Aero) e;
            Report r;

            if (b.getUsedInternalBombs() > 0) {
                int id = e.getId();

                // Header
                r = new Report(5601);
                r.subject = id;
                r.addDesc(e);
                vReport.add(r);

                // Roll
                int rollTarget = 10; //Testing purposes
                int roll = Compute.d6(2);
                boolean explosion = roll >= rollTarget;
                r = new Report(5602);
                r.indent();
                r.subject = id;
                r.addDesc(e);
                r.add(rollTarget);
                r.add(roll, false);
                vReport.add(r);

                // Outcome
                r = (explosion) ? new Report(5603) : new Report(5604);
                r.indent();
                r.subject = id;
                r.addDesc(e);
                int bombsLeft = b.getBombs().stream().mapToInt(Mounted::getUsableShotsLeft).sum();
                int bombDamage = b.getInternalBombsDamageTotal();
                if (explosion) {
                    r.add(bombDamage);
                }
                r.add(bombsLeft);
                vReport.add(r);
                // Deal damage
                if (explosion) {
                    HitData hd = new HitData(b.getBodyLocation(), false, HitData.EFFECT_NONE);
                    vReport.addAll(gameManager.damageEntity(e, hd, bombDamage, true, DamageType.NONE,true));
                    e.applyDamage();
                }
            }
        }
        return vReport;
    }

    /**
     * End-phase checks for laid explosives; check whether explosives are
     * touched off, or if we should report laying explosives
     */
    private void checkLayExplosives() {
        // Report continuing explosive work
        for (Entity e : game.getEntitiesVector()) {
            if (!(e instanceof Infantry)) {
                continue;
            }
            Infantry inf = (Infantry) e;
            if (inf.turnsLayingExplosives > 0) {
                Report r = new Report(4271);
                r.subject = inf.getId();
                r.addDesc(inf);
                gameManager.addReport(r);
            }
        }
        // Check for touched-off explosives
        Vector<Building> updatedBuildings = new Vector<>();
        for (Building.DemolitionCharge charge : gameManager.getExplodingCharges()) {
            Building bldg = game.getBoard().getBuildingAt(charge.pos);
            if (bldg == null) { // Shouldn't happen...
                continue;
            }
            bldg.removeDemolitionCharge(charge);
            updatedBuildings.add(bldg);
            Report r = new Report(4272, Report.PUBLIC);
            r.add(bldg.getName());
            gameManager.addReport(r);
            Vector<Report> dmgReports = gameManager.damageBuilding(bldg, charge.damage, " explodes for ", charge.pos);
            for (Report rep : dmgReports) {
                rep.indent();
                gameManager.addReport(rep);
            }
        }
        gameManager.clearExplodingCharges();
        gameManager.sendChangedBuildings(updatedBuildings);
    }

    /*
     * Resolve HarJel II/III repairs for Mechs so equipped.
     */
    private void resolveHarJelRepairs() {
        Report r;
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            Entity entity = i.next();
            if (!(entity instanceof Mech)) {
                continue;
            }

            Mech me = (Mech) entity;
            for (int loc = 0; loc < me.locations(); ++loc) {
                boolean harJelII = me.hasHarJelIIIn(loc); // false implies HarJel III
                if ((harJelII || me.hasHarJelIIIIn(loc))
                        && me.isArmorDamagedThisTurn(loc)) {
                    if (me.hasRearArmor(loc)) {
                        // must have at least one remaining armor in location
                        if (!((me.getArmor(loc) > 0) || (me.getArmor(loc, true) > 0))) {
                            continue;
                        }

                        int toRepair = harJelII ? 2 : 4;
                        int frontRepair, rearRepair;
                        int desiredFrontRepair, desiredRearRepair;

                        Mounted harJel = null;
                        // find HarJel item
                        // don't need to check ready or worry about null,
                        // we already know there is one, it's ready,
                        // and there can be at most one in a given location
                        for (Mounted m: me.getMisc()) {
                            if ((m.getLocation() == loc)
                                    && (m.getType().hasFlag(MiscType.F_HARJEL_II)
                                    || m.getType().hasFlag(MiscType.F_HARJEL_III))) {
                                harJel = m;
                            }
                        }

                        if (harJelII) {
                            if (harJel.curMode().equals(MiscType.S_HARJEL_II_1F1R)) {
                                desiredFrontRepair = 1;
                            } else if (harJel.curMode().equals(MiscType.S_HARJEL_II_2F0R)) {
                                desiredFrontRepair = 2;
                            } else { // 0F2R
                                desiredFrontRepair = 0;
                            }
                        } else { // HarJel III
                            if (harJel.curMode().equals(MiscType.S_HARJEL_III_2F2R)) {
                                desiredFrontRepair = 2;
                            } else if (harJel.curMode().equals(MiscType.S_HARJEL_III_4F0R)) {
                                desiredFrontRepair = 4;
                            } else if (harJel.curMode().equals(MiscType.S_HARJEL_III_3F1R)) {
                                desiredFrontRepair = 3;
                            } else if (harJel.curMode().equals(MiscType.S_HARJEL_III_1F3R)) {
                                desiredFrontRepair = 1;
                            } else { // 0F4R
                                desiredFrontRepair = 0;
                            }
                        }
                        desiredRearRepair = toRepair - desiredFrontRepair;

                        int availableFrontRepair = me.getOArmor(loc) - me.getArmor(loc);
                        int availableRearRepair = me.getOArmor(loc, true) - me.getArmor(loc, true);
                        frontRepair = Math.min(availableFrontRepair, desiredFrontRepair);
                        rearRepair = Math.min(availableRearRepair, desiredRearRepair);
                        int surplus = desiredFrontRepair - frontRepair;
                        if (surplus > 0) { // we couldn't use all the points we wanted in front
                            rearRepair = Math.min(availableRearRepair, rearRepair + surplus);
                        } else {
                            surplus = desiredRearRepair - rearRepair;
                            // try to move any excess points from rear to front
                            frontRepair = Math.min(availableFrontRepair, frontRepair + surplus);
                        }

                        if (frontRepair > 0) {
                            me.setArmor(me.getArmor(loc) + frontRepair, loc);
                            r = new Report(harJelII ? 9850 : 9851);
                            r.subject = me.getId();
                            r.addDesc(entity);
                            r.add(frontRepair);
                            r.add(me.getLocationAbbr(loc));
                            gameManager.addReport(r);
                        }
                        if (rearRepair > 0) {
                            me.setArmor(me.getArmor(loc, true) + rearRepair, loc, true);
                            r = new Report(harJelII ? 9850 : 9851);
                            r.subject = me.getId();
                            r.addDesc(entity);
                            r.add(rearRepair);
                            r.add(me.getLocationAbbr(loc) + " (R)");
                            gameManager.addReport(r);
                        }
                    } else {
                        // must have at least one remaining armor in location
                        if (!(me.getArmor(loc) > 0)) {
                            continue;
                        }
                        int toRepair = harJelII ? 2 : 4;
                        toRepair = Math.min(toRepair, me.getOArmor(loc) - me.getArmor(loc));
                        me.setArmor(me.getArmor(loc) + toRepair, loc);
                        r = new Report(harJelII ? 9850 : 9851);
                        r.subject = me.getId();
                        r.addDesc(entity);
                        r.add(toRepair);
                        r.add(me.getLocationAbbr(loc));
                        gameManager.addReport(r);
                    }
                }
            }
        }
    }

    private void resolveEmergencyCoolantSystem() {
        for (Entity e : game.getEntitiesVector()) {
            if ((e instanceof Mech) && e.hasWorkingMisc(MiscType.F_EMERGENCY_COOLANT_SYSTEM)
                    && (e.heat > 13)) {
                Mech mech = (Mech) e;
                Vector<Report> vDesc = new Vector<>();
                HashMap<Integer, java.util.List<CriticalSlot>> crits = new HashMap<>();
                if (!(mech.doRISCEmergencyCoolantCheckFor(vDesc, crits))) {
                    mech.heat -= 6 + mech.getCoolantSystemMOS();
                    Report r = new Report(5027);
                    r.add(6+mech.getCoolantSystemMOS());
                    vDesc.add(r);
                }
                gameManager.addReport(vDesc);
                for (Integer loc : crits.keySet()) {
                    List<CriticalSlot> lcs = crits.get(loc);
                    for (CriticalSlot cs : lcs) {
                        gameManager.addReport(gameManager.applyCriticalHit(mech, loc, cs, true, 0, false));
                    }
                }
            }
        }
    }

    /**
     * Checks to see if any entities are underwater (or in vacuum) with damaged
     * life support. Called during the end phase.
     */
    private void checkForSuffocation() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if ((null == entity.getPosition()) || entity.isOffBoard()) {
                continue;
            }
            final Hex curHex = game.getBoard().getHex(entity.getPosition());
            if ((((entity.getElevation() < 0) && ((curHex
                    .terrainLevel(Terrains.WATER) > 1) || ((curHex
                    .terrainLevel(Terrains.WATER) == 1) && entity.isProne()))) || game
                    .getPlanetaryConditions().isVacuum())
                    && (entity.getHitCriticals(CriticalSlot.TYPE_SYSTEM,
                    Mech.SYSTEM_LIFE_SUPPORT, Mech.LOC_HEAD) > 0)) {
                Report r = new Report(6020);
                r.subject = entity.getId();
                r.addDesc(entity);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.damageCrew(entity, 1));

            }
        }
    }

    /**
     * Report: - Any ammo dumps beginning the following round. - Any ammo dumps
     * that have ended with the end of this round.
     */
    private void resolveAmmoDumps() {
        Report r;
        for (Entity entity : game.getEntitiesVector()) {
            for (Mounted m : entity.getAmmo()) {
                if (m.isPendingDump()) {
                    // report dumping next round
                    r = new Report(5110);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.add(m.getName());
                    gameManager.addReport(r);
                    // update status
                    m.setPendingDump(false);
                    m.setDumping(true);
                } else if (m.isDumping()) {
                    // report finished dumping
                    r = new Report(5115);
                    r.subject = entity.getId();
                    r.addDesc(entity);
                    r.add(m.getName());
                    gameManager.addReport(r);
                    // update status
                    m.setDumping(false);
                    m.setShotsLeft(0);
                }
            }
            // also do DWP dumping
            if (entity instanceof BattleArmor) {
                for (Mounted m : entity.getWeaponList()) {
                    if (m.isDWPMounted() && m.isPendingDump()) {
                        m.setMissing(true);
                        r = new Report(5116);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        r.add(m.getName());
                        gameManager.addReport(r);
                        m.setPendingDump(false);
                        // Also dump all of the ammo in the DWP
                        for (Mounted ammo : entity.getAmmo()) {
                            if (m.equals(ammo.getLinkedBy())) {
                                ammo.setMissing(true);
                            }
                        }
                        // Check for jettisoning missiles
                    } else if (m.isBodyMounted() && m.isPendingDump()
                            && m.getType().hasFlag(WeaponType.F_MISSILE)
                            && (m.getLinked() != null)
                            && (m.getLinked().getUsableShotsLeft() > 0)) {
                        m.setMissing(true);
                        r = new Report(5116);
                        r.subject = entity.getId();
                        r.addDesc(entity);
                        r.add(m.getName());
                        gameManager.addReport(r);
                        m.setPendingDump(false);
                        // Dump all ammo related to this launcher
                        // BA burdened is based on whether the launcher has
                        // ammo left
                        while ((m.getLinked() != null)
                                && (m.getLinked().getUsableShotsLeft() > 0)) {
                            m.getLinked().setMissing(true);
                            entity.loadWeapon(m);
                        }
                    }
                }
            }
            entity.reloadEmptyWeapons();
        }
    }

    /**
     * Make the rolls indicating whether any unconscious crews wake up
     */
    private void resolveCrewWakeUp() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity e = i.next();

            // only unconscious pilots of mechs and protos, ASF and Small Craft
            // and MechWarriors can roll to wake up
            if (e.isTargetable()
                    && ((e instanceof Mech) || (e instanceof Protomech)
                    || (e instanceof MechWarrior) || ((e instanceof Aero) && !(e instanceof Jumpship)))) {
                for (int pos = 0; pos < e.getCrew().getSlotCount(); pos++) {
                    if (e.getCrew().isMissing(pos)) {
                        continue;
                    }
                    if (e.getCrew().isUnconscious(pos)
                            && !e.getCrew().isKoThisRound(pos)) {
                        Roll diceRoll = Compute.rollD6(2);
                        int rollValue = diceRoll.getIntValue();
                        String rollCalc = String.valueOf(rollValue);

                        if (e.hasAbility(OptionsConstants.MISC_PAIN_RESISTANCE)) {
                            rollValue = Math.min(12, rollValue + 1);
                            rollCalc = rollValue + " [" + diceRoll.getIntValue() + " + 1] max 12";
                        }

                        int rollTarget = Compute.getConsciousnessNumber(e.getCrew().getHits(pos));
                        Report r = new Report(6029);
                        r.subject = e.getId();
                        r.add(e.getCrew().getCrewType().getRoleName(pos));
                        r.addDesc(e);
                        r.add(e.getCrew().getName(pos));
                        r.add(rollTarget);
                        r.addDataWithTooltip(rollCalc, diceRoll.getReport());

                        if (rollValue >= rollTarget) {
                            r.choose(true);
                            e.getCrew().setUnconscious(false, pos);
                        } else {
                            r.choose(false);
                        }
                        gameManager.addReport(r);
                    }
                }
            }
        }
    }

    /**
     * Check whether any <code>Entity</code> with a cockpit command console has been scheduled to swap
     * roles between the two crew members.
     */
    private void resolveConsoleCrewSwaps() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity e = i.next();
            if (e.getCrew().doConsoleRoleSwap()) {
                final Crew crew = e.getCrew();
                final int current = crew.getCurrentPilotIndex();
                Report r = new Report(5560);
                r.subject = e.getId();
                r.add(crew.getNameAndRole(current));
                r.add(crew.getCrewType().getRoleName(0));
                r.addDesc(e);
                gameManager.addReport(r);
            }
        }
    }

    /*
     * Resolve any outstanding self destructions...
     */
    private void resolveSelfDestruct() {
        for (Entity e : game.getEntitiesVector()) {
            if (e.getSelfDestructing()) {
                e.setSelfDestructing(false);
                e.setSelfDestructInitiated(true);
                Report r = new Report(5535, Report.PUBLIC);
                r.subject = e.getId();
                r.addDesc(e);
                gameManager.addReport(r);
            }
        }
    }

    /*
     * Resolve any outstanding crashes from shutting down and being airborne
     * VTOL or WiGE...
     */
    private void resolveShutdownCrashes() {
        for (Entity e : game.getEntitiesVector()) {
            if (e.isShutDown() && e.isAirborneVTOLorWIGE()
                    && !(e.isDestroyed() || e.isDoomed())) {
                Tank t = (Tank) e;
                t.immobilize();
                gameManager.addReport(gameManager.forceLandVTOLorWiGE(t));
            }
        }
    }

    /**
     * checks if IndustrialMechs should die because they moved into to-deep
     * water last round
     */
    private void checkForIndustrialWaterDeath() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if ((null == entity.getPosition()) || entity.isOffBoard()) {
                // If it's not on the board - aboard something else, for
                // example...
                continue;
            }
            if ((entity instanceof Mech) && ((Mech) entity).isIndustrial()
                    && ((Mech) entity).shouldDieAtEndOfTurnBecauseOfWater()) {
                gameManager.addReport(gameManager.destroyEntity(entity,
                        "being in water without environmental shielding", true,
                        true));
            }

        }
    }

    private void checkForIndustrialEndOfTurn() {
        checkForIndustrialWaterDeath();
        checkForIndustrialUnstall();
        checkForIndustrialCrit(); // This might hit an actuator or gyro, so...
        gameManager.addReport(gameManager.resolvePilotingRolls());
    }

    private void checkForIndustrialUnstall() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext(); ) {
            final Entity entity = i.next();
            entity.checkUnstall(gameManager.getvPhaseReport());
        }
    }

    /**
     * industrial mechs might need to check for critical damage
     */
    private void checkForIndustrialCrit() {
        for (Iterator<Entity> i = game.getEntities(); i.hasNext();) {
            final Entity entity = i.next();
            if ((entity instanceof Mech) && ((Mech) entity).isIndustrial()) {
                Mech mech = (Mech) entity;
                // should we check for critical damage?
                if (mech.isCheckForCrit()) {
                    Report r = new Report(5530);
                    r.addDesc(mech);
                    r.subject = mech.getId();
                    r.newlines = 0;
                    gameManager.addReport(r);
                    // for being hit by a physical weapon
                    if (mech.getLevelsFallen() == 0) {
                        r = new Report(5531);
                        r.subject = mech.getId();
                        // or for falling
                    } else {
                        r = new Report(5532);
                        r.subject = mech.getId();
                        r.add(mech.getLevelsFallen());
                    }
                    gameManager.addReport(r);
                    HitData newHit = mech.rollHitLocation(ToHitData.HIT_NORMAL, ToHitData.SIDE_FRONT);
                    gameManager.addReport(gameManager.criticalEntity(mech,
                            newHit.getLocation(), newHit.isRear(),
                            mech.getLevelsFallen(), 0));
                }
            }
        }
    }

    /**
     * Checks if ejected MechWarriors are eligible to be picked up, and if so,
     * captures them or picks them up
     */
    private void resolveMechWarriorPickUp() {
        Report r;

        // fetch all mechWarriors that are not picked up
        Iterator<Entity> mechWarriors = game.getSelectedEntities(entity -> {
            if (entity instanceof MechWarrior) {
                MechWarrior mw = (MechWarrior) entity;
                return (mw.getPickedUpById() == Entity.NONE)
                        && !mw.isDoomed()
                        && (mw.getTransportId() == Entity.NONE);
            }
            return false;
        });
        // loop through them, check if they are in a hex occupied by another
        // unit
        while (mechWarriors.hasNext()) {
            boolean pickedUp = false;
            MechWarrior e = (MechWarrior) mechWarriors.next();
            // Check for owner entities first...
            for (Entity pe : game.getEntitiesVector(e.getPosition())) {
                if (pe.isDoomed() || pe.isShutDown() || pe.getCrew().isUnconscious()
                        || (pe.isAirborne() && !pe.isSpaceborne())
                        || (pe.getElevation() != e.getElevation())
                        || (pe.getOwnerId() != e.getOwnerId())
                        || (pe.getId() == e.getId())) {
                    continue;
                }
                if (pe instanceof MechWarrior) {
                    // MWs have a beer together
                    r = new Report(6415, Report.PUBLIC);
                    r.add(pe.getDisplayName());
                    gameManager.addReport(r);
                    continue;
                }
                // Pick up the unit.
                pe.pickUp(e);
                // The picked unit is being carried by the loader.
                e.setPickedUpById(pe.getId());
                e.setPickedUpByExternalId(pe.getExternalIdAsString());
                pickedUp = true;
                r = new Report(6420, Report.PUBLIC);
                r.add(e.getDisplayName());
                r.addDesc(pe);
                gameManager.addReport(r);
                break;
            }
            // Check for allied entities next...
            if (!pickedUp) {
                for (Entity pe : game.getEntitiesVector(e.getPosition())) {
                    if (pe.isDoomed() || pe.isShutDown() || pe.getCrew().isUnconscious()
                            || (pe.isAirborne() && !pe.isSpaceborne())
                            || (pe.getElevation() != e.getElevation())
                            || (pe.getOwnerId() == e.getOwnerId()) || (pe.getId() == e.getId())
                            || (pe.getOwner().getTeam() == Player.TEAM_NONE)
                            || (pe.getOwner().getTeam() != e.getOwner().getTeam())) {
                        continue;
                    }
                    if (pe instanceof MechWarrior) {
                        // MWs have a beer together
                        r = new Report(6416, Report.PUBLIC);
                        r.add(pe.getDisplayName());
                        gameManager.addReport(r);
                        continue;
                    }
                    // Pick up the unit.
                    pe.pickUp(e);
                    // The picked unit is being carried by the loader.
                    e.setPickedUpById(pe.getId());
                    e.setPickedUpByExternalId(pe.getExternalIdAsString());
                    pickedUp = true;
                    r = new Report(6420, Report.PUBLIC);
                    r.add(e.getDisplayName());
                    r.addDesc(pe);
                    gameManager.addReport(r);
                    break;
                }
            }
            // Now check for anyone else...
            if (!pickedUp) {
                Iterator<Entity> pickupEnemyEntities = game.getEnemyEntities(e.getPosition(), e);
                while (pickupEnemyEntities.hasNext()) {
                    Entity pe = pickupEnemyEntities.next();
                    if (pe.isDoomed() || pe.isShutDown() || pe.getCrew().isUnconscious()
                            || pe.isAirborne() || (pe.getElevation() != e.getElevation())) {
                        continue;
                    }
                    if (pe instanceof MechWarrior) {
                        // MWs have a beer together
                        r = new Report(6417, Report.PUBLIC);
                        r.add(pe.getDisplayName());
                        gameManager.addReport(r);
                        continue;
                    }
                    // Capture the unit.
                    pe.pickUp(e);
                    // The captured unit is being carried by the loader.
                    e.setCaptured(true);
                    e.setPickedUpById(pe.getId());
                    e.setPickedUpByExternalId(pe.getExternalIdAsString());
                    pickedUp = true;
                    r = new Report(6420, Report.PUBLIC);
                    r.add(e.getDisplayName());
                    r.addDesc(pe);
                    gameManager.addReport(r);
                    break;
                }
            }
            if (pickedUp) {
                // Remove the picked-up unit from the screen.
                e.setPosition(null);
                // Update the loaded unit.
                gameManager.entityUpdate(e.getId());
            }
        }
    }

    /**
     * Remove all iNarc pods from all vehicles that did not move and shoot this
     * round NOTE: this is not quite what the rules say, the player should be
     * able to choose whether or not to remove all iNarc Pods that are attached.
     */
    private void resolveVeeINarcPodRemoval() {
        Iterator<Entity> vees = game.getSelectedEntities(
                entity -> (entity instanceof Tank) && (entity.mpUsed == 0));
        boolean canSwipePods;
        while (vees.hasNext()) {
            canSwipePods = true;
            Entity entity = vees.next();
            for (int i = 0; i <= 5; i++) {
                if (entity.weaponFiredFrom(i)) {
                    canSwipePods = false;
                }
            }
            if (((Tank) entity).getStunnedTurns() > 0) {
                canSwipePods = false;
            }
            if (canSwipePods && entity.hasINarcPodsAttached()
                    && entity.getCrew().isActive()) {
                entity.removeAllINarcPods();
                Report r = new Report(2345);
                r.addDesc(entity);
                gameManager.addReport(r);
            }
        }
    }

    /**
     * Resolve any Infantry units which are fortifying hexes
     */
    void resolveFortify() {
        Report r;
        for (Entity ent : game.getEntitiesVector()) {
            if (ent instanceof Infantry) {
                Infantry inf = (Infantry) ent;
                int dig = inf.getDugIn();
                if (dig == Infantry.DUG_IN_WORKING) {
                    r = new Report(5300);
                    r.addDesc(inf);
                    r.subject = inf.getId();
                    gameManager.addReport(r);
                } else if (dig == Infantry.DUG_IN_FORTIFYING3) {
                    Coords c = inf.getPosition();
                    r = new Report(5305);
                    r.addDesc(inf);
                    r.add(c.getBoardNum());
                    r.subject = inf.getId();
                    gameManager.addReport(r);
                    // fortification complete - add to map
                    Hex hex = game.getBoard().getHex(c);
                    hex.addTerrain(new Terrain(Terrains.FORTIFIED, 1));
                    gameManager.sendChangedHex(c);
                    // Clear the dig in for any units in same hex, since they
                    // get it for free by fort
                    for (Entity ent2 : game.getEntitiesVector(c)) {
                        if (ent2 instanceof Infantry) {
                            Infantry inf2 = (Infantry) ent2;
                            inf2.setDugIn(Infantry.DUG_IN_NONE);
                        }
                    }
                }
            }

            if (ent instanceof Tank) {
                Tank tnk = (Tank) ent;
                int dig = tnk.getDugIn();
                if (dig == Tank.DUG_IN_FORTIFYING3) {
                    Coords c = tnk.getPosition();
                    r = new Report(5305);
                    r.addDesc(tnk);
                    r.add(c.getBoardNum());
                    r.subject = tnk.getId();
                    gameManager.addReport(r);
                    // Fort complete, now add it to the map
                    Hex hex = game.getBoard().getHex(c);
                    hex.addTerrain(new Terrain(Terrains.FORTIFIED, 1));
                    gameManager.sendChangedHex(c);
                    tnk.setDugIn(Tank.DUG_IN_NONE);
                    // Clear the dig in for any units in same hex, since they
                    // get it for free by fort
                    for (Entity ent2 : game.getEntitiesVector(c)) {
                        if (ent2 instanceof Infantry) {
                            Infantry inf2 = (Infantry) ent2;
                            inf2.setDugIn(Infantry.DUG_IN_NONE);
                        }
                    }
                }
            }
        }
    }

    private void entityStatusReport() {
        if (game.getOptions().booleanOption(OptionsConstants.BASE_SUPPRESS_UNIT_TOOLTIP_IN_REPORT_LOG)) {
            return;
        }

        List<Report> reports = new ArrayList<>();
        List<Entity> entities = game.getEntitiesVector().stream()
                .filter(e -> (e.isDeployed() && e.getPosition() != null))
                .collect(Collectors.toList());
        Comparator<Entity> comp = Comparator.comparing((Entity e) -> e.getOwner().getTeam());
        comp = comp.thenComparing((Entity e) -> e.getOwner().getName());
        comp = comp.thenComparing((Entity e) -> e.getDisplayName());
        entities.sort(comp);

        // turn off preformatted text for unit tool tip
        Report r = new Report(1230, Report.PUBLIC);
        r.add("</pre>");
        reports.add(r);

        r = new Report(7600);
        reports.add(r);

        for (Entity e : entities) {
            r = new Report(1231);
            r.subject = e.getId();
            r.addDesc(e);
            String etr = "";
            try {
                etr = UnitToolTip.getEntityTipReport(e).toString();
            } catch (Exception ex) {
                LogManager.getLogger().error("", ex);
            }
            r.add(etr);
            reports.add(r);

            r = new Report(1230, Report.PUBLIC);
            r.add("<BR>");
            reports.add(r);
        }

        // turn preformatted text back on, so that text after will display properly
        r = new Report(1230, Report.PUBLIC);
        r.add("<pre>");
        reports.add(r);

        gameManager.getvPhaseReport().addAll(reports);
    }
}
