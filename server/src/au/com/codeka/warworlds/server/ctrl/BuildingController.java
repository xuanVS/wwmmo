package au.com.codeka.warworlds.server.ctrl;

import java.util.ArrayList;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Building;
import au.com.codeka.common.messages.BuildingPosition;
import au.com.codeka.common.messages.Colony;
import au.com.codeka.common.messages.Star;
import au.com.codeka.common.model.BuildingDesign;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.DesignManager;
import au.com.codeka.warworlds.server.msghelpers.StarLoader;

public class BuildingController {
    private DataBase db;

    public BuildingController() {
        db = new DataBase();
    }
    public BuildingController(Transaction trans) {
        db = new DataBase(trans);
    }

    public void createBuilding(Star star, Colony colony, String designID, String notes)
            throws RequestException {
        try {
            Building building = new Building.Builder()
                .colony_key(colony.key)
                .design_name(designID)
                .level(1)
                .notes(notes)
                .build();
            db.createBuilding(colony, building);

            // TODO: hard-coded?
            if (designID.equals("hq")) {
                new EmpireController().setHomeStar(Integer.parseInt(colony.empire_key),
                        Integer.parseInt(star.key));
            }
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void upgradeBuilding(Star star, Colony colony, int buildingID) throws RequestException {
        Building existingBuilding = null;
        for (Building building : star.buildings) {
            if (Integer.parseInt(building.key) == buildingID
                    && building.colony_key.equals(colony.key)) {
                existingBuilding = building;
                break;
            }
        }
        if (existingBuilding == null) {
            throw new RequestException(404);
        }

        BuildingDesign design = (BuildingDesign) DesignManager.i.getDesign(DesignKind.BUILDING,
                existingBuilding.design_name);
        if (existingBuilding.level > design.getUpgrades().size()) {
            // it's already at the max level, can't upgrade
            return;
        }

        try {
            db.upgradeBuilding(existingBuilding);
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public ArrayList<BuildingPosition> getBuildings(int empireID, long minSectorX, long minSectorY,
            long maxSectorX, long maxSectorY) throws RequestException {
        try {
            return db.getBuildings(empireID, minSectorX, minSectorY, maxSectorX, maxSectorY);
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

        public void createBuilding(Colony colony, Building building) throws Exception {
            String sql = "INSERT INTO buildings (star_id, colony_id, empire_id," +
                                               " design_id, build_time, level, notes)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, Integer.parseInt(colony.star_key));
                stmt.setInt(2, Integer.parseInt(colony.key));
                stmt.setInt(3, Integer.parseInt(colony.empire_key));
                stmt.setString(4, building.design_name);
                stmt.setDateTime(5, DateTime.now());
                stmt.setInt(6, 1);
                stmt.setString(7, building.notes);
                stmt.update();
            }
        }

        public void upgradeBuilding(Building building) throws Exception {
            String sql = "UPDATE buildings SET level = level+1 WHERE id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, Integer.parseInt(building.key));
                stmt.update();
            }
        }

        public ArrayList<BuildingPosition> getBuildings(int empireID, long minSectorX, long minSectorY,
                                                long maxSectorX, long maxSectorY) throws Exception {
            String sql = "SELECT buildings.*, sectors.x AS sector_x, sectors.y AS sector_y," +
                               " stars.x AS offset_x, stars.y AS offset_y " +
                        " FROM buildings" +
                        " INNER JOIN  stars ON buildings.star_id = stars.id" +
                        " INNER JOIN sectors ON stars.sector_id = sectors.id" +
                        " WHERE buildings.empire_id = ?" +
                          " AND sectors.x >= ? AND sectors.x <= ?" +
                          " AND sectors.y >= ? AND sectors.y <= ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, empireID);
                stmt.setLong(2, minSectorX);
                stmt.setLong(3, maxSectorX);
                stmt.setLong(4, minSectorY);
                stmt.setLong(5, maxSectorY);
                SqlResult res = stmt.select();

                ArrayList<BuildingPosition> buildings = new ArrayList<BuildingPosition>();
                while (res.next()) {
                    buildings.add(StarLoader.loadBuildingPosition(res));
                }
                return buildings;
            }
        }
    }
}
