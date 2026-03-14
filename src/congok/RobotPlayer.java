package congok;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    static RobotController rc;
    static Random rng;

    @SuppressWarnings("unused")
    public static void run(RobotController start_rc) throws GameActionException {
        rc=start_rc;
        rng=new Random(rc.getID());

        while (true) {
            try {
                dispatch();
            } catch (Exception e) {
                rc.setIndicatorString("ERR: "+e.getMessage());
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void dispatch() throws GameActionException {
        if (rc.getType().isTowerType()) {
            TowerPlayer.run(rc);
            return;
        }

        switch (rc.getType()) {
            case SOLDIER:
                SoldierPlayer.run(rc);
                break;
            case SPLASHER:
                SplasherPlayer.run(rc);
                break;
            default:
                break;
        }
    }
}
