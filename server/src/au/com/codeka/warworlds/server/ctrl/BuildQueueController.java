package au.com.codeka.warworlds.server.ctrl;

import java.sql.Statement;
import java.util.ArrayList;

import org.joda.time.DateTime;

import au.com.codeka.common.Log;
import au.com.codeka.common.messages.BuildRequest;
import au.com.codeka.common.messages.Building;
import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.Fleet;
import au.com.codeka.common.messages.GenericError;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.model.BuildingDesign;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.Design.Dependency;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.model.ShipDesign;
import au.com.codeka.common.msghelpers.BuildHelper;
import au.com.codeka.common.msghelpers.FleetHelper;
import au.com.codeka.common.msghelpers.StarHelper;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.DesignManager;

public class BuildQueueController {
    private static final Log log = new Log("BuildQueueController");
    private DataBase db;

    public BuildQueueController() {
        db = new DataBase();
    }
    public BuildQueueController(Transaction trans) {
        db = new DataBase(trans);
    }

    public void build(BuildRequest buildRequest) throws RequestException {
        Star star = new StarController(db.getTransaction()).getStar(
                Integer.parseInt(buildRequest.star_key));
        Colony colony = StarHelper.getColony(star, buildRequest.colony_key);

        Design design = DesignManager.i.getDesign(DesignKind.fromBuildKind(buildRequest.build_kind),
                buildRequest.design_id);

        if (buildRequest.count <= 0) {
            throw new RequestException(400, "Cannot build negative count.");
        }
        if (buildRequest.build_kind == BuildRequest.BUILD_KIND.SHIP
                && buildRequest.count > design.getBuildCost().getMaxCount()
                && buildRequest.existing_fleet_id == null) {
            buildRequest = new BuildRequest.Builder(buildRequest).count(
                    design.getBuildCost().getMaxCount()).build();
        }

        // check dependencies
        for (Design.Dependency dependency : design.getDependencies()) {
            if (!dependency.isMet(star, colony)) {
                throw new RequestException(400,
                        GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot build %s as level %d %s is required.",
                                      BuildHelper.getDesign(buildRequest).getDisplayName(),
                                      dependency.getLevel(),
                                      design.getDisplayName()));
            }
        }

        // check build limits
        if (design.getDesignKind() == DesignKind.BUILDING
                && buildRequest.existing_building_key == null) {
            BuildingDesign buildingDesign = (BuildingDesign) design;

            if (buildingDesign.getMaxPerColony() > 0) {
                int maxPerColony = buildingDesign.getMaxPerColony();
                int numThisColony = 0;
                for (Building building : StarHelper.getBuildings(star, colony)) {
                    if (building.design_id.equals(buildRequest.design_id)) {
                        numThisColony ++;
                    }
                }
                for (BuildRequest baseBuildRequest : star.build_requests) {
                    BuildRequest otherBuildRequest = (BuildRequest) baseBuildRequest;
                    if (otherBuildRequest.colony_key.equals(colony.key) &&
                        otherBuildRequest.design_id.equals(buildRequest.design_id)) {
                        numThisColony ++;
                    }
                }

                if (numThisColony >= maxPerColony) {
                    throw new RequestException(400,
                            GenericError.ErrorCode.CannotBuildMaxPerColonyReached,
                            String.format("Cannot build %s, maximum per colony reached.",
                                          BuildHelper.getDesign(buildRequest).getDisplayName()));
                }
            }

            if (buildingDesign.getMaxPerEmpire() > 0) {
                String sql ="SELECT (" +
                              " SELECT COUNT(*)" +
                              " FROM buildings" +
                              " WHERE empire_id = ?" +
                                " AND design_id = ?" +
                            " ) + (" +
                              " SELECT COUNT(*)" +
                              " FROM build_requests" +
                              " WHERE empire_id = ?" +
                                " AND design_id = ?" +
                            " )";
                try(SqlStmt stmt = db.prepare(sql)) {
                    stmt.setInt(1, Integer.parseInt(buildRequest.empire_key));
                    stmt.setString(2, buildRequest.design_id);
                    stmt.setInt(3, Integer.parseInt(buildRequest.empire_key));
                    stmt.setString(4, buildRequest.design_id);
                    Long numPerEmpire = stmt.selectFirstValue(Long.class);
                    if (numPerEmpire >= buildingDesign.getMaxPerEmpire()) {
                        throw new RequestException(400,
                                GenericError.ErrorCode.CannotBuildMaxPerColonyReached,
                                String.format("Cannot build %s, maximum per empire reached.",
                                              BuildHelper.getDesign(buildRequest).getDisplayName()));
                    }
                } catch (Exception e) {
                    throw new RequestException(e);
                }
            }
        }

