package TowerAndExplore;

import battlecode.common.*;

public class Tower {

    static int soldierCount = 0;
    static int spawnIdx = 0;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            UnitType next = pickSpawn();
            boolean built = tryBuild(rc, next);
            if (built) {
                spawnIdx++;
                if (next == UnitType.SOLDIER) soldierCount++;
            } else {
                break;
            }
        }

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();
            RobotInfo occupant = rc.canSenseLocation(ruinLoc) ? rc.senseRobotAtLocation(ruinLoc) : null;
            if (occupant != null && occupant.getTeam() == rc.getTeam()) continue;
            UnitType t = Soldier.pickTowerType(ruinLoc);
            if (rc.canMarkTowerPattern(t, ruinLoc)) rc.markTowerPattern(t, ruinLoc);
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                break;
            }
        }
    }

    static UnitType pickSpawn() {
        int idx = spawnIdx % 5;
        if (soldierCount <= 10) {
            switch (idx) {
                case 0: 
                case 1: 
                case 2: 
                    return UnitType.SOLDIER;
                case 3:
                    return UnitType.MOPPER; 
                case 4: 
                    return UnitType.SPLASHER;
            }
            return UnitType.SOLDIER;
        }
        switch (idx) {
            case 0: 
            case 3: 
                return UnitType.SOLDIER;
            case 1: 
                return UnitType.MOPPER;
            case 2: 
            case 4: 
                return UnitType.SPLASHER;
        }
        return UnitType.SOLDIER;
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
