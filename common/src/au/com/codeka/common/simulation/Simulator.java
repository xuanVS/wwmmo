package au.com.codeka.common.simulation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.Seconds;

import au.com.codeka.common.Log;
import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.CombatRound;
import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.Planet;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.ShipDesign;
import au.com.codeka.common.model.ShipEffect;

/** This class is used to simulate a {@link Star}. */
public class Simulator {
    private final LogHandler logHandler;
    private final boolean predict;
    private DateTime now;

    private static boolean sDebug = false;
    private static int sNumSimulations;
    private static DateTime year2k = new DateTime(2000, 1, 1, 0, 0);

    public Simulator() {
        this(DateTime.now(DateTimeZone.UTC), true, sDebug ? new BasicLogHandler() : null);
    }
    public Simulator(LogHandler log) {
        this(DateTime.now(DateTimeZone.UTC), true, log);
    }
    public Simulator(boolean predict) {
        this(DateTime.now(DateTimeZone.UTC), predict, sDebug ? new BasicLogHandler() : null);
    }
    public Simulator(DateTime now, LogHandler log) {
        this(now, true, log);
    }
    public Simulator(DateTime now, boolean predict, LogHandler logHandler) {
        this.now = now;
        this.predict = predict;
        this.logHandler = logHandler;
    }

    public static int getNumRunningSimulations() {
        return sNumSimulations;
    }

    protected void log(String message) {
        if (logHandler != null) {
            logHandler.log(message);
        }
    }

    /**
     * Return a new instance of {@link Star} that's been simulated up to "now".
     */
    public Star simulate(Star star) {
        sNumSimulations ++;
        log(String.format("Begin simulation for '%s'", star.name));
        SimulationStatus status = new SimulationStatus(star, logHandler);

        Set<String> empireKeys = status.empires.keySet();
        DateTime startTime = status.getSimulateStartTime();
        if (startTime == null) {
            // Nothing worth simulating...
            return new Star.Builder(star).build();
        }

        DateTime endTime = now;

        // if we have less than a few seconds of time to simulate, we'll extend the end time
        // a little to ensure there's no rounding errors and such
        if (endTime.minusSeconds(3).compareTo(startTime) < 0) {
            endTime = startTime.plusSeconds(3);
        }

        // We'll simulate in "prediction mode" for an extra bit of time so that we can get a
        // more accurate estimate of the end time for builds. We won't *record* the population
        // growth and such, just the end time of builds. We'll also record the time that the
        // population drops below a certain threshold so that we can warn the player.
        DateTime predictionTime = endTime.plusHours(24);
        Star predictionStar = null;

        while (true) {
            Duration dt = Duration.standardMinutes(15);
            DateTime stepEndTime = startTime.plus(dt);
            if (stepEndTime.compareTo(endTime) < 0) {
                simulateStepForAllEmpires(dt, startTime, status, empireKeys);
                startTime = stepEndTime;
            } else if (predict && stepEndTime.compareTo(predictionTime) < 0) {
                if (predictionStar == null) {
                    log("--------------------------------------------------");
                    log("Prediction phase beginning...");
                    log("--------------------------------------------------");
                    now = endTime;
                    dt = new Interval(startTime, endTime).toDuration();
                    if (dt.getMillis() > 1000) {
                        // last little bit of the simulation
                        simulateStepForAllEmpires(dt, startTime, star, empireKeys);
                    }
                    startTime = startTime.plus(dt);

                    predictionStar = star.clone();
                    dt = Duration.standardMinutes(15);
                }

                simulateStepForAllEmpires(dt, startTime, predictionStar, empireKeys);
                startTime = stepEndTime;
            } else {
                break;
            }
        }

        if (predictionStar != null) {
            // copy the end times for builds from prediction_star_pb
            for (BaseBuildRequest starBuildRequest : star.getBuildRequests()) {
                for (BaseBuildRequest predictedBuildRequest : predictionStar.getBuildRequests()) {
                    if (starBuildRequest.getKey().equals(predictedBuildRequest.getKey())) {
                        starBuildRequest.setEndTime(predictedBuildRequest.getEndTime());
                    }
                }
            }

            // any fleets that *will be* destroyed, remember the time of their death
            for (BaseFleet fleet : star.getFleets()) {
                for (BaseFleet predictedFleet : predictionStar.getFleets()) {
                    if (fleet.getKey().equals(predictedFleet.getKey())) {
                        log(String.format("Fleet #%s updating timeDestroyed to: %s", fleet.getKey(), predictedFleet.getTimeDestroyed()));
                        fleet.setTimeDestroyed(predictedFleet.getTimeDestroyed());
                    }
                }
            }

            // if the empire is going to run out of resources, save that time as well.
            for (BaseEmpirePresence empirePresence : star.getEmpirePresences()) {
                for (BaseEmpirePresence predictedEmpirePresence : predictionStar.getEmpirePresences()) {
                    if (empirePresence.getKey().equals(predictedEmpirePresence.getKey())) {
                        empirePresence.setGoodsZeroTime(predictedEmpirePresence.getGoodsZeroTime());
                    }
                }
            }

            // also, the prediction combat report (if any) is the one to use
            star.setCombatReport(predictionStar.getCombatReport());
        }

        star.setLastSimulation(endTime);
        sNumSimulations --;
    }


