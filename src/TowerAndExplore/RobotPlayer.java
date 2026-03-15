package TowerAndExplore;

import battlecode.common.*;

public class RobotPlayer {

    static int turnCount = 0;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        Soldier.run(rc);
                        break;
                    case MOPPER:
                        Mopper.run(rc);
                        break;
                    case SPLASHER:
                        Splasher.run(rc);
                        break;
                    default:
                        Tower.run(rc);
                        break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
