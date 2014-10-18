package au.com.codeka.common.simulation;

import org.eclipse.jdt.annotation.Nullable;
import org.joda.time.DateTime;

import au.com.codeka.common.messages.Fleet;

/**
 * Represents the simulated status of a fleet (or actually, group of fleets).
 * <p>
 * If a fleet is represented in this class, then it is -- by definition -- attacking, since this
 * is only used for combat simulations and therefore only attacking fleets matter.
 */
public class FleetStatus {
    private Fleet fleet;

    public FleetStatus(Fleet fleet) {
        this.fleet = fleet;
    }

    @Nullable
    public String getEmpireKey() {
        return fleet.empire_key;
    }

    @Nullable
    public Integer getAllianceID() {
        return fleet.alliance_id;
    }

    public String getDesignName() {
        return fleet.design_name;
    }

    public Fleet.FLEET_STATE getState() {
        return fleet.state;
    }

    public Fleet.FLEET_STANCE getStance() {
        return fleet.stance;
    }

    public DateTime getStateStartTime() {
        return new DateTime(fleet.state_start_time * 1000);
    }

    public boolean isDestroyed(DateTime now) {
        return Helper.isDestroyed(fleet, now);
    }

    public float getNumShips() {
        return fleet.num_ships;
    }
}
