package au.com.codeka.warworlds.server.ctrl;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import okio.ByteString;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Alliance;
import au.com.codeka.common.messages.AllianceMember;
import au.com.codeka.common.messages.AllianceRequest;
import au.com.codeka.common.messages.AllianceRequestVote;
import au.com.codeka.common.messages.CashAuditRecord;
import au.com.codeka.common.messages.Empire;
import au.com.codeka.common.messages.GenericError;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.msghelpers.AllianceHelper;
import au.com.codeka.warworlds.server.utils.ImageSizer;

public class AllianceController {
    private DataBase db;

    public AllianceController() {
        db = new DataBase();
    }
    public AllianceController(Transaction trans) {
        db = new DataBase(trans);
    }

    public BaseDataBase getDB() {
        return db;
    }

    public List<Alliance> getAlliances() throws RequestException {
        try {
            return db.getAlliances();
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public Alliance getAlliance(int allianceID) throws RequestException {
        try {
            return db.getAlliance(allianceID);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public boolean isSameAlliance(int empireID1, int empireID2) throws RequestException {
        if (empireID1 == empireID2) {
            return true;
        }

        try {
            return db.isSameAlliance(empireID1, empireID2);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public boolean isSameAlliance(Empire empire1, Empire empire2) {
        if (empire1.key.equals(empire2.key)) {
            return true;
        }

        if (empire1.alliance == null || empire2.alliance == null) {
            return false;
        }

        return empire1.alliance.key.equals(empire2.alliance.key);
    }

    public List<AllianceRequest> getRequests(int allianceID, boolean includeWithdrawn,
            Integer cursor) throws RequestException {
        try {
            return db.getRequests(allianceID, includeWithdrawn, cursor);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public void leaveAlliance(int empireID, int allianceID) throws RequestException {
        try {
            db.leaveAlliance(empireID, allianceID);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public int addRequest(AllianceRequest request) throws RequestException {
        try {
            // if there's a PNG image attached, make sure it's not too big
            byte[] pngImage = request.png_image.toByteArray();
            byte[] resizedPngImage = ImageSizer.ensureMaxSize(pngImage, 128, 128);
            if (pngImage != resizedPngImage) {
                request = new AllianceRequest.Builder(request)
                    .png_image(ByteString.of(resizedPngImage))
                    .build();
            }

            // Save the request and update the id.
            int requestID = db.addRequest(request);
            request = new AllianceRequest.Builder(request).id(requestID).build();

            // there's an implicit vote when you create a request (some requests require zero
            // votes, which means it passes straight away)
            Alliance alliance = db.getAlliance(request.alliance_id);
            AllianceRequestProcessor processor = AllianceRequestProcessor.get(alliance, request);
            processor.onVote(this);

            return requestID;
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public int getNumVotes(AllianceMember.Rank rank) {
        switch(rank) {
        case CAPTAIN:
            return 10;
        case LIEUTENANT:
            return 5;
        case MEMBER:
        default:
            return 1;
        }
    }

    public void vote(AllianceRequestVote vote) throws RequestException {
        try {
            Alliance alliance = db.getAlliance(vote.alliance_id);
            // normalize the number of votes they get by their rank in the alliance
            for (AllianceMember member : alliance.members) {
                if (Integer.parseInt(member.empire_key) == vote.empire_id) {
                    int numVotes = getNumVotes(member.rank);
                    if (vote.votes < 0) {
                        numVotes *= -1;
                    }
                    if (vote.votes != numVotes) {
                        vote = new AllianceRequestVote.Builder(vote).votes(numVotes).build();
                    }
                    break;
                }
            }

            db.vote(vote);

            // depending on the kind of request this is, check whether this is enough votes to
            // complete the voting or not
            AllianceRequest request = db.getRequest(vote.alliance_request_id);
            AllianceRequestProcessor processor = AllianceRequestProcessor.get(alliance, request);
            processor.onVote(this);
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public int createAlliance(Alliance alliance, Empire ownerEmpire) throws RequestException {
        CashAuditRecord.Builder audit_record_pb = new CashAuditRecord.Builder()
                .empire_id(Integer.parseInt(ownerEmpire.key))
                .alliance_name(alliance.name);
        if (!new EmpireController().withdrawCash(Integer.parseInt(ownerEmpire.key),
                250000, audit_record_pb)) {
            throw new RequestException(400, GenericError.ErrorCode.InsufficientCash,
                    "Insufficient cash to create a new alliance.");
        }

        try {
            return db.createAlliance(alliance, Integer.parseInt(ownerEmpire.key));
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public byte[] getAllianceShield(int allianceID, Integer shieldID) throws RequestException {
        String sql = "SELECT image FROM alliance_shields " +
                    " WHERE alliance_id = ? ";
        if (shieldID != null) {
            sql += " AND id = ?";
        }
        sql += " ORDER BY create_date DESC LIMIT 1";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setInt(1, allianceID);
            if (shieldID != null) {
                stmt.setInt(2, shieldID);
            }
            SqlResult res = stmt.select();
            if (res.next()) {
                return res.getBytes(1);
            }
        } catch (Exception e) {
            throw new RequestException(e);
        }

        return null;
    }

    public void changeAllianceShield(int allianceID, byte[] pngImage) throws RequestException {
        String sql = "INSERT INTO alliance_shields (alliance_id, create_date, image) VALUES (?, NOW(), ?)";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setInt(1, allianceID);
            stmt.setBytes(2, pngImage);
            stmt.update();
        } catch (Exception e) {
            throw new RequestException(e);
        }

        sql = "UPDATE alliances SET image_updated_date = NOW() WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setInt(1, allianceID);
            stmt.update();
        } catch (Exception e) {
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

        public boolean isSameAlliance(int empireID1, int empireID2) throws Exception {
            String sql = "SELECT alliance_id FROM empires WHERE id IN (?, ?)";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, empireID1);
                stmt.setInt(2, empireID2);
                SqlResult res = stmt.select();

                Integer allianceID1 = -1;
                Integer allianceID2 = -1;
                while (res.next()) {
                    if (allianceID1 != null && allianceID1 < 0) {
                        allianceID1 = res.getInt(1);
                    } else {
                        allianceID2 = res.getInt(1);
                    }
                }

                if (allianceID1 == null || allianceID2 == null) {
                    return false;
                } else {
                    return allianceID1 == allianceID2;
                }
            }
        }

        public List<Alliance> getAlliances() throws Exception {
            String sql = "SELECT alliances.*," +
                               " (SELECT COUNT(*) FROM empires WHERE empires.alliance_id = alliances.id) AS num_empires," +
                               " (SELECT COUNT(*) FROM alliance_requests WHERE alliance_id = alliances.id AND state = " + AllianceRequest.RequestState.PENDING.getValue() + ") AS num_pending_requests" +
                        " FROM alliances" +
                        " ORDER BY name ASC";
            try (SqlStmt stmt = prepare(sql)) {
                SqlResult res = stmt.select();

                ArrayList<Alliance> alliances = new ArrayList<Alliance>();
                while (res.next()) {
                    alliances.add(AllianceHelper.loadAlliance(null, res));
                }
                return alliances;
            }
        }

        public Alliance getAlliance(int allianceID) throws Exception {
            Alliance alliance = null;
            String sql = "SELECT *, 0 AS num_empires," +
                               " (SELECT COUNT(*) FROM alliance_requests WHERE alliance_id = alliances.id AND state = " + AllianceRequest.RequestState.PENDING.getValue() + ") AS num_pending_requests" +
                        " FROM alliances WHERE id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, allianceID);
                SqlResult res = stmt.select();

                if (res.next()) {

                    sql = "SELECT id, alliance_id, alliance_rank" +
                          " FROM empires WHERE alliance_id = ?";
                    try (SqlStmt memberStmt = prepare(sql)) {
                        stmt.setInt(1, allianceID);
                        SqlResult memberRes = memberStmt.select();
                        while (res.next()) {
                            alliance = AllianceHelper.loadAlliance(null, res, memberRes);
                        }
                    }
                }
            }
            if (alliance == null) {
                throw new RequestException(404);
            }

            return alliance;
        }

        public int createAlliance(Alliance alliance, int creatorEmpireID) throws Exception {
            int allianceID;

            String sql = "INSERT INTO alliances (name, creator_empire_id, created_date," +
                              " bank_balance, image_updated_date) VALUES (?, ?, ?, 0, NOW())";
            try (SqlStmt stmt = prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, alliance.name);
                stmt.setInt(2, creatorEmpireID);
                stmt.setDateTime(3, DateTime.now());
                stmt.update();
                allianceID = stmt.getAutoGeneratedID();
            }

            sql = "UPDATE empires SET alliance_id = ? WHERE id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, allianceID);
                stmt.setInt(2, creatorEmpireID);
                stmt.update();
            }

            return allianceID;
        }

        public int addRequest(AllianceRequest request) throws Exception {
            // if you make another request while you've still got one pending, the new request
            // will overwrite the old one.
            String sql = "DELETE FROM alliance_requests" +
                        " WHERE request_empire_id = ?" +
                          " AND alliance_id = ?" +
                          " AND request_type = ?" +
                          " AND state = " + AllianceRequest.RequestState.PENDING.getValue();
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, request.request_empire_id);
                stmt.setInt(2, request.alliance_id);
                stmt.setInt(3, request.request_type.getValue());
                stmt.update();
            }

            sql = "INSERT INTO alliance_requests (" +
                    "alliance_id, request_empire_id, request_date, request_type, message, state," +
                   " votes, target_empire_id, amount, png_image, new_name)" +
                 " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (SqlStmt stmt = prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, request.alliance_id);
                stmt.setInt(2, request.request_empire_id);
                stmt.setDateTime(3, DateTime.now());
                stmt.setInt(4, request.request_type.getValue());
                stmt.setString(5, request.message);
                stmt.setInt(6, AllianceRequest.RequestState.PENDING.getValue());
                stmt.setInt(7, 0);
                stmt.setInt(8, request.target_empire_id);
                stmt.setDouble(9, request.amount);
                stmt.setBytes(10, request.png_image.toByteArray());
                stmt.setString(11, request.new_name);
                stmt.update();

                return stmt.getAutoGeneratedID();
            }
        }

        public void leaveAlliance(int empireID, int allianceID) throws Exception {
            String sql = "UPDATE empires SET alliance_id = NULL WHERE id = ? AND alliance_id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, empireID);
                stmt.setInt(2, allianceID);
                stmt.update();
            }
        }

        public List<AllianceRequest> getRequests(int allianceID, boolean includeWithdrawn,
                Integer cursor) throws Exception {
            ArrayList<AllianceRequest> requests = new ArrayList<AllianceRequest>();
            HashSet<Integer> requestIDs = new HashSet<Integer>();

            String sql = "SELECT * FROM alliance_requests" +
                        " WHERE alliance_id = ?";
            if (!includeWithdrawn) {
                sql += " AND state != " + AllianceRequest.RequestState.WITHDRAWN.getValue();
            }
            if (cursor != null) {
                sql += " AND id < ?";
            }
            sql += " ORDER BY request_date DESC LIMIT 50";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, allianceID); 
                if (cursor != null) {
                    stmt.setInt(2, cursor);
                }
                SqlResult res = stmt.select();

                while (res.next()) {
                    AllianceRequest request = AllianceHelper.loadAllianceRequest(res);
                    requests.add(request);

                    if (!requestIDs.contains(request.id)) {
                        requestIDs.add(request.id);
                    }
                }
            }

            if (!requestIDs.isEmpty()) {
                sql = "SELECT * FROM alliance_request_votes WHERE alliance_request_id IN ";
                sql += buildInClause(requestIDs);
                try (SqlStmt stmt = prepare(sql)) {
                    SqlResult res = stmt.select();
                    while (res.next()) {
                        AllianceRequestVote vote = AllianceHelper.loadAllianceRequestVote(res);
                        for (AllianceRequest request : requests) {
                            if (request.id == vote.alliance_request_id) {
                                request.vote.add(vote);
                            }
                        }
                    }
                }
            }

            return requests;
        }

        public AllianceRequest getRequest(int allianceRequestID) throws Exception {
            String sql = "SELECT * FROM alliance_requests WHERE id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, allianceRequestID); 
                SqlResult res = stmt.select();

                if (res.next()) {
                    return AllianceHelper.loadAllianceRequest(res);
                }
            }

            throw new RequestException(404, "No such alliance request found!");
        }

        public void vote(AllianceRequestVote vote) throws Exception {
            // if they're already voted for this request, then update the existing vote
            String sql = "SELECT id FROM alliance_request_votes " +
                         "WHERE alliance_request_id = ? AND empire_id = ?";
            Long id = null;
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, vote.alliance_request_id);
                stmt.setInt(2, vote.empire_id);
                id = stmt.selectFirstValue(Long.class);
            }

            if (id != null) {
                sql = "UPDATE alliance_request_votes SET votes = ? WHERE id = ?";
                try (SqlStmt stmt = prepare(sql)) {
                    stmt.setInt(1, vote.votes);
                    stmt.setInt(2, (int) (long) id);
                    stmt.update();
                }
            } else {
                sql = "INSERT INTO alliance_request_votes (alliance_id, alliance_request_id," +
                         " empire_id, votes, date) VALUES (?, ?, ?, ?, NOW())";
                try (SqlStmt stmt = prepare(sql)) {
                    stmt.setInt(1, vote.alliance_id);
                    stmt.setInt(2, vote.alliance_request_id);
                    stmt.setInt(3, vote.empire_id);
                    stmt.setInt(4, vote.votes);
                    stmt.update();
                }
            }

            // update the alliance_requests table so it has an accurate vote count for this request
            sql = "UPDATE alliance_requests SET votes = (" +
                        "SELECT SUM(votes) FROM alliance_request_votes WHERE alliance_request_id = alliance_requests.id" +
                    ") WHERE id = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, vote.alliance_request_id);
                stmt.update();
            }
        }
    }
}
