package TowerAndExplore;

import battlecode.common.*;

public class Soldier {

    public static void run(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapInfo curRuin = null;
        int closestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                if (rc.canSenseLocation(ruinLoc)) {
                    RobotInfo bot = rc.senseRobotAtLocation(ruinLoc);
                    if (bot != null && bot.getTeam() == rc.getTeam() && bot.getType().isTowerType()) {
                        continue;
                    }
                }
                int dist = rc.getLocation().distanceSquaredTo(ruinLoc);
                if (dist < closestDist) {
                    closestDist = dist;
                    curRuin = tile;
                }
            }
        }

        if (curRuin != null) {
            MapLocation targetLoc = curRuin.getMapLocation();
            UnitType towerType = pickTowerType(targetLoc);

            Direction dir = rc.getLocation().directionTo(targetLoc);
            if (rc.canMove(dir))
                rc.move(dir);

            MapLocation checkMark = targetLoc.subtract(dir);
            if (rc.canSenseLocation(checkMark)
                    && rc.senseMapInfo(checkMark).getMark() == PaintType.EMPTY
                    && rc.canMarkTowerPattern(towerType, targetLoc)) {
                rc.markTowerPattern(towerType, targetLoc);
            }

            for (MapInfo tile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                if (tile.getMark() != tile.getPaint() && tile.getMark() != PaintType.EMPTY) {
                    boolean useSecondary = tile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(tile.getMapLocation()))
                        rc.attack(tile.getMapLocation(), useSecondary);
                }
            }

            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
        }

        Direction dir = Util.directions[Util.rng.nextInt(Util.directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }

        MapInfo here = rc.senseMapInfo(rc.getLocation());
        if (!here.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }

        if (rc.getRoundNum() > Util.earlyEnd && rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo enemy : enemies) {
                if (rc.canAttack(enemy.getLocation())) {
                    rc.attack(enemy.getLocation());
                    break;
                }
            }
        }
    }

    static UnitType pickTowerType(MapLocation ruinLoc) {
        if (ruinLoc == null)
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        int hash = Math.abs(ruinLoc.x * 31 + ruinLoc.y);
        if (hash % 3 == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }
}
