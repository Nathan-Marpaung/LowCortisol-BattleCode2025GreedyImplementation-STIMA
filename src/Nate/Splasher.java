package Nate;

import battlecode.common.*;

public class Splasher {

    public static void run(RobotController rc) throws GameActionException {
        // Priority 1: Attack enemy bots or ruins if in range
        if (rc.isActionReady()) {
            boolean attackedEntity = attackBestSplashTileEnemyPrioritized(rc);
            if (!attackedEntity) {
                // If no enemy bot/ruin was attacked, try finding a good tile to splash
                attackBestSplashTile(rc);
            }
        }

        // Priority 2: Move toward enemy bots or enemy ruins
        if (rc.isMovementReady()) {
            MapLocation target = findNearestEnemyEntity(rc);
            if (target != null) {
                Util.moveGreedy(rc, target);
            } else {
                // Priority 3: Move toward unpainted territory
                target = Util.findNearestUnpainted(rc);
                if (target != null) {
                    Util.moveGreedy(rc, target);
                } else {
                    Util.randomMove(rc);
                }
            }
        }

        // Attack again after moving
        if (rc.isActionReady()) {
            boolean attackedEntity = attackBestSplashTileEnemyPrioritized(rc);
            if (!attackedEntity) {
                attackBestSplashTile(rc);
            }
        }
    }

    /**
     * Finds the nearest enemy robot or enemy-controlled ruin.
     */
    static MapLocation findNearestEnemyEntity(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;

        // Check for nearest enemy robot
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            int d = rc.getLocation().distanceSquaredTo(enemy.getLocation());
            if (d < bestDist) {
                bestDist = d;
                bestLoc = enemy.getLocation();
            }
        }

        // If an enemy robot is found, return it (they move, higher priority)
        if (bestLoc != null) {
            return bestLoc;
        }

        // Check for nearest enemy ruin/tower structure
        // Since we only sense robots and not towers directly, towers are included in senseNearbyRobots.
        // But let's act as a fallback: if no bots around, check if any nearby tiles have enemy paint pattern or are enemy ruins
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.hasRuin() && tile.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestDist) {
                    bestDist = d;
                    bestLoc = tile.getMapLocation();
                }
            }
        }

        return bestLoc;
    }

    /**
     * Prioritize splashing an area that directly damages enemy robots.
     * Returns true if it successfully attacked.
     */
    static boolean attackBestSplashTileEnemyPrioritized(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int bestScore = 0;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc) || tile.isWall())
                continue;

            int score = 0;

            // Does center have a robot?
            if (rc.canSenseLocation(loc)) {
                RobotInfo bot = rc.senseRobotAtLocation(loc);
                if (bot != null && bot.getTeam() == rc.getTeam().opponent()) {
                    score += 10;
                } else if (bot != null && bot.getTeam() == rc.getTeam()) {
                    score -= 5;
                }
            }

            // Check neighbors for robots
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

            // Include paint score to break ties
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

    /**
     * Find the tile that maximizes number of non-ally tiles covered by the splash.
     * ONLY targets tiles where at least some non-ally paint exists (never wastes on
     * all-ally areas).
     */
    static void attackBestSplashTile(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int bestScore = 0; // Must be > 0 to bother attacking.
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc))
                continue;
            if (tile.isWall())
                continue;

            int score = 0;
            // Score center tile.
            if (tile.getPaint().isEnemy())
                score += 5;
            else if (tile.getPaint() == PaintType.EMPTY)
                score += 3;
            else
                continue; // Center is already ally — skip to avoid waste.

            // Score neighbors.
            for (Direction d : Util.directions) {
                MapLocation n = loc.add(d);
                if (!rc.canSenseLocation(n))
                    continue;
                MapInfo ni = rc.senseMapInfo(n);
                if (ni.isWall())
                    continue;
                if (ni.getPaint().isEnemy())
                    score += 3;
                else if (ni.getPaint() == PaintType.EMPTY)
                    score += 2;
                // ally tiles get 0 — don't waste paint on them.
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