    private void simulateStepForAllEmpires(Duration dt, DateTime now, SimulationStatus status,
            Set<String> empireKeys) {
        log(String.format("- Step [dt=%.2f hrs] [now=%s]", (float)(dt.toStandardSeconds().getSeconds()) / 3600.0f, now));
        for (String empireKey : empireKeys) {
            log(String.format("-- Empire [%s]", empireKey == null ? "Native" : empireKey));
            simulateStep(dt, now, status, empireKey);
        }

        // Don't forget to simulate combat for this step as well (what to do if combat continues
        // after the prediction phase?)
        simulateCombat(status, now, dt);
    }

    private void simulateStep(Duration dt, DateTime now, SimulationStatus status,
            String empireKey) {
        EmpireStatus empireStatus = status.empires.get(empireKey);

        float dtInHours = ((float) dt.getMillis()) / (1000.0f * 3600.0f);

        for (ColonyStatus colonyStatus : empireStatus.colonies) {
            log(String.format("--- Colony [planetIndex=%d] [population=%.2f]",
                    colonyStatus.getPlanetIndex(), colonyStatus.population));
            Planet planet = colonyStatus.getPlanet();

            // calculate the output from farming this turn and add it to the star global
            float goods = colonyStatus.population * colonyStatus.getFarmingFocus()
                    * (planet.farming_congeniality / 100.0f);
            colonyStatus.deltaGoods = goods;
            empireStatus.totalGoods += goods * dtInHours;
            empireStatus.deltaGoodsPerHour += goods;
            log(String.format("    Goods: [delta=%.2f / hr] [this turn=%.2f]", goods,
                    goods * dtInHours));

            // calculate the output from mining this turn and add it to the star global
            float minerals = colonyStatus.population * colonyStatus.getMiningFocus()
                    * (planet.mining_congeniality / 100.0f);
            colonyStatus.deltaMinerals = minerals;
            empireStatus.totalMinerals += minerals * dtInHours;
            empireStatus.deltaMineralsPerHour += minerals;
            log(String.format("    Minerals: [delta=%.2f / hr] [this turn=%.2f]", goods,
                    goods * dtInHours));

            empireStatus.totalPopulation += colonyStatus.population;

            // work out the amount of taxes this colony has generated in the last turn
            float taxPerPopulationPerHour = 0.012f;
            float taxPerHour = taxPerPopulationPerHour * colonyStatus.population;
            float taxThisTurn = taxPerHour * dtInHours;
            log(String.format("    Taxes %.2f + %.2f = %.2f uncollected",
                    colonyStatus.uncollectedTaxes, taxThisTurn,
                    colonyStatus.uncollectedTaxes + taxThisTurn));
            empireStatus.totalTaxPerHour += taxPerHour;
            colonyStatus.uncollectedTaxes = colonyStatus.uncollectedTaxes + taxThisTurn;
        }

        // A second loop though the colonies, once the goods/minerals have been calculated. This
        // way, goods minerals are shared between colonies
        for (ColonyStatus colonyStatus : empireStatus.colonies) {
            // not all build requests will be processed this turn. We divide up the population
            // based on the number of ACTUAL build requests they'll be working on this turn
            int numValidBuildRequests = 0;
            for (BuildStatus buildStatus : colonyStatus.builds) {
                if (buildStatus.startTime.compareTo(now.plus(dt)) > 0) {
                    continue;
                }

                // the end_time will be accurate, since it'll have been updated last step
                if (buildStatus.endTime.compareTo(now) < 0
                        && buildStatus.endTime.compareTo(year2k) > 0) {
                    continue;
                }

                // as long as it's started but hasn't finished, we'll be working on it this turn
                numValidBuildRequests += 1;
            }

            // If we have pending build requests, we'll have to update them as well
            if (numValidBuildRequests > 0) {
                float totalWorkers = colonyStatus.population * colonyStatus.getConstructionFocus();
                float workersPerBuildRequest = totalWorkers / numValidBuildRequests;
                log(String.format("--- Building [buildRequests=%d] [planetIndex=%d] [totalWorker=%.2f]",
                        numValidBuildRequests, colonyStatus.getPlanetIndex(), totalWorkers));

                // OK, we can spare at least ONE population
                if (workersPerBuildRequest < 1.0f) {
                    workersPerBuildRequest = 1.0f;
                }

                // divide the minerals up per build request, so they each get a share. I'm not sure
                // if we should portion minerals out by how 'big' the build request is, but we'll
                // see how this goes initially
                float mineralsPerBuildRequest = empireStatus.totalMinerals / numValidBuildRequests;

                for (BuildStatus buildStatus : colonyStatus.builds) {
                    Design design = buildStatus.getDesign();
                    log(String.format("---- Building [design=%s %s] [count=%d]",
                            design.getDesignKind(), design.getID(), buildStatus.getCount()));

                    DateTime startTime = buildStatus.startTime;
                    if (startTime.compareTo(now.plus(dt)) > 0) {
                        continue;
                    }

                    // the build cost is defined by the original design, or possibly by the upgrade
                    // if that is what it is.
                    Design.BuildCost buildCost = design.getBuildCost();
                    if (buildStatus.getExistingFleetID() != null) {
                        ShipDesign shipDesign = (ShipDesign) design;
                        ShipDesign.Upgrade upgrade = shipDesign.getUpgrade(buildStatus.getUpgradeID());
                        buildCost = upgrade.getBuildCost();
                    }

                    // So the build time the design specifies is the time to build the structure
                    // with 100 workers available. Double the workers and you halve the build time.
                    // Halve the workers and you double the build time.
                    float totalBuildTimeInHours = (float)(buildStatus.getCount()
                            * (double) buildCost.getTimeInSeconds() / 3600.0);
                    totalBuildTimeInHours *= (100.0 / workersPerBuildRequest);

                    // the number of hours of work required, assuming we have all the minerals we need
                    float timeRemainingInHours = (1.0f - buildStatus.progress) * totalBuildTimeInHours;
                    if (timeRemainingInHours < (10.0f / 3600.0f)) {
                        // if there's less than 10 seconds to go, just say it's done now.
                        timeRemainingInHours = 0.0f;
                    }
                    log(String.format("     Time [total=%.2f hrs] [remaining=%.2f hrs]",
                            totalBuildTimeInHours, timeRemainingInHours));

                    float dtUsed = dtInHours;
                    if (startTime.isAfter(now)) {
                        Duration startOffset = new Interval(now, startTime).toDuration();
                        dtUsed -= startOffset.getMillis() / (1000.0f * 3600.0f);
                    }
                    if (dtUsed > timeRemainingInHours) {
                        dtUsed = timeRemainingInHours;
                    }

                    // what is the current amount of time we have now as a percentage of the total build
                    // time?
                    float progressThisTurn = dtUsed / totalBuildTimeInHours;
                    log(String.format("Progress this turn: %f", progressThisTurn));
                    if (progressThisTurn <= 0) {
                        DateTime endTime;
                        timeRemainingInHours = (1.0f - buildStatus.progress) * totalBuildTimeInHours;
                        if (timeRemainingInHours < (10.0f / 3600.0f)) {
                            endTime = now;
                        } else {
                            endTime = now.plus((long)(timeRemainingInHours * 3600.0f * 1000.0f));
                        }
                        if (buildStatus.endTime.compareTo(endTime) > 0) {
                            buildStatus.endTime = endTime;
                        }
                        log("    Finished this turn.");
                        continue;
                    }

                    // work out how many minerals we require for this turn
                    float mineralsRequired = buildStatus.getCount()
                            * buildCost.getCostInMinerals() * progressThisTurn;
                    log(String.format("Cost in minerals: %f", mineralsRequired));
                    if (mineralsRequired > mineralsPerBuildRequest) {
                        // if we don't have enough minerals, we'll just do a percentage of the work
                        // this turn
                        empireStatus.totalMinerals -= mineralsPerBuildRequest;
                        float percentMineralsAvailable = mineralsPerBuildRequest / mineralsRequired;
                        buildStatus.progress = buildStatus.progress
                                + (progressThisTurn * percentMineralsAvailable);
                        log(String.format("     Progress %.4f%% + %.4f%% (this turn, adjusted - %.4f%% originally) ",
                            buildStatus.progress * 100.0f,
                            progressThisTurn * percentMineralsAvailable * 100.0f,
                            progressThisTurn * 100.0f));
                    } else {
                        // awesome, we have enough minerals so we can make some progress. We'll start by
                        // removing the minerals we need from the global pool...
                        empireStatus.totalMinerals -= mineralsRequired;
                        buildStatus.progress = buildStatus.progress + progressThisTurn;
                        log(String.format("     Progress %.4f%% + %.4f%% (this turn)",
                            buildStatus.progress * 100.0f, progressThisTurn * 100.0f));
                    }
                    empireStatus.deltaMineralsPerHour -= mineralsRequired / dtInHours;
                    log(String.format("     Minerals [required=%.2f] [available=%.2f] [available per build=%.2f]",
                            mineralsRequired, empireStatus.totalMinerals, mineralsPerBuildRequest));

                    // adjust the end_time for this turn
                    timeRemainingInHours = (1.0f - buildStatus.progress) * totalBuildTimeInHours;
                    if (timeRemainingInHours > 100000) {
                        // this is waaaaaay too long! it's basically never going to finish, but cap
                        // it to avoid overflow errors.
                        timeRemainingInHours = 100000;
                    }
                    DateTime endTime = now.plus((long)(dtUsed * 1000 * 3600)
                            + (long)(timeRemainingInHours * 1000 * 3600));
                    buildStatus.endTime = endTime;
                    log(String.format("     End Time: %s (%.2f hrs)", endTime,
                            Seconds.secondsBetween(now, endTime).getSeconds() / 3600.0f));

                    if (buildStatus.progress >= 1.0f) {
                        // if we've finished this turn, just set progress
                        buildStatus.progress = 1.0f;
                    }
                }
            }
        }

        // Finally, update the population. The first thing we need to do is evenly distribute goods
        // between all of the colonies.
        float totalGoodsPerHour = empireStatus.totalPopulation / 10.0f;
        if (empireStatus.totalPopulation > 0.0001f && totalGoodsPerHour < 10.0f) {
            totalGoodsPerHour = 10.0f;
        }
        float totalGoodsRequired = totalGoodsPerHour * dtInHours;
        empireStatus.deltaGoodsPerHour -= totalGoodsPerHour;

        // If we have more than total_goods_required stored, then we're cool. Otherwise, our population
        // suffers...
        float goodsEfficiency = 1.0f;
        if (totalGoodsRequired > empireStatus.totalGoods && totalGoodsRequired > 0) {
            goodsEfficiency = empireStatus.totalGoods / totalGoodsRequired;
        }

        log(String.format("--- Updating Population [goods required=%.2f] [goods available=%.2f] [efficiency=%.2f]",
                          totalGoodsRequired, empireStatus.totalGoods, goodsEfficiency));

        // subtract all the goods we'll need
        empireStatus.totalGoods -= totalGoodsRequired;
        if (empireStatus.totalGoods <= 0.0f) {
            // We've run out of goods! That's bad...
            empireStatus.totalGoods = 0.0f;

            if (empireStatus.goodsZeroTime == null
                    || empireStatus.goodsZeroTime.isAfter(now.plus(dt))) {
                log(String.format("    GOODS HAVE HIT ZERO"));
                empireStatus.goodsZeroTime = now.plus(dt);
            }
        }

        // now loop through the colonies and update the population/goods counter
        for (ColonyStatus colonyStatus : empireStatus.colonies) {
            float populationIncrease;
            if (goodsEfficiency >= 1.0f) {
                populationIncrease = Math.max(colonyStatus.population, 10.0f);
                populationIncrease *= colonyStatus.getPopulationFocus() * 0.5f;
            } else {
                populationIncrease = Math.max(colonyStatus.population, 10.0f);
                populationIncrease *= (1.0f - colonyStatus.getPopulationFocus());
                populationIncrease *= 0.25f * (goodsEfficiency - 1.0f);
            }

            colonyStatus.populationDelta = populationIncrease;
            float populationIncreaseThisTurn = populationIncrease * dtInHours;

            float newPopulation = colonyStatus.population + populationIncreaseThisTurn;
            if (newPopulation < 1.0f) {
                newPopulation = 0.0f;
            } else if (newPopulation > colonyStatus.getMaxPopulation()) {
                newPopulation = colonyStatus.getMaxPopulation();
            }
            if (newPopulation < 100.0f && colonyStatus.isInCooldown()) {
                newPopulation = 100.0f;
            }
            log(String.format("    Colony[%d]: [delta=%.2f] [new=%.2f]",
                              colonyStatus.getPlanetIndex(), populationIncrease, newPopulation));
            colonyStatus.population = newPopulation;
        }

        if (empireStatus.totalGoods > empireStatus.maxGoods) {
            empireStatus.totalGoods = empireStatus.maxGoods;
        }
        if (empireStatus.totalMinerals > empireStatus.maxMinerals) {
            empireStatus.totalMinerals = empireStatus.maxMinerals;
        }
    }

