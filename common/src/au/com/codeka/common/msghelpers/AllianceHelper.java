package au.com.codeka.common.msghelpers;

import java.util.Set;

import au.com.codeka.common.messages.Alliance;
import au.com.codeka.common.messages.AllianceMember;

public class AllianceHelper {
    /** Gets the number of votes an alliance member of the given rank gets. */
    public static int getNumVotes(AllianceMember.Rank rank) {
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

    /**
     * Gets the total possible votes the given alliance can make, not including votes from the
     * given set of empires.
     */
    public static int getTotalPossibleVotes(Alliance alliance, Set<Integer> excludingEmpires) {
        int totalVotes = 0;
        for (AllianceMember member :alliance.members) {
            int memberEmpireID = Integer.parseInt(member.empire_key);
            if (excludingEmpires.contains(memberEmpireID)) {
                continue;
            }

            totalVotes += getNumVotes(member.rank);
        }
        return totalVotes;
    }

}
