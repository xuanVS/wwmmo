package au.com.codeka.common.msghelpers;

import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.FleetUpgrade;

public class FleetHelper {
    /**
     * Gets the upgrade with the given ID from the given fleet, or null if the fleet doesn't have
     * it.
     */
    public static FleetUpgrade getFleetUpgrade(Fleet fleet, String upgradeID) {
        for (FleetUpgrade fleetUpgrade : fleet.upgrades) {
            if (fleetUpgrade.upgrade_id.equals(upgradeID)) {
                return fleetUpgrade;
            }
        }
        return null;
    }
}
