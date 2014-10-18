package au.com.codeka.common.simulation;

import org.eclipse.jdt.annotation.Nullable;
import org.joda.time.DateTime;

import au.com.codeka.common.messages.Fleet;

public class Helper {
    public static boolean sameEmpire(String keyOne, String keyTwo) {
        if (keyOne == null && keyTwo == null) {
            return true;
        }
        if (keyOne == null || keyTwo == null) {
            return false;
        }
        return keyOne.equals(keyTwo);
    }

    public static boolean isFriendly(FleetGroupStatus fleetGroup1, FleetGroupStatus fleetGroup2) {
        return isFriendly(fleetGroup1.fleets.get(0), fleetGroup2.fleets.get(0));
    }

    public static boolean isFriendly(FleetStatus fleet1, FleetStatus fleet2) {
        return isFriendly(fleet1.getEmpireKey(), fleet2.getEmpireKey(), fleet1.getAllianceID(),
                fleet2.getAllianceID());
    }

    public static boolean isFriendly(Fleet fleet1, Fleet fleet2) {
        return isFriendly(fleet1.empire_key, fleet2.empire_key, fleet1.alliance_id,
                fleet2.alliance_id);
    }

    public static boolean isFriendly(@Nullable String empireKey1, @Nullable String empireKey2,
            @Nullable Integer allianceID1, @Nullable Integer alliancID2) {
        if (empireKey1 == null && empireKey2 == null) {
            // if they're both native (i.e. no empire key) they they're friendly
            return true;
        }
        if (empireKey1 == null || empireKey2 == null) {
            // if one is native and one is non-native, then they're not friendly
            return false;
        }
        if (empireKey1.equals(empireKey2)) {
            // if they're both the same empire they're friendly
            return true;
        }

        // if either one is not in an alliance, then they're not friendly
        if (allianceID1 == null || alliancID2 == null) {
            return false;
        }

        // if they're in the same alliance, they they're friendly
        if (allianceID1 == alliancID2) {
            return true;
        }

        // otherwise, they're in different alliances: not friendly
        return false;
    }

    public static boolean isDestroyed(Fleet fleet, DateTime now) {
        if (fleet.time_destroyed != null && now.isBefore(new DateTime(fleet.time_destroyed * 1000))) {
            return true;
        }
        return false;
    }

}
