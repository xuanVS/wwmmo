package au.com.codeka.common.simulation;

import java.util.ArrayList;
import java.util.List;

import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.model.BaseDesignManager;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.model.ShipDesign;
import au.com.codeka.common.model.ShipEffect;

/**
 * Represents the simulated status of a group of fleets of the same kind/stance/etc.
 * <p>
 * If a fleet is represented in this class, then it is -- by definition -- attacking, since this
 * is only used for combat simulations and therefore only attacking fleets matter.
 * <p>
 * Fleets are group together by kind so that when attacking/defending, there is no difference
 * between 1 fleet of 100 ships and 100 fleets of 1 ship each.
 */
public class FleetGroupStatus {
    /** The individual fleets in this {@link FleetGroupStatus}. */
    private List<FleetStatus> fleets;

    public Fleet.FLEET_STATE state;
    public float numShips;

    public FleetGroupStatus(FleetStatus fleet) {
        fleets = new ArrayList<FleetStatus>();
        state = fleet.getState();
        addFleet(fleet);
    }

    public void addFleet(FleetStatus fleet) {
        fleets.add(fleet);
        numShips += fleet.getNumShips();
    }

    public void onAttacked(Star star) {
        ShipDesign design = getDesign();
        for (FleetStatus fleet : fleets) {
            if (fleet.getState() == Fleet.FLEET_STATE.IDLE) {
                ArrayList<ShipEffect> effects = design.getEffects(ShipEffect.class);
                for (ShipEffect effect : effects) {
                    effect.onAttacked(star, fleet);
                }
            }
        }

    }

    public boolean isInGroup(FleetStatus fleet) {
        return Helper.isFriendly(fleets.get(0), fleet)
            && fleets.get(0).getDesignName().equals(fleet.getDesignName())
            && fleets.get(0).getStance() == fleet.getStance()
            && fleets.get(0).getState() == fleet.getState();
    }

    public ShipDesign getDesign() {
        return (ShipDesign) BaseDesignManager.i.getDesign(
                DesignKind.SHIP, fleets.get(0).getDesignName());
    }
}
