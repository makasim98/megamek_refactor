/* MegaMek - Copyright (C) 2004,2005 Ben Mazur (bmazur@sev.org)
 * 
 *  This program is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU General Public License as published by the Free 
 *  Software Foundation; either version 2 of the License, or (at your option) 
 *  any later version.
 * 
 *  This program is distributed in the hope that it will be useful, but 
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 *  for more details.
 */
/*
 * Created on Sep 25, 2004
 *
 */
package megamek.common.weapons.capitalweapons;

import megamek.common.AmmoType;
import megamek.common.IGame;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.BarracudaHandler;
import megamek.server.Server;

/**
 * @author Jay Lawson
 */
public class CapMissBarracudaWeapon extends CapitalMissileWeapon {
    /**
     * 
     */
    private static final long serialVersionUID = 8756042527483383101L;

    /**
     * 
     */
    public CapMissBarracudaWeapon() {
        super();
        this.name = "Capital Missile Launcher (Barracuda)";
        this.setInternalName(this.name);
        this.addLookupName("Barracuda");
        this.heat = 10;
        this.damage = 2;
        this.ammoType = AmmoType.T_BARRACUDA;
        this.shortRange = 20;
        this.mediumRange = 30;
        this.longRange = 40;
        this.extremeRange = 50;
        this.tonnage = 90.0f;
        this.bv = 522;
        this.cost = 90000;
        this.atClass = CLASS_CAPITAL_MISSILE;
        this.shortAV = 2;
        this.medAV = 2;
        this.longAV = 2;
        this.extAV = 2;
        this.missileArmor = 20;
        this.maxRange = RANGE_EXT;
        this.toHitModifier = -2;
        rulesRefs = "210,TM";
        techAdvancement.setTechBase(TECH_BASE_ALL)
        	.setIntroLevel(false)
        	.setUnofficial(false)
            .setTechRating(RATING_E)
            .setAvailability(RATING_C, RATING_E, RATING_E, RATING_D)
            .setISAdvancement(2200, 2305, 3055, 2950, 3051)
            .setISApproximate(true, false, false, true, false)
            .setClanAdvancement(2200, 2305, 3055, DATE_NONE, DATE_NONE)
            .setClanApproximate(true, false, false,false, false)
            .setPrototypeFactions(F_TA)
            .setProductionFactions(F_TA)
            .setReintroductionFactions(F_FS,F_LC);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * megamek.common.weapons.Weapon#getCorrectHandler(megamek.common.ToHitData,
     * megamek.common.actions.WeaponAttackAction, megamek.common.IGame)
     */
    @Override
    protected AttackHandler getCorrectHandler(ToHitData toHit,
            WeaponAttackAction waa, IGame game, Server server) {
        return new BarracudaHandler(toHit, waa, game, server);
    }
}
