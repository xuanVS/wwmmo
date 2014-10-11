package au.com.codeka.warworlds.server.msghelpers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Alliance;
import au.com.codeka.common.messages.AllianceMember;
import au.com.codeka.common.messages.AllianceRequest;
import au.com.codeka.common.messages.AllianceRequestVote;
import au.com.codeka.warworlds.server.data.SqlResult;

public class AllianceHelper {
    public static Alliance loadAlliance(Integer id, SqlResult res) throws SQLException {
        return loadAlliance(id, res, null);
    }

    public static Alliance loadAlliance(Integer id, SqlResult res, SqlResult memberRes)
            throws SQLException {
        return new Alliance.Builder()
                .key(Integer.toString(id == null ? res.getInt("id") : id))
                .name(res.hasColumn("alliance_name")
                        ? res.getString("alliance_name") : res.getString("name"))
                .creator_empire_key(Integer.toString(res.getInt("creator_empire_id")))
                .time_created(res.getDateTime("created_date").getMillis() / 1000)
                .num_members(res.getInt("num_empires"))
                .members(loadAllianceMembers(memberRes))
                .bank_balance(res.getDouble("bank_balance"))
                .date_image_updated(res.getDateTime("image_updated_date").getMillis() / 1000)
                .num_pending_requests(res.getInt("num_pending_requests"))
                .build();
    }

    public static AllianceRequest loadAllianceRequest(SqlResult res) throws SQLException {
        return new AllianceRequest.Builder()
                .id(res.getInt("id"))
                .alliance_id(res.getInt("alliance_id"))
                .request_empire_id(res.getInt("request_empire_id"))
                .request_date(res.getDateTime("request_date").getMillis() / 1000)
                .request_type(AllianceRequest.RequestType.values()[res.getInt("request_type")])
                .message(res.getString("message"))
                .state(AllianceRequest.RequestState.values()[res.getInt("state")])
                .num_votes(res.getInt("votes"))
                .target_empire_id(res.getInt("target_empire_id"))
                .amount(res.getFloat("amount"))
                .png_image(res.getByteString("png_image"))
                .new_name(res.getString("new_name"))
                .build();
    }

    public static AllianceRequestVote loadAllianceRequestVote(SqlResult res) throws SQLException {
        return new AllianceRequestVote.Builder()
                .id(res.getInt("id"))
                .alliance_id(res.getInt("alliance_id"))
                .alliance_request_id(res.getInt("alliance_request_id"))
                .empire_id(res.getInt("empire_id"))
                .votes(res.getInt("votes"))
                .date(res.getDateTime("date").getMillis() / 1000)
                .build();
    }

    private static List<AllianceMember> loadAllianceMembers(SqlResult res) throws SQLException {
        ArrayList<AllianceMember> members = new ArrayList<AllianceMember>();
        if (res == null) {
            return members;
        }

        while (res.next()) {
            members.add(new AllianceMember.Builder()
                    .empire_key(Integer.toString(res.getInt("id")))
                    .alliance_key(Integer.toString(res.getInt("alliance_id")))
                    .time_joined(DateTime.now().getMillis() / 1000) // TODO
                    .rank(res.getInt("alliance_rank") == null
                        ? AllianceMember.Rank.CAPTAIN
                        : AllianceMember.Rank.values()[res.getInt("alliance_rank")])
                    .build());
        }
        return members;
    }
}
