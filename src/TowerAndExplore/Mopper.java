package TowerAndExplore;

import battlecode.common.*;

public class Mopper {

    static final int minPaint = 30;
    static final int giveAmount = 40;
    static final int lowPaint = 50;

    public static void run(RobotController rc) throws GameActionException {
        givePaint(rc);

        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0) {
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

        MapLocation enemyPaint = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestDist) {
                    bestDist = d;
                    enemyPaint = tile.getMapLocation();
                }
            }
        }

        if (enemyPaint != null) {
            if (rc.isMovementReady()) {
                Util.moveGreedy(rc, enemyPaint);
            }
            if (rc.isActionReady()) {
                for (MapInfo tile : rc.senseNearbyMapInfos()) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
            }
            return;
        }

        if (rc.isMovementReady()) {
            MapLocation target = Util.findNearestUnpainted(rc);
            if (target != null) {
                Util.moveGreedy(rc, target);
            } else {
                Util.randomMove(rc);
            }
        }

        if (rc.isActionReady()) {
            for (MapInfo tile : rc.senseNearbyMapInfos()) {
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    break;
                }
            }
        }
    }

    static boolean givePaint(RobotController rc) throws GameActionException {
        if (rc.getPaint() <= minPaint)
            return false;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo target = null;
        int mostNeeded = 0;
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() || ally.getType() == UnitType.MOPPER)
                continue;
            int need = ally.getType().paintCapacity - ally.paintAmount;
            if (need > lowPaint && need > mostNeeded) {
                mostNeeded = need;
                target = ally;
            }
        }
        if (target == null)
            return false;
        int amount = Math.min(giveAmount, Math.min(mostNeeded, rc.getPaint() - minPaint));
        if (amount <= 0)
            return false;
        if (rc.canTransferPaint(target.getLocation(), amount)) {
            rc.transferPaint(target.getLocation(), amount);
            return true;
        }
        return false;
    }
}
