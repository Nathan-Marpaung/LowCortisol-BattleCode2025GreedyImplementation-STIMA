package Nate;

import battlecode.common.*;

public class Mopper {

    // ---- Paint thresholds for mopper ----
    static final int MOPPER_MIN_RESERVE = 30;
    static final int MOPPER_TRANSFER_AMOUNT = 40;
    static final int LOW_PAINT_THRESHOLD = 50;

    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        // ---- Priority 1: Transfer paint to low-paint allies ----
        tryTransferPaint(rc);

        // ---- Priority 2: Mop swing at nearby enemies ----
        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0) {
                // Swing in the direction of the closest enemy.
                RobotInfo closest = enemies[0];
                int closestDist = Integer.MAX_VALUE;
                for (RobotInfo enemy : enemies) {
                    int d = rc.getLocation().distanceSquaredTo(enemy.getLocation());
                    if (d < closestDist) {
                        closestDist = d;
                        closest = enemy;
                    }
                }
                Direction toEnemy = rc.getLocation().directionTo(closest.getLocation());
                if (rc.canMopSwing(toEnemy)) {
                    rc.mopSwing(toEnemy);
                }
            }
        }

        // ---- Priority 3: Find and remove enemy paint ----
        MapLocation enemyPaintLoc = null;
        int bestEnemyDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestEnemyDist) {
                    bestEnemyDist = d;
                    enemyPaintLoc = tile.getMapLocation();
                }
            }
        }

        if (enemyPaintLoc != null) {
            // Move toward enemy paint.
            if (rc.isMovementReady()) {
                Util.moveGreedy(rc, enemyPaintLoc);
            }
            // Attack enemy paint to remove it.
            if (rc.isActionReady()) {
                // Refresh search after moving.
                for (MapInfo tile : rc.senseNearbyMapInfos()) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
            }
            return;
        }

        // ---- Priority 4: actively seek unpainted/enemy territory instead of pushing
        // blindly
        if (rc.isMovementReady()) {
            MapLocation target = Util.findNearestUnpainted(rc);
            if (target != null) {
                Util.moveGreedy(rc, target);
            } else {
                Util.randomMove(rc);
            }
        }

        // After moving, try to mop any enemy paint we've reached.
        if (rc.isActionReady()) {
            for (MapInfo tile : rc.senseNearbyMapInfos()) {
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    break;
                }
            }
        }
    }

    static boolean tryTransferPaint(RobotController rc) throws GameActionException {
        if (rc.getPaint() <= MOPPER_MIN_RESERVE)
            return false;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo bestAlly = null;
        int bestNeed = 0;
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() || ally.getType() == UnitType.MOPPER)
                continue;
            int need = ally.getType().paintCapacity - ally.paintAmount;
            if (need > LOW_PAINT_THRESHOLD && need > bestNeed) {
                bestNeed = need;
                bestAlly = ally;
            }
        }
        if (bestAlly == null)
            return false;
        int amount = Math.min(MOPPER_TRANSFER_AMOUNT, Math.min(bestNeed, rc.getPaint() - MOPPER_MIN_RESERVE));
        if (amount <= 0)
            return false;
        if (rc.canTransferPaint(bestAlly.getLocation(), amount)) {
            rc.transferPaint(bestAlly.getLocation(), amount);
            return true;
        }
        return false;
    }
}
