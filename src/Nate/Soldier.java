package Nate;

import battlecode.common.*;

public class Soldier {

    public static void run(RobotController rc) throws GameActionException {
        // ---- Step 1: Look for any nearby ruin ----
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapInfo curRuin = null;
        int closestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                // Skip ruins that already have an allied tower.
                if (rc.canSenseLocation(ruinLoc)) {
                    RobotInfo robotAt = rc.senseRobotAtLocation(ruinLoc);
                    if (robotAt != null && robotAt.getTeam() == rc.getTeam()
                            && robotAt.getType().isTowerType()) {
                        continue; // Already built here.
                    }
                }
                int dist = rc.getLocation().distanceSquaredTo(ruinLoc);
                if (dist < closestDist) {
                    closestDist = dist;
                    curRuin = tile;
                }
            }
        }

        // ---- Step 2: If we found a ruin, work on it ----
        // This block is directly modeled on examplefuncsplayer lines 185-210.
        if (curRuin != null) {
            MapLocation targetLoc = curRuin.getMapLocation();
            UnitType towerType = chooseTowerType(targetLoc);

            // Move toward the ruin.
            Direction dir = rc.getLocation().directionTo(targetLoc);
            if (rc.canMove(dir))
                rc.move(dir);

            // Mark the pattern if not already marked.
            // Check a tile next to the ruin to see if marks exist.
            MapLocation shouldBeMarked = targetLoc.subtract(dir);
            if (rc.canSenseLocation(shouldBeMarked)
                    && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                    && rc.canMarkTowerPattern(towerType, targetLoc)) {
                rc.markTowerPattern(towerType, targetLoc);
                System.out.println("Marked tower pattern at " + targetLoc);
            }

            // Fill in any spots in the 5x5 pattern with appropriate paint.
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                if (patternTile.getMark() != patternTile.getPaint()
                        && patternTile.getMark() != PaintType.EMPTY) {
                    boolean useSecondary = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation()))
                        rc.attack(patternTile.getMapLocation(), useSecondary);
                }
            }

            // Complete the tower if the pattern is done.
            // Try all Level 1 types to handle patterns marked by other soldiers.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built MONEY tower at " + targetLoc);
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built PAINT tower at " + targetLoc);
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built DEFENSE tower at " + targetLoc);
            }
        }

        // ---- Step 3: Random movement if no ruin objective ----
        Direction dir = Util.directions[Util.rng.nextInt(Util.directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }

        // ---- Step 4: Paint under self (ONLY if not ally-painted) ----
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }

        // ---- Step 5 (mid-late): Attack enemies if in range ----
        if (rc.getRoundNum() > Util.EARLY_PHASE_END && rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo enemy : enemies) {
                if (rc.canAttack(enemy.getLocation())) {
                    rc.attack(enemy.getLocation());
                    break;
                }
            }
        }
    }

    /**
     * Choose tower type based on the ruin location to guarantee a 1:2 ratio
     * distributed evenly, avoiding static var traps since vars reset per bot.
     */
    static UnitType chooseTowerType(MapLocation ruinLoc) {
        if (ruinLoc == null)
            return UnitType.LEVEL_ONE_MONEY_TOWER;

        // Simple spatial hashing function combining X and Y coordinates.
        int hash = Math.abs(ruinLoc.x * 31 + ruinLoc.y);

        // Mod 3 gives 0, 1, or 2. We use 0 for PAINT to guarantee 33% paint towers.
        if (hash % 3 == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }
}
