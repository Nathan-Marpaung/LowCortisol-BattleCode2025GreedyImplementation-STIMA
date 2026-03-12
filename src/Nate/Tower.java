package Nate;

import battlecode.common.*;

public class Tower {

    static int spawnCounter = 0;

    public static void run(RobotController rc) throws GameActionException {
        // --- Spawn units ---
        // Spend leftover chips by spawning as many per turn as possible
        while (true) {
            UnitType toSpawn = chooseSpawnType(rc.getRoundNum());
            boolean built = tryBuild(rc, toSpawn);
            if (!built) {
                for (UnitType fallback : new UnitType[] { UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER }) {
                    if (fallback != toSpawn && tryBuild(rc, fallback)) {
                        built = true;
                        break;
                    }
                }
            }
            if (built) {
                spawnCounter++;
            } else {
                break; // Could not build any more
            }
        }

        // --- Tower attacks nearest enemy ---
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                break;
            }
        }
    }

    /**
     * Deterministic spawn cycle (no RNG — guarantees ratios).
     * Early: [SOLDIER, SOLDIER, MOPPER, SOLDIER, SPLASHER] → 60/20/20
     * Late: [SPLASHER, SOLDIER, MOPPER, SOLDIER, SPLASHER] → 40/40/20 (gentle splasher bias)
     */
    static UnitType chooseSpawnType(int round) {
        int idx = spawnCounter % 5;
        if (round <= Util.EARLY_PHASE_END) {
            // Early: prioritize soldiers to explore ruins.
            switch (idx) {
                case 0:
                case 1:
                case 3:
                    return UnitType.SOLDIER;
                case 2:
                    return UnitType.MOPPER;
                default:
                    return UnitType.SPLASHER;
            }
        } else {
            // Late: keep soldiers dominant, but still strong splasher presence.
            switch (idx) {
                case 0:
                case 4:
                    return UnitType.SPLASHER;
                case 2: 
                    return UnitType.MOPPER;
                case 1:
                    return UnitType.SOLDIER;
                case 3:
                    return UnitType.SOLDIER;
                default:
                    return UnitType.SOLDIER;
            }
        }
    }

    static boolean tryBuild(RobotController rc, UnitType type) throws GameActionException {
        for (Direction dir : Util.directions) {
            MapLocation loc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, loc)) {
                rc.buildRobot(type, loc);
                return true;
            }
        }
        return false;
    }
}
