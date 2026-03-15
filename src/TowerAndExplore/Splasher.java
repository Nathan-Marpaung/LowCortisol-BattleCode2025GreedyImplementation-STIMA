package TowerAndExplore;

import battlecode.common.*;

public class Splasher {

    public static void run(RobotController rc) throws GameActionException {
        if (rc.isActionReady()) {
            boolean hit = splashAtEnemies(rc);
            if (!hit) {
                splashBestTile(rc);
            }
        }

        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getLocation(), 8)) {
            if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                if (rc.isActionReady() && rc.canAttack(tile.getMapLocation())) {
                    boolean useSecondary = tile.getMark() == PaintType.ALLY_SECONDARY;
                    rc.attack(tile.getMapLocation(), useSecondary);
                    break;
                }
            }
        }

        if (rc.isMovementReady()) {
            MapLocation target = nearestEnemy(rc);
            if (target != null) {
                Util.moveGreedy(rc, target);
            } else {
                target = Util.findNearestUnpainted(rc);
                if (target != null) {
                    Util.moveGreedy(rc, target);
                } else {
                    Util.randomMove(rc);
                }
            }
        }

        if (rc.isActionReady()) {
            boolean hit = splashAtEnemies(rc);
            if (!hit) {
                splashBestTile(rc);
            }
        }
    }

    static MapLocation nearestEnemy(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            int d = rc.getLocation().distanceSquaredTo(enemy.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = enemy.getLocation();
            }
        }

        if (best != null) return best;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.hasRuin() && tile.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = tile.getMapLocation();
                }
            }
        }

        return best;
    }

    static boolean splashAtEnemies(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int bestScore = 0;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc) || tile.isWall())
                continue;

            int score = 0;

            if (rc.canSenseLocation(loc)) {
                RobotInfo bot = rc.senseRobotAtLocation(loc);
                if (bot != null && bot.getTeam() == rc.getTeam().opponent()) {
                    score += 10;
                } else if (bot != null && bot.getTeam() == rc.getTeam()) {
                    score -= 5;
                }
            }

            for (Direction d : Util.directions) {
                MapLocation n = loc.add(d);
                if (!rc.canSenseLocation(n)) continue;
                RobotInfo bot = rc.senseRobotAtLocation(n);
                if (bot != null && bot.getTeam() == rc.getTeam().opponent()) {
                    score += 4;
                } else if (bot != null && bot.getTeam() == rc.getTeam()) {
                    score -= 2;
                }
            }

            if (tile.getPaint().isEnemy()) score += 2;
            else if (tile.getPaint() == PaintType.EMPTY) score += 1;

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }

        if (bestLoc != null && bestScore > 0) {
            rc.attack(bestLoc);
            return true;
        }
        return false;
    }

    static void splashBestTile(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int bestScore = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc) || tile.isWall())
                continue;

            int score = 0;
            if (tile.getPaint().isEnemy())
                score += 5;
            else if (tile.getPaint() == PaintType.EMPTY)
                score += 3;
            else
                continue;

            for (Direction d : Util.directions) {
                MapLocation n = loc.add(d);
                if (!rc.canSenseLocation(n)) continue;
                MapInfo ni = rc.senseMapInfo(n);
                if (ni.isWall()) continue;
                if (ni.getPaint().isEnemy())
                    score += 3;
                else if (ni.getPaint() == PaintType.EMPTY)
                    score += 2;
            }

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }
        if (bestLoc != null) {
            rc.attack(bestLoc);
        }
    }
}
