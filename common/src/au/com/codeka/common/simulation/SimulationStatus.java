package au.com.codeka.common.simulation;

import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.Star;

/**
 * Class that maintains the "diff" between the current {@link Star} and what the star will be
 * like once the simulation is complete.
 */
public class SimulationStatus {
    private final Simulator.LogHandler logHandler;
    private final Star star;

    public final Map<String, EmpireStatus> empires;
    public CombatStatus combatStatus;

    public SimulationStatus(Star star, Simulator.LogHandler logHandler) {
        this.star = star;
        this.logHandler = logHandler;

        empires = new TreeMap<String, EmpireStatus>();
        for (Colony colony : star.colonies) {
            if (!empires.containsKey(colony.empire_key)) {
                empires.put(colony.empire_key, new EmpireStatus(star, colony.empire_key));
            }
        }
    }

    public Star getStar() {
        return star;
    }

    /** Prepare to simulate combat. Returns {@code false} if there's no combat to simulate. */
    public boolean prepareCombat(DateTime now) {
        combatStatus = new CombatStatus(star, now);
        return combatStatus.hasAttackingFleets(now);
    }

    /** Gets the {@link DateTime} we should begin the simulation from. */
    public DateTime getSimulateStartTime() {
        DateTime lastSimulation = null;
        if (star.last_simulation != null) {
            lastSimulation = new DateTime(star.last_simulation * 1000);
        }
        if (lastSimulation == null) {
            for (Fleet fleet : star.fleets) {
                DateTime fleetStateStartTime = new DateTime(fleet.state_start_time * 1000);
                if (lastSimulation == null || fleetStateStartTime.compareTo(lastSimulation) < 0) {
                    lastSimulation = fleetStateStartTime;
                }
            }
        }

        // if there's only native colonies, don't bother simulating from more than
        // 24 hours ago. The native colonies will generally be in a steady state
        DateTime oneDayAgo = DateTime.now().minusHours(24);
        if (lastSimulation != null && lastSimulation.isBefore(oneDayAgo)) {
            log("Last simulation more than on day ago, checking whether there are any non-native colonies.");
            boolean onlyNativeColonies = true;
            for (Colony colony : star.colonies) {
                if (colony.empire_key != null) {
                    onlyNativeColonies = false;
                }
            }
            for (Fleet fleet : star.fleets) {
                if (fleet.empire_key != null) {
                    onlyNativeColonies = false;
                }
            }
            if (onlyNativeColonies) {
                log("No non-native colonies detected, simulating only 24 hours in the past.");
                lastSimulation = oneDayAgo;
            }
        }

        return lastSimulation;
    }

    private void log(String message) {
        if (logHandler != null) {
            logHandler.log(message);
        }
    }
}
