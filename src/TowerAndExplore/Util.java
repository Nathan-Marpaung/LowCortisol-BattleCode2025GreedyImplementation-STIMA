package TowerAndExplore;

import battlecode.common.*;
import java.util.Random;

public class Util {

    public static final int earlyEnd = 800;
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
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int nearbyAllies = rc.senseNearbyRobots(9, rc.getTeam()).length;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isAlly() || tile.isWall() || isOurRuin(rc, tile))
                continue;

            MapLocation loc = tile.getMapLocation();
            int dist = myLoc.distanceSquaredTo(loc);

            double score = tile.getPaint().isEnemy() ? 20 : 8;
            score += dist * 0.6;
            score -= nearbyAllies * 4;

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    public static MapLocation findNearestAllyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(ally.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = ally.getLocation();
            }
        }
        return best;
    }

    public static void moveGreedy(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
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
        MapLocation loc = rc.getLocation();
        int offset = (loc.x * 7 + loc.y * 13 + rc.getRoundNum()) & 7;
        for (int i = 0; i < 8; i++) {
            Direction d = directions[(offset + i) & 7];
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }

    public static boolean isOurRuin(RobotController rc, MapInfo tile) throws GameActionException {
        if (tile.hasRuin()) {
            MapLocation ruinLoc = tile.getMapLocation();
            if (rc.canSenseLocation(ruinLoc)) {
                RobotInfo bot = rc.senseRobotAtLocation(ruinLoc);
                if (bot != null && bot.getTeam() == rc.getTeam() && bot.getType().isTowerType()) {
                    return true;
                }
            }
        }
        return false;
    }
}