    private void simulateCombat(SimulationStatus status, DateTime now, Duration dt) {
        if (!status.prepareCombat(now)) {
            return;
        }

        log(String.format("-- Combat, [loaded %d rounds] [%d fleets]",
                status.combatStatus.rounds.size(), status.combatStatus.fleets.size()));

        // if they're not supposed to start attacking yet, then don't start
        DateTime attackStartTime = status.combatStatus.getAttackStartTime(now);
        if (attackStartTime.isAfter(now.plus(dt))) {
            return;
        }

        // attacks happen in turns, each turn lasts for one minute
        DateTime attackEndTime = now.plus(dt);
        while (now.isBefore(attackEndTime)) {
            if (now.isBefore(attackStartTime)) {
                now = now.plusMinutes(1);
                if (now.isAfter(attackStartTime)) {
                    now = attackStartTime;
                }
                continue;
            }

            CombatRound.Builder round = status.combatStatus.beginRound(now);
            log(String.format("--- Round #%d [%s]", status.combatStatus.rounds.size(), now));
            if (!simulateCombatRound(now, status, round)) {
                log(String.format("--- Combat finished."));
                break;
            }
            now = now.plusMinutes(1);
        }
    }

    private boolean simulateCombatRound(DateTime now, SimulationStatus status,
            CombatRound.Builder round) {
        CombatStatus combatStatus = status.combatStatus;

        // Build the list of fleet groups for this round.
        combatStatus.fleetGroups = new ArrayList<FleetGroupStatus>();
        for (FleetStatus fleet : combatStatus.fleets) {
            if (fleet.isDestroyed(now)) {
                continue;
            }

            boolean foundGroup = false;
            for (FleetGroupStatus fleetGroup : combatStatus.fleetGroups) {
                if (fleetGroup.isInGroup(fleet)) {
                    fleetGroup.addFleet(fleet);
                    foundGroup = true;
                }
            }
            if (!foundGroup) {
                combatStatus.fleetGroups.add(new FleetGroupStatus(fleet));
            }
        }

        // each fleet targets and fires at once
        TreeMap<Integer, Double> hits = new TreeMap<Integer, Double>();
        for (FleetGroupStatus fleetGroup : combatStatus.fleetGroups) {
            if (fleetGroup.state != Fleet.FLEET_STATE.ATTACKING) {
                continue;
            }

            FleetGroupStatus target = findTarget(combatStatus, fleetGroup);
            if (target == null) {
                // if there's no more available targets, then we're no longer attacking
                log(String.format("    Fleet #%d no suitable target.",
                        combatStatus.fleetGroups.indexOf(fleetGroup)));
                fleetGroup.state = Fleet.FLEET_STATE.IDLE;
                continue;
            } else {
                log(String.format("    Fleet #%d attacking fleet #%d",
                        combatStatus.fleetGroups.indexOf(fleetGroup),
                        combatStatus.fleetGroups.indexOf(target)));
            }

            int fleetIndex = combatStatus.fleetGroups.indexOf(fleetGroup);
            int targetIndex = combatStatus.fleetGroups.indexOf(target);

            ShipDesign fleetDesign = fleetGroup.getDesign();
            float damage = fleetGroup.numShips * fleetDesign.getBaseAttack();
            log(String.format("    Fleet #%d (%s x %.2f) hit by fleet #%d (%s x %.2f) for %.2f damage",
                    targetIndex, target.getDesign().getID(), target.numShips, fleetIndex,
                    fleetGroup.getDesign().getID(), fleetGroup.numShips, damage));

            Double totalDamage = hits.get(targetIndex);
            if (totalDamage == null) {
                hits.put(targetIndex, new Double(damage));
            } else {
                hits.put(targetIndex, new Double(totalDamage + damage));
            }

            round.fleets_attacked.add(new CombatRound.FleetAttackRecord.Builder()
                    .fleet_index(fleetIndex)
                    .target_index(targetIndex)
                    .damage(damage)
                    .build());
        }

        // any fleets that were attacked this round will want to change to attacking for the next
        // round, if they're not attacking already...
        for (int i = 0; i < combatStatus.fleetGroups.size(); i++) {
            FleetGroupStatus fleetGroup = combatStatus.fleetGroups.get(i);
            if (!hits.keySet().contains(i)) {
                continue;
            }
            fleetGroup.onAttacked(status.getStar());
        }

        // next, apply the damage from this round
        for (BaseCombatReport.FleetSummary fleet : round.getFleets()) {
            Double damage = hits.get(fleet.getIndex());
            if (damage == null) {
                continue;
            }

            ShipDesign fleetDesign = (ShipDesign) BaseDesignManager.i.getDesign(DesignKind.SHIP, fleet.getDesignID());
            damage /= fleetDesign.getBaseDefence();
            fleet.removeShips((float) (double) damage);
            log(String.format("    Fleet #%d %.2f ships lost (%.2f ships remaining)", fleet.getIndex(), damage, fleet.getNumShips()));

            BaseCombatReport.FleetDamagedRecord damageRecord = new BaseCombatReport.FleetDamagedRecord(
                    round.getFleets(), fleet.getIndex(), (float) damage.doubleValue());
            round.getFleetDamagedRecords().add(damageRecord);

            // go through the "real" fleets and apply the damage as well
            for (String fleetKey : fleet.getFleetKeys()) {
                BaseFleet realFleet = star.findFleet(fleetKey);
                float newNumShips = (float)(realFleet.getNumShips() - damage);
                if (newNumShips <= 0) {
                    newNumShips = 0;
                }
                realFleet.setNumShips(newNumShips);
                if (realFleet.getNumShips() <= 0.0f) {
                    realFleet.setTimeDestroyed(now);
                }

                if (damage <= 0) {
                    break;
                }
            }
        }

        // if all the fleets are friendly (or running away), we can stop attacking
        boolean enemyExists = false;
        for (int i = 0; i < star.getFleets().size(); i++) {
            BaseFleet fleet1 = star.getFleets().get(i);
            if (isDestroyed(fleet1, now) || fleet1.getState() == BaseFleet.State.MOVING) {
                continue;
            }

            for (int j = i + 1; j < star.getFleets().size(); j++) {
                BaseFleet fleet2 = star.getFleets().get(j);
                if (isDestroyed(fleet2, now)) {
                    continue;
                }

                if (!isFriendly(fleet1, fleet2)) {
                    if (fleet2.getState() == BaseFleet.State.MOVING) {
                        // if it's moving, it doesn't count
                        continue;
                    }
                    enemyExists = true;
                }
            }
        }
        if (!enemyExists) {
            for (BaseFleet fleet : star.getFleets()) {
                // switch back from attacking mode to idle
                if (fleet.getState() == BaseFleet.State.ATTACKING) {
                    fleet.idle(now);
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Searches for an enemy fleet with the lowest priority.
     */
    private FleetGroupStatus findTarget(CombatStatus combatStatus, FleetGroupStatus fleet) {
        int foundPriority = 9999;
        FleetGroupStatus target = null;

        for (FleetGroupStatus otherFleet : combatStatus.fleetGroups) {
            if (Helper.isFriendly(fleet, otherFleet)) {
                continue;
            }
            ShipDesign design = otherFleet.getDesign();
            if (target == null || design.getCombatPriority() < foundPriority) {
                target = otherFleet;
                foundPriority = design.getCombatPriority();
            }
        }

        return target;
    }

    /**
     * This interface is used to help debug the simulation code. Implement it to receive a bunch
     * of debug log messages during the simulation process.
     */
    public interface LogHandler {
        void log(String message);
    }

    private static class BasicLogHandler implements LogHandler {
        private static final Log log = new Log("Simulation");

        @Override
        public void log(String message) {
            log.info(message);
        }
    }
}
