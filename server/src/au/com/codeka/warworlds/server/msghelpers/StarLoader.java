package au.com.codeka.warworlds.server.msghelpers;

import java.sql.SQLException;

import au.com.codeka.common.messages.Building;
import au.com.codeka.common.messages.BuildingPosition;
import au.com.codeka.warworlds.server.data.SqlResult;

public class StarLoader {
    public static Building loadBuilding(SqlResult res) throws SQLException {
        return new Building.Builder()
            .key(Integer.toString(res.getInt("id")))
            .colony_key(Integer.toString(res.getInt("colony_id")))
            .design_name(res.getString("design_id"))
            .level(res.getInt("level"))
            .notes(res.getString("notes"))
            .build();
    }

    public static BuildingPosition loadBuildingPosition(SqlResult res) throws SQLException {
        return new BuildingPosition.Builder()
            .building(loadBuilding(res))
            .sector_x(res.getLong("sector_x"))
            .sector_y(res.getLong("sector_y"))
            .offset_x(res.getInt("offset_x"))
            .offset_y(res.getInt("offset_y"))
            .build();
    }
}
