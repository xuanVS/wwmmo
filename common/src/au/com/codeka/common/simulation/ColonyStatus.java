package au.com.codeka.common.simulation;

import java.util.ArrayList;
import java.util.List;

import au.com.codeka.common.messages.BuildRequest;
import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.Planet;
import au.com.codeka.common.messages.Star;

/** Represents the state of a single {@link Colony} during a {@link Simulator}. */
public class ColonyStatus {
    private final Star star;
    private final Colony.Builder colony;
    private final Planet planet;

    public float deltaGoods;
    public float deltaMinerals;
    public float uncollectedTaxes;
    public float population;
    public float populationDelta;
    public List<BuildStatus> builds;

    public ColonyStatus(Star star, Colony colony) {
        this.star = star;
        this.colony = new Colony.Builder(colony);
        this.planet = star.planets.get(colony.planet_index - 1);

        deltaGoods = colony.delta_goods;
        deltaMinerals = colony.delta_minerals;
        uncollectedTaxes = colony.uncollected_taxes;
        population = colony.population;

        builds = new ArrayList<BuildStatus>();
        for (BuildRequest buildRequest : star.build_requests) {
            if (buildRequest.colony_key.equals(colony.key)) {
                builds.add(new BuildStatus(star, buildRequest));
            }
        }
    }

    public int getPlanetIndex() {
        return colony.planet_index;
    }

    public Planet getPlanet() {
        return planet;
    }

    public float getMaxPopulation() {
        return colony.max_population;
    }

    public boolean isInCooldown() {
        return colony.cooldown_end_time != null;
    }

    public float getFarmingFocus() {
        return colony.focus_farming;
    }

    public float getMiningFocus() {
        return colony.focus_mining;
    }

    public float getConstructionFocus() {
        return colony.focus_construction;
    }

    public float getPopulationFocus() {
        return colony.focus_population;
    }
}
