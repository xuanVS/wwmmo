package au.com.codeka.warworlds.server.ctrl;

import java.util.Arrays;
import java.util.TreeSet;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.Alliance;
import au.com.codeka.common.messages.AllianceMember;
import au.com.codeka.common.messages.AllianceRequest;
import au.com.codeka.common.messages.CashAuditRecord;
import au.com.codeka.common.messages.GenericError;
import au.com.codeka.common.msghelpers.AllianceHelper;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.utils.ImageSizer;

/**
 * This is the base class for the alliance request processors (which are actually inner classes
 * of this class as well). For each {@link RequestType}, we may have different vote requirements,
 * difference effects and so on.
 */
public abstract class AllianceRequestProcessor {
    /**
     * Gets an \c AllianceRequestProcessor for the given \see AllianceRequest.
     */
    public static AllianceRequestProcessor get(Alliance alliance, AllianceRequest request) {
        switch (request.request_type) {
        case JOIN:
            return new JoinRequestProcessor(alliance, request);
        case LEAVE:
            return new LeaveRequestProcessor(alliance, request);
        case KICK:
            return new KickRequestProcessor(alliance, request);
        case DEPOSIT_CASH:
            return new DepositCashRequestProcessor(alliance, request);
        case WITHDRAW_CASH:
            return new WithdrawCashRequestProcessor(alliance, request);
        case CHANGE_IMAGE:
            return new ChangeImageRequestProcessor(alliance, request);
        case CHANGE_NAME:
            return new ChangeNameRequestProcessor(alliance, request);
        }

        throw new UnsupportedOperationException("Unknown request type: " + request.request_type);
    }

    protected Alliance mAlliance;
    protected AllianceRequest mRequest;

    protected AllianceRequestProcessor(Alliance alliance, AllianceRequest request) {
        mRequest = request;
        mAlliance = alliance;
    }

    public static int getRequiredVotes(AllianceRequest.RequestType requestType) {
        switch(requestType) {
        case JOIN:
            return 5;
        case LEAVE:
            return 0;
        case KICK:
            return 10;
        case DEPOSIT_CASH:
            return 0;
        case WITHDRAW_CASH:
            return 10;
        case CHANGE_IMAGE:
            return 10;
        case CHANGE_NAME:
            return 10;
        default:
            throw new IllegalArgumentException();
        }
    }

    /**
     * Called when we receive a vote for this request. If we've received enough votes, then the
     * request is passed. If we've received enough negative votes, the motion is denied.
     */
    public void onVote(AllianceController ctrl) throws Exception {
        if (mRequest.state != AllianceRequest.RequestState.PENDING) {
            throw new RequestException(400,
                    GenericError.ErrorCode.CannotVoteOnNonPendingRequest,
                    "Cannot vote on a request that is not PENDING.");
        }

        int requiredVotes = getRequiredVotes(mRequest.request_type);
        int totalPossibleVotes = getTotalPossibleVotes();
        if (requiredVotes > totalPossibleVotes) {
            requiredVotes = totalPossibleVotes;
        }
        if (requiredVotes < 0) {
            requiredVotes = 0;
        }

        if (mRequest.num_votes >= requiredVotes) {
            // if we have enough votes for a 'success', then this vote passes.
            onVotePassed(ctrl);
        } else if (mRequest.num_votes <= -requiredVotes) {
            // if we have enough negative votes for a 'failure' then this vote fails.
            onVoteFailed(ctrl);
        }
    }

