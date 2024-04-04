/*
 * Copyright (c) 2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package megamek.common.enums;

import megamek.MegaMek;
import megamek.common.*;
import megamek.common.options.OptionsConstants;
import megamek.server.GameManager;
import megamek.server.phases.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.ResourceBundle;

public enum GamePhase {
    //region Enum Declarations
    UNKNOWN("GamePhase.UNKNOWN.text", UnknownPhase.class),
    LOUNGE("GamePhase.LOUNGE.text", LoungePhase.class),
    SELECTION("GamePhase.SELECTION.text", SelectionPhase.class),
    EXCHANGE("GamePhase.EXCHANGE.text", ExchangePhase.class),
    DEPLOYMENT("GamePhase.DEPLOYMENT.text", DeploymentPhase.class),
    INITIATIVE("GamePhase.INITIATIVE.text", InitiativePhase.class),
    INITIATIVE_REPORT("GamePhase.INITIATIVE_REPORT.text", InitiativeReportPhase.class),
    TARGETING("GamePhase.TARGETING.text", TargetingPhase.class),
    TARGETING_REPORT("GamePhase.TARGETING_REPORT.text", TargetingReportPhase.class),
    PREMOVEMENT("GamePhase.PREMOVEMENT.text", PremovementPhase.class),
    MOVEMENT("GamePhase.MOVEMENT.text", MovementPhase.class),
    MOVEMENT_REPORT("GamePhase.MOVEMENT_REPORT.text", MovementReportPhase.class),
    OFFBOARD("GamePhase.OFFBOARD.text", OffboardPhase.class),
    OFFBOARD_REPORT("GamePhase.OFFBOARD_REPORT.text", OffboardReportPhase.class),
    POINTBLANK_SHOT("GamePhase.POINTBLANK_SHOT.text", PointblankPhase.class), // Fake phase only reached through hidden units
    PREFIRING("GamePhase.PREFIRING.text", PrefiringPhase.class),
    FIRING("GamePhase.FIRING.text", FiringPhase.class),
    FIRING_REPORT("GamePhase.FIRING_REPORT.text", FiringReportPhase.class),
    PHYSICAL("GamePhase.PHYSICAL.text", PhysicalPhase.class),
    PHYSICAL_REPORT("GamePhase.PHYSICAL_REPORT.text", PhysicalReportPhase.class),
    END("GamePhase.END.text", EndPhase.class),
    END_REPORT("GamePhase.END_REPORT.text", EndReportPhase.class),
    VICTORY("GamePhase.VICTORY.text", VictoryPhase.class),
    DEPLOY_MINEFIELDS("GamePhase.DEPLOY_MINEFIELDS.text", DeployMinefieldsPhase.class),
    STARTING_SCENARIO("GamePhase.STARTING_SCENARIO.text", StartingScenarioPhase.class),
    SET_ARTILLERY_AUTOHIT_HEXES("GamePhase.SET_ARTILLERY_AUTOHIT_HEXES.text", SetArtilleryAutoHitHexesPhase.class);
    //endregion Enum Declarations

    //region Variable Declarations
    private final String name;
    private final Class<? extends AbstractGamePhase> phaseClass;
    private AbstractGamePhase phase;
    //endregion Variable Declarations

    //region Constructors
    GamePhase(final String name, Class<? extends AbstractGamePhase> phaseClass)  {
        final ResourceBundle resources = ResourceBundle.getBundle("megamek.common.messages",
                MegaMek.getMMOptions().getLocale());
        this.name = resources.getString(name);
        this.phaseClass = phaseClass;
    }
    //endregion Constructors

    public AbstractGamePhase getPhase(GameManager manager) {
        try {
            if (phase == null) {
                phase = phaseClass.getConstructor(GameManager.class).newInstance(manager);
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
        return phase;
    }

    //region Boolean Comparison Methods
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    public boolean isLounge() {
        return this == LOUNGE;
    }

    public boolean isSelection() {
        return this == SELECTION;
    }

    public boolean isExchange() {
        return this == EXCHANGE;
    }

    public boolean isDeployment() {
        return this == DEPLOYMENT;
    }

    public boolean isInitiative() {
        return this == INITIATIVE;
    }

    public boolean isInitiativeReport() {
        return this == INITIATIVE_REPORT;
    }

    public boolean isTargeting() {
        return this == TARGETING;
    }

    public boolean isTargetingReport() {
        return this == TARGETING_REPORT;
    }

    public boolean isPremovement() {
        return this == PREMOVEMENT;
    }

    public boolean isMovement() {
        return this == MOVEMENT;
    }

    public boolean isMovementReport() {
        return this == MOVEMENT_REPORT;
    }

    public boolean isOffboard() {
        return this == OFFBOARD;
    }

    public boolean isOffboardReport() {
        return this == OFFBOARD_REPORT;
    }

    public boolean isPointblankShot() {
        return this == POINTBLANK_SHOT;
    }

    public boolean isPrefiring() {
        return this == PREFIRING;
    }

    public boolean isFiring() {
        return this == FIRING;
    }

    public boolean isFiringReport() {
        return this == FIRING_REPORT;
    }

    public boolean isPhysical() {
        return this == PHYSICAL;
    }

    public boolean isPhysicalReport() {
        return this == PHYSICAL_REPORT;
    }

    public boolean isEnd() {
        return this == END;
    }

    public boolean isEndReport() {
        return this == END_REPORT;
    }

    public boolean isVictory() {
        return this == VICTORY;
    }

    public boolean isDeployMinefields() {
        return this == DEPLOY_MINEFIELDS;
    }

    public boolean isStartingScenario() {
        return this == STARTING_SCENARIO;
    }

    public boolean isSetArtilleryAutohitHexes() {
        return this == SET_ARTILLERY_AUTOHIT_HEXES;
    }

    public boolean isReport() {
        switch (this) {
            case INITIATIVE_REPORT:
            case TARGETING_REPORT:
            case MOVEMENT_REPORT:
            case OFFBOARD_REPORT:
            case FIRING_REPORT:
            case PHYSICAL_REPORT:
            case END_REPORT:
            case VICTORY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true when this phase shows the game map.
     */
    public boolean isOnMap() {
        switch (this) {
            case DEPLOYMENT:
            case TARGETING:
            case PREMOVEMENT:
            case MOVEMENT:
            case OFFBOARD:
            case PREFIRING:
            case FIRING:
            case PHYSICAL:
            case DEPLOY_MINEFIELDS:
            case SET_ARTILLERY_AUTOHIT_HEXES:
            case INITIATIVE_REPORT:
            case TARGETING_REPORT:
            case MOVEMENT_REPORT:
            case OFFBOARD_REPORT:
            case FIRING_REPORT:
            case PHYSICAL_REPORT:
            case END_REPORT:
            case VICTORY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Should we play this phase or skip it?
     */
    public boolean isPlayable(final Game game) {
        switch (this) {
            case INITIATIVE:
            case END:
                return false;
            case DEPLOYMENT:
            case TARGETING:
            case PREMOVEMENT:
            case MOVEMENT:
            case PREFIRING:
            case FIRING:
            case PHYSICAL:
            case DEPLOY_MINEFIELDS:
            case SET_ARTILLERY_AUTOHIT_HEXES:
                return game.hasMoreTurns();
            case OFFBOARD:
                return isOffboardPlayable(game);
            default:
                return true;
        }
    }

    /**
     * Skip offboard phase, if there is no homing / semiguided ammo in play
     */
    private boolean isOffboardPlayable(final Game game) {
        if (!game.hasMoreTurns()) {
            return false;
        }

        for (final Entity entity : game.getEntitiesVector()) {
            for (final Mounted mounted : entity.getAmmo()) {
                AmmoType ammoType = (AmmoType) mounted.getType();

                // per errata, TAG will spot for LRMs and such
                if ((ammoType.getAmmoType() == AmmoType.T_LRM)
                        || (ammoType.getAmmoType() == AmmoType.T_LRM_IMP)
                        || (ammoType.getAmmoType() == AmmoType.T_MML)
                        || (ammoType.getAmmoType() == AmmoType.T_NLRM)
                        || (ammoType.getAmmoType() == AmmoType.T_MEK_MORTAR)) {
                    return true;
                }

                if (((ammoType.getAmmoType() == AmmoType.T_ARROW_IV)
                        || (ammoType.getAmmoType() == AmmoType.T_LONG_TOM)
                        || (ammoType.getAmmoType() == AmmoType.T_SNIPER)
                        || (ammoType.getAmmoType() == AmmoType.T_THUMPER))
                        && (ammoType.getMunitionType().contains(AmmoType.Munitions.M_HOMING))) {
                    return true;
                }
            }

            if (entity.getBombs().stream().anyMatch(bomb -> !bomb.isDestroyed()
                    && (bomb.getUsableShotsLeft() > 0)
                    && (((BombType) bomb.getType()).getBombType() == BombType.B_LG))) {
                return true;
            }
        }

        // Go through all current attacks, checking if any use homing ammunition. If so, the phase
        // is playable. This prevents issues from aerospace homing artillery with the aerospace
        // unit having left the field already, for example
        return game.getAttacksVector().stream()
                .map(attackHandler -> attackHandler.getWaa())
                .filter(Objects::nonNull).anyMatch(waa -> waa.getAmmoMunitionType().contains(AmmoType.Munitions.M_HOMING));
    }

    /**
     * @return true if this phase has turns. If false, the phase is simply waiting for everybody
     * to declare "done".
     */
    public boolean hasTurns() {
        switch (this) {
            case SET_ARTILLERY_AUTOHIT_HEXES:
            case DEPLOY_MINEFIELDS:
            case DEPLOYMENT:
            case PREMOVEMENT:
            case MOVEMENT:
            case PREFIRING:
            case FIRING:
            case PHYSICAL:
            case TARGETING:
            case OFFBOARD:
                return true;
            default:
                return false;
        }
    }

    /**
     * @param game The current {@link Game}
     * @return true if this phase is simultaneous
     */
    public boolean isSimultaneous(final Game game) {
        switch (this) {
            case DEPLOYMENT:
                return game.getOptions().booleanOption(OptionsConstants.INIT_SIMULTANEOUS_DEPLOYMENT);
            case MOVEMENT:
                return game.getOptions().booleanOption(OptionsConstants.INIT_SIMULTANEOUS_MOVEMENT);
            case FIRING:
                return game.getOptions().booleanOption(OptionsConstants.INIT_SIMULTANEOUS_FIRING);
            case PHYSICAL:
                return game.getOptions().booleanOption(OptionsConstants.INIT_SIMULTANEOUS_PHYSICAL);
            case TARGETING:
            case OFFBOARD:
                return game.getOptions().booleanOption(OptionsConstants.INIT_SIMULTANEOUS_TARGETING);
            case PREMOVEMENT:
            case PREFIRING:
                return true;
            default:
                return false;
        }
    }

    public boolean isDuringOrAfter(final GamePhase otherPhase) {
        return compareTo(otherPhase) >= 0;
    }

    public boolean isBefore(final GamePhase otherPhase) {
        return compareTo(otherPhase) < 0;
    }
    //endregion Boolean Comparison Methods

    @Override
    public String toString() {
        return name;
    }
}
