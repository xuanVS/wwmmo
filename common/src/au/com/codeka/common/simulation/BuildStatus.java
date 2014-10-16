package au.com.codeka.common.simulation;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.BuildRequest;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.msghelpers.BuildHelper;

/** Represents the status of a single build request. */
public class BuildStatus {
    private final Star star;
    private final BuildRequest.Builder buildRequest;

    public DateTime startTime;
    public DateTime endTime;
    public float progress;

    public BuildStatus(Star star, BuildRequest buildRequest) {
        this.star = star;
        this.buildRequest = new BuildRequest.Builder(buildRequest);

        startTime = new DateTime(buildRequest.start_time * 1000);
        endTime = new DateTime(buildRequest.end_time * 1000);
        progress = buildRequest.progress;
    }

    public Design getDesign() {
        return BuildHelper.getDesign(buildRequest);
    }

    public int getCount() {
        return buildRequest.count;
    }

    public Integer getExistingFleetID() {
        return buildRequest.existing_fleet_id;
    }

    public String getUpgradeID() {
        return buildRequest.upgrade_id;
    }
}