        if (buildRequest.build_kind == BuildRequest.BUILD_KIND.BUILDING
                && buildRequest.existing_building_key != null) {
            BuildingDesign buildingDesign = (BuildingDesign) design;

            // if we're upgrading a building, make sure we don't upgrade it twice!
            for (BuildRequest baseBuildRequest : star.build_requests) {
                BuildRequest otherBuildRequest = (BuildRequest) baseBuildRequest;
                if (otherBuildRequest.existing_building_key == null) {
                    continue;
                }
                if (otherBuildRequest.existing_building_key.equals(buildRequest.existing_building_key)) {
                    throw new RequestException(400,
                            GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s, upgrade is already in progress.",
                                          BuildHelper.getDesign(buildRequest).getDisplayName()));
                }
            }

            Building existingBuilding = null;
            for (Building building : StarHelper.getBuildings(star, colony)) {
                if (building.key.equals(buildRequest.existing_building_key)) {
                    existingBuilding = building;
                }
            }
            if (existingBuilding == null) {
                throw new RequestException(400,
                        GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot upgrade %s, original building no longer exists.",
                                      BuildHelper.getDesign(buildRequest).getDisplayName()));
            }

            // make sure the existing building isn't already at the maximum level
            if (existingBuilding.level == buildingDesign.getUpgrades().size() + 1) {
               throw new RequestException(400,
                        GenericError.ErrorCode.CannotBuildMaxLevelReached,
                        String.format("Cannot update %s, already at maximum level.",
                                BuildHelper.getDesign(buildRequest).getDisplayName()));
            }

            // check dependencies for this specific level
            ArrayList<Dependency> dependencies = buildingDesign.getDependencies(
                    existingBuilding.level + 1);
            for (Design.Dependency dependency : dependencies) {
                if (!dependency.isMet(star, colony)) {
                    throw new RequestException(400,
                            GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s as level %d %s is required.",
                                          BuildHelper.getDesign(buildRequest).getDisplayName(),
                                          dependency.getLevel(),
                                          design.getDisplayName()));
                }
            }
        }

        if (buildRequest.build_kind == BuildRequest.BUILD_KIND.SHIP
                && buildRequest.existing_fleet_id != null) {
            ShipDesign shipDesign = (ShipDesign) design;

            // if we're upgrading a ship, make sure we don't upgrade it twice!
            for (BuildRequest existingBuildRequest : star.build_requests) {
                if (existingBuildRequest.existing_fleet_id == null) {
                    continue;
                }
                if (existingBuildRequest.existing_fleet_id == buildRequest.existing_fleet_id) {
                    throw new RequestException(400,
                            GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s, upgrade is already in progress.",
                                    shipDesign.getDisplayName()));
                }
            }

            Fleet existingFleet = null;
            for (Fleet fleet : star.fleets) {
                if (Integer.parseInt(fleet.key) == buildRequest.existing_fleet_id) {
                    existingFleet = fleet;
                }
            }
            if (existingFleet == null) {
                throw new RequestException(400,
                        GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot upgrade %s, original fleet no longer exists.",
                                shipDesign.getDisplayName()));
            }

            // make sure the existing fleet doesn't already have the upgrade
            if (FleetHelper.getFleetUpgrade(existingFleet, buildRequest.upgrade_id) != null) {
                throw new RequestException(400,
                        GenericError.ErrorCode.CannotBuildMaxLevelReached,
                        String.format("Cannot update %s, already has upgrade.",
                                shipDesign.getDisplayName()));
            }
        }

        // OK, we're good to go, let's go!
        String sql = "INSERT INTO build_requests (star_id, planet_index, colony_id, empire_id," +
                       " existing_building_id, design_kind, design_id, notes," +
                       " existing_fleet_id, upgrade_id, count, progress, processing, start_time," +
                       " end_time)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)";
        try (SqlStmt stmt = db.prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, Integer.parseInt(buildRequest.star_key));
            stmt.setInt(2, buildRequest.planet_index);
            stmt.setInt(3, Integer.parseInt(buildRequest.colony_key));
            stmt.setInt(4, Integer.parseInt(buildRequest.empire_key));
            if (buildRequest.existing_building_key != null) {
                stmt.setInt(5, Integer.parseInt(buildRequest.existing_building_key));
            } else {
                stmt.setNull(5);
            }
            stmt.setInt(6, buildRequest.build_kind.getValue());
            stmt.setString(7, buildRequest.design_id);
            stmt.setString(8, buildRequest.notes);
            stmt.setInt(9, buildRequest.existing_fleet_id);
            stmt.setString(10, buildRequest.upgrade_id);
            stmt.setInt(11, buildRequest.count);
            stmt.setDouble(12, buildRequest.progress);
            stmt.setDateTime(13, new DateTime(buildRequest.start_time * 1000));
            stmt.setDateTime(14, new DateTime(buildRequest.end_time * 1000));
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void updateNotes(int buildRequestID, String notes) throws RequestException {
        String sql = "UPDATE build_requests SET notes = ? WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setString(1, notes);
            stmt.setInt(2, buildRequestID);
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void stop(Star star, BuildRequest buildRequest) throws RequestException {
        star.getBuildRequests().remove(buildRequest);

        String sql = "DELETE FROM build_requests WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setInt(1, buildRequest.getID());
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    /**
     * Accelerate the given build. Returns {@code true} if the build is now complete.
     */
    public boolean accelerate(Star star, BuildRequest buildRequest, float accelerateAmount) throws RequestException {
        if (accelerateAmount > 0.99f) {
            accelerateAmount = 1.0f;
        }
        float remainingProgress = 1.0f - buildRequest.getProgress(false);
        float progressToComplete = remainingProgress * accelerateAmount;

        Design design = buildRequest.getDesign();
        float mineralsToUse = design.getBuildCost().getCostInMinerals() * progressToComplete;
        float cost = mineralsToUse * buildRequest.getCount();

        Messages.CashAuditRecord.Builder audit_record_pb = Messages.CashAuditRecord.newBuilder();
        audit_record_pb.setEmpireId(buildRequest.getEmpireID());
        audit_record_pb.setBuildDesignId(buildRequest.getDesignID());
        audit_record_pb.setBuildCount(buildRequest.getCount());
        audit_record_pb.setAccelerateAmount(accelerateAmount);
        if (!new EmpireController().withdrawCash(buildRequest.getEmpireID(), cost, audit_record_pb)) {
            throw new RequestException(400, Messages.GenericError.ErrorCode.InsufficientCash,
                    "You don't have enough cash to accelerate this build.");
        }
        float finalProgress = buildRequest.getProgress(false) + progressToComplete;
        buildRequest.setProgress(finalProgress);
        if (finalProgress > 0.999) {
            buildRequest.setEndTime(DateTime.now());

            // if you accelerate to completion, don't spam a notification
            buildRequest.disableNotification();
            return true;
        }

        return false;
    }

    public void saveBuildRequest(BuildRequest buildRequest) throws RequestException {
        String sql = "UPDATE build_requests SET progress = ?, end_time = ?, disable_notification = ? WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setDouble(1, buildRequest.getProgress(false));
            stmt.setDateTime(2, buildRequest.getEndTime());
            stmt.setInt(3, buildRequest.getDisableNotification() ? 1 : 0);
            stmt.setInt(4, buildRequest.getID());
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    private static class DataBase extends BaseDataBase {
        public DataBase() {
            super();
        }
        public DataBase(Transaction trans) {
            super(trans);
        }
    }
}
