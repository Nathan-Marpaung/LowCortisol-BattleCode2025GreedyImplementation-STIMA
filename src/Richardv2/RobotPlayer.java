package Richardv2;

import battlecode.common.*;

public class RobotPlayer {
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                State.init(rc);
                State.updateLast16();
                switch (rc.getType()){
                    case SOLDIER: 
                        RunSoldier.run(); 
                        break; 
                    case MOPPER: 
                        RunMopper.run(); 
                        break;
                    case SPLASHER: 
                        RunSplasher.run();
                        break;
                    default: 
                        RunTower.run(); 
                        break;
                }
            }
            catch (GameActionException e) {
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
