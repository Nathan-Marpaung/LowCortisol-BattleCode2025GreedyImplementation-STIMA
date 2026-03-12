package Nate;

import battlecode.common.*;
import java.util.Random;

public class Util {

    public static final int EARLY_PHASE_END = 800;
    public static final Random rng = new Random(6147);

    public static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public static MapLocation findNearestUnpainted(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isAlly() || tile.isWall())
                continue;
            int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                best = tile.getMapLocation();
            }
        }
        return best;
    }

    public static MapLocation findNearestAllyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType())
                continue;
            int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = ally.getLocation();
            }
        }
        return best;
    }

    public static void moveGreedy(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady())
            return;
        Direction main = rc.getLocation().directionTo(target);
        Direction[] tries = {
                main, main.rotateLeft(), main.rotateRight(),
                main.rotateLeft().rotateLeft(), main.rotateRight().rotateRight()
        };
        for (Direction d : tries) {
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
        randomMove(rc);
    }

    public static void randomMove(RobotController rc) throws GameActionException {
        for (int i = 0; i < 16; i++) {
            Direction d = directions[rng.nextInt(directions.length)];
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }
}