    protected void onVotePassed(AllianceController ctrl) throws Exception {
        mRequest = new AllianceRequest.Builder(mRequest)
                .state(AllianceRequest.RequestState.ACCEPTED).build();

        String sql = "UPDATE alliance_requests SET state = ? WHERE id = ?";
        try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
            stmt.setInt(1, mRequest.state.getValue());
            stmt.setInt(2, mRequest.id);
            stmt.update();
        }
    }

    protected void onVoteFailed(AllianceController ctrl) throws Exception {
        mRequest = new AllianceRequest.Builder(mRequest)
        .state(AllianceRequest.RequestState.REJECTED).build();

        String sql = "UPDATE alliance_requests SET state = ? WHERE id = ?";
        try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
            stmt.setInt(1, mRequest.state.getValue());
            stmt.setInt(2, mRequest.id);
            stmt.update();
        }
    }

    /**
     * Given that you can't vote for your own requests, what's the maximum number of votes this
     * alliance can cast?
     */
    protected int getTotalPossibleVotes() {
        TreeSet<Integer> excludingEmpires = new TreeSet<Integer>(
                Arrays.asList(new Integer[] {mRequest.request_empire_id}));
        if (mRequest.target_empire_id != null) {
            excludingEmpires.add(mRequest.target_empire_id);
        }
        return AllianceHelper.getTotalPossibleVotes(mAlliance, excludingEmpires);
    }

    private static class JoinRequestProcessor extends AllianceRequestProcessor {
        public JoinRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            String sql = "UPDATE empires SET alliance_id = ?, alliance_rank = ? WHERE id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, mRequest.alliance_id);
                stmt.setInt(2, AllianceMember.Rank.MEMBER.getValue());
                stmt.setInt(3, mRequest.request_empire_id);
                stmt.update();
            }

            // if you have open requests to join other alliances, withdraw those
            sql = "UPDATE alliance_requests SET state = ?" +
                 " WHERE request_type = ?" +
                   " AND state = ?" +
                   " AND request_empire_id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, AllianceRequest.RequestState.WITHDRAWN.getValue());
                stmt.setInt(2, AllianceRequest.RequestType.JOIN.getValue());
                stmt.setInt(3, AllianceRequest.RequestState.PENDING.getValue());
                stmt.setInt(4, mRequest.request_empire_id);
                stmt.update();
            }

            // TODO: send a notification
        }
    }

    private static class LeaveRequestProcessor extends AllianceRequestProcessor {
        public LeaveRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            String sql = "UPDATE empires SET alliance_id = NULL, alliance_rank = NULL WHERE id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, mRequest.request_empire_id);
                stmt.update();
            }

            // TODO: send a notification
        }
    }

    private static class KickRequestProcessor extends AllianceRequestProcessor {
        public KickRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            String sql = "UPDATE empires SET alliance_id = NULL, alliance_rank = NULL WHERE id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, mRequest.target_empire_id);
                stmt.update();
            }

            // TODO: send a notification
        }
    }

    private static class DepositCashRequestProcessor extends AllianceRequestProcessor {
        public DepositCashRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            CashAuditRecord.Builder auditRecord = new CashAuditRecord.Builder()
                    .empire_id(mRequest.request_empire_id)
                    .reason(CashAuditRecord.Reason.AllianceWithdraw);
            if (!new EmpireController(ctrl.getDB().getTransaction()).adjustBalance(
                    mRequest.request_empire_id, -mRequest.amount, auditRecord)) {
                // if the empire didn't have enough cash, then don't proceed...
                return;
            }

            String sql = "UPDATE alliances SET bank_balance = bank_balance + ? WHERE id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setDouble(1, (double) mRequest.amount);
                stmt.setInt(2, mRequest.alliance_id);
                stmt.update();
            }

            sql = "INSERT INTO alliance_bank_balance_audit (alliance_id, alliance_request_id," +
                     " empire_id, date, amount_before, amount_after) VALUES (?, ?, ?, ?, ?, ?)";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, mRequest.alliance_id);
                stmt.setInt(2, mRequest.id);
                stmt.setInt(3, mRequest.request_empire_id);
                stmt.setDateTime(4, DateTime.now());
                stmt.setDouble(5, mAlliance.bank_balance);
                stmt.setDouble(6, mAlliance.bank_balance + mRequest.amount);
                stmt.update();
            }
        }
    }

    private static class WithdrawCashRequestProcessor extends AllianceRequestProcessor {
        public WithdrawCashRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            String sql = "UPDATE alliances SET bank_balance = bank_balance - ? WHERE id = ? AND bank_balance > ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setDouble(1, (double) mRequest.amount);
                stmt.setInt(2, mRequest.alliance_id);
                stmt.setDouble(3, (double) mRequest.amount);
                if (stmt.update() == 0) {
                    // if we didn't update the row, it means there wasn't enough balance anyway...
                    return;
                }
            }

            CashAuditRecord.Builder auditRecord = new CashAuditRecord.Builder()
                    .empire_id(mRequest.request_empire_id)
                    .reason(CashAuditRecord.Reason.AllianceWithdraw);
            new EmpireController(ctrl.getDB().getTransaction()).adjustBalance(
                    mRequest.request_empire_id, mRequest.amount, auditRecord);

            sql = "INSERT INTO alliance_bank_balance_audit (alliance_id, alliance_request_id," +
                     " empire_id, date, amount_before, amount_after) VALUES (?, ?, ?, ?, ?, ?)";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setInt(1, mRequest.alliance_id);
                stmt.setInt(2, mRequest.id);
                stmt.setInt(3, mRequest.request_empire_id);
                stmt.setDateTime(4, DateTime.now());
                stmt.setDouble(5, mAlliance.bank_balance);
                stmt.setDouble(6, mAlliance.bank_balance - mRequest.amount);
                stmt.update();
            }
        }
    }

    private static class ChangeImageRequestProcessor extends AllianceRequestProcessor {
        public ChangeImageRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            // Make sure the image is valid and reasonable dimensions
            byte[] pngImage = ImageSizer.ensureMaxSize(mRequest.png_image.toByteArray(), 128, 128);
            new AllianceController().changeAllianceShield(Integer.parseInt(mAlliance.key),
                    pngImage);
        }
    }

    private static class ChangeNameRequestProcessor extends AllianceRequestProcessor {
        public ChangeNameRequestProcessor(Alliance alliance, AllianceRequest request) {
            super(alliance, request);
        }

        @Override
        protected void onVotePassed(AllianceController ctrl) throws Exception {
            super.onVotePassed(ctrl);

            String sql = "UPDATE alliances SET name = ? WHERE id = ?";
            try (SqlStmt stmt = ctrl.getDB().prepare(sql)) {
                stmt.setString(1, mRequest.new_name);
                stmt.setInt(2, mRequest.alliance_id);
                stmt.update();
            }
        }
    }
}
