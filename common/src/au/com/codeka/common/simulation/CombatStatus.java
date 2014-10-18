package au.com.codeka.common.simulation;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.CombatReport;
import au.com.codeka.common.messages.CombatRound;
import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.msghelpers.FleetHelper;

/** Represents the status of combat. */
public class CombatStatus {
    private final Star star;
    private final CombatReport combatReport;

    public final List<FleetStatus> fleets;
    public final List<FleetGroupStatus> fleetGroups;
    public final List<CombatRound.Builder> rounds;

    public CombatStatus(Star star, DateTime now) {
        this.star = star;
        combatReport = star.current_combat_report;
        rounds = new ArrayList<CombatRound.Builder>();

        if (combatReport != null) {
            long nowSeconds = now.getMillis() / 1000;
            for (CombatRound round : combatReport.rounds) {
                if (round.round_time < nowSeconds) {
                    rounds.add(new CombatRound.Builder(round));
                }
            }
        }

        fleets = new ArrayList<FleetStatus>();
        fleetGroups = new ArrayList<FleetGroupStatus>();

        for (Fleet fleet : star.fleets) {
            // if it's moving, then it's invisible to combat
            if (fleet.state == Fleet.FLEET_STATE.MOVING) {
                continue;
            }

            // if it's got a cloaking device and it's not aggressive, then it's invisible to combat
            if (FleetHelper.hasUpgrade(fleet, "cloak")
                    && fleet.stance != Fleet.FLEET_STANCE.AGGRESSIVE) {
                continue;
            }

            fleets.add(new FleetStatus(fleet));
        }
    }

    CombatRound.Builder beginRound(DateTime now) {
        CombatRound.Builder round = new CombatRound.Builder()
            .star_key(star.key)
            .round_time(now.getMillis() / 1000);
        rounds.add(round);
        return round;
    }

    public boolean hasAttackingFleets(DateTime now) {
        int numAttacking = 0;
        for (FleetStatus fleet : fleets) {
            if (fleet.getState() == Fleet.FLEET_STATE.ATTACKING
                    && !fleet.isDestroyed(now)) {
                numAttacking ++;
            }
        }
        return numAttacking > 0;
    }

    public DateTime getAttackStartTime(DateTime now) {
        DateTime attackStartTime = null;
        for (FleetStatus fleet : fleets) {
            if (fleet.getState() != Fleet.FLEET_STATE.ATTACKING || fleet.isDestroyed(now)) {
                continue;
            }

            if (attackStartTime == null || attackStartTime.isAfter(fleet.getStateStartTime())) {
                attackStartTime = fleet.getStateStartTime();
            }
        }

        if (attackStartTime == null || attackStartTime.isBefore(now)) {
            attackStartTime = now;
        }

        // round up to the next minute
        attackStartTime = new DateTime(attackStartTime.getYear(), attackStartTime.getMonthOfYear(),
                attackStartTime.getDayOfMonth(), attackStartTime.getHourOfDay(),
                attackStartTime.getMinuteOfHour(), 0);
        return attackStartTime.plusMinutes(1);
    }
}
