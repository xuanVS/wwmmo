package au.com.codeka.common.msghelpers;


import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import au.com.codeka.common.messages.Building;
import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.Star;

public class StarHelper {
    public static Colony getColony(Star star, String colonyKey) {
        for (Colony colony : star.colonies) {
            if (colony.key.equals(colonyKey)) {
                return colony;
            }
        }
        return null;
    }

    /** Returns an iterator of buildings in the given colony. */
    public static Iterable<Building> getBuildings(Star star, final Colony colony) {
        return Iterables.filter(star.buildings, new Predicate<Building>() {
            @Override
            public boolean apply(Building building) {
                return building.colony_key.equals(colony.key);
            }
        });
    }
}
