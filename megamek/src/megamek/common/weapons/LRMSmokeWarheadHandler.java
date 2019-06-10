/**
 * MegaMek - Copyright (C) 2005 Ben Mazur (bmazur@sev.org)
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
package megamek.common.weapons;

import java.util.Vector;

import megamek.common.AmmoType;
import megamek.common.Compute;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.IGame;
import megamek.common.Report;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.server.Server;
import megamek.server.SmokeCloud;

/**
 * @author FogHat
 */
public class LRMSmokeWarheadHandler extends LRMHandler {

    /**
     *
     */

    private static final long serialVersionUID = -30934685350251837L;

    /**
     * @param t
     * @param w
     * @param g
     * @param s
     */
    public LRMSmokeWarheadHandler(ToHitData t, WeaponAttackAction w, IGame g,
            Server s) {
        super(t, w, g, s);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * megamek.common.weapons.WeaponHandler#specialResolution(java.util.Vector,
     * megamek.common.Entity, boolean)
     */
    @Override
    protected boolean specialResolution(Vector<Report> vPhaseReport,
            Entity entityTarget) {
        Coords coords = target.getPosition();
        Coords center = coords;

        AmmoType atype = (AmmoType) ammo.getType();

        if (!bMissed) {
            Report r = new Report(3190);
            r.subject = subjectId;
            r.player = ae.getOwnerId();
            r.add(coords.getBoardNum());
            vPhaseReport.addElement(r);
        } else {
            // scatterable LRMs scatter like dive bombing
            coords = Compute.scatter(coords, 1);
            if (game.getBoard().contains(coords)) {
                // misses and scatters to another hex
                Report r = new Report(3195);
                r.subject = subjectId;
                r.add(coords.getBoardNum());
                vPhaseReport.addElement(r);
            } else {
                // misses and scatters off-board
                Report r = new Report(3200);
                r.subject = subjectId;
                vPhaseReport.addElement(r);
                return !bMissed;
            }
        }

        // Handle munitions.
        if (atype.getMunitionType() == AmmoType.M_SMOKE_WARHEAD) {
            int damage = wtype.getRackSize() * calcDamagePerHit();
            int radius = (int) Math.ceil(damage / 10.0);
            int smokeType = SmokeCloud.SMOKE_LIGHT;
            if (damage > 5) {
                smokeType = SmokeCloud.SMOKE_HEAVY;
            }
            server.deliverMissileSmoke(center, smokeType, vPhaseReport);
            //Now handle the radius for larger racks. According to the rules on TOp371, you shouldn't be able to 
            //have a radius larger than 2 with an LRM20.
            if (radius > 1) {
                for (int dir = 0; dir <= 5; dir++) {
                    Coords tempcoords = coords.translated(dir);
                    if (game.getBoard().contains(tempcoords)) {
                        server.deliverMissileSmoke(tempcoords, smokeType, vPhaseReport);
                    }
                }
            }
        }
        return true;
    }
}
