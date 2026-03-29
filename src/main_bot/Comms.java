package main_bot;

import battlecode.common.*;

public class Comms {
    private static final int max_local_claims=10;
    private static final MapLocation[] local_claims=new MapLocation[max_local_claims];

    static UnitType getNextTowerType(RobotController rc) {
        int built_since_start=Math.max(0,rc.getNumberTowers()-GameConstants.NUMBER_INITIAL_TOWERS);
        return (built_since_start%2==0)?UnitType.LEVEL_ONE_PAINT_TOWER:UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static void onTowerDone(RobotController rc) {
    }

    static boolean isRuinClaimed(RobotController rc, MapLocation ruins) {
        for (MapLocation claimed : local_claims) {
            if (ruins.equals(claimed)) return true;
        }
        return false;
    }

    static boolean claimRuin(RobotController rc, MapLocation ruins) {
        for (int i=0; i<local_claims.length; i++) {
            if (local_claims[i]==null) {
                local_claims[i]=ruins;
                return true;
            }
        }
        return false;
    }

    static void releaseRuin(RobotController rc, MapLocation ruins) {
        for (int i=0; i<local_claims.length; i++) {
            if (ruins.equals(local_claims[i])) {
                local_claims[i]=null;
                return;
            }
        }
    }
}
