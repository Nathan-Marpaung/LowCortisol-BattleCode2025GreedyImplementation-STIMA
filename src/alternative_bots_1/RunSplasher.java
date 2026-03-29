package alternative_bots_1;

import battlecode.common.*;

public class RunSplasher {

    private static MapLocation expansionTarget = null;
    private static int targetAge = 0;
    private static final int TARGET_TTL = 12;

    public static void run() throws GameActionException {
        RobotController rc = State.rc;
        MapLocation myLoc = State.myLoc;
        targetAge++;

        updateLastPaintTower(rc);

        // Isi ulang paint
        if (rc.getPaint() < State.REFUEL_THRESHOLD_SPLASHER
                && rc.getMoney() < State.LOW_PAINT_MONEY_THRESHOLD) {
            if (!State.isLowPaint) {
                State.isLowPaint = true;
                State.prevLocInfo = rc.senseMapInfo(myLoc);
                State.inBugNav = false;
                State.acrossWall = null;
            }
            lowPaintBehavior(rc);
            return;
        } else if (State.isLowPaint) {
            if (State.removePaint == null && State.prevLocInfo != null) {
                State.removePaint = State.prevLocInfo;
            }
            State.prevLocInfo = null;
            State.isLowPaint = false;
            State.inBugNav = false;
            State.acrossWall = null;
        }

        // Hindari tower musuh
        for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (bot.getType().isTowerType()) {
                MapLocation towerLoc = bot.getLocation();
                if (State.removePaint != null
                        && State.removePaint.getMapLocation().isWithinDistanceSquared(towerLoc, 9)) {
                    State.removePaint = null;
                }
                Direction flee = myLoc.directionTo(towerLoc)
                        .rotateRight().rotateRight().rotateRight();
                if (rc.canMove(flee)) {
                    rc.move(flee);
                    break;
                }
            }
        }

        // Hapus target yang sudah aman
        if (State.removePaint != null
                && rc.canSenseLocation(State.removePaint.getMapLocation())
                && rc.senseMapInfo(State.removePaint.getMapLocation()).getPaint().isAlly()) {
            State.removePaint = null;
        }

        // Cari splash terbaik
        MapInfo splashTarget = findBestSplashTarget(rc, myLoc);

        if (splashTarget != null && rc.canAttack(splashTarget.getMapLocation())) {
            rc.attack(splashTarget.getMapLocation());
            return;
        } else if (splashTarget != null) {
            if (State.removePaint == null)
                State.removePaint = splashTarget;
            Direction dir = Pathfind.bfsPathfind(rc, splashTarget.getMapLocation());
            if (dir != null && rc.canMove(dir))
                rc.move(dir);
            return;
        }

        // Dekati target cat
        if (State.removePaint != null) {
            if (rc.canAttack(State.removePaint.getMapLocation())) {
                rc.attack(State.removePaint.getMapLocation());
                return;
            }
            if (rc.getActionCooldownTurns() < 10) {
                Direction dir = Pathfind.bfsPathfind(rc, State.removePaint.getMapLocation());
                if (dir != null && rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
        }

        // Ekspansi ke area baru
        if (rc.isMovementReady() && State.botRoundNum > 1) {
            refreshExpansionTargetIfNeeded(rc, myLoc);
            if (expansionTarget != null) {
                if (myLoc.isWithinDistanceSquared(expansionTarget, 4) || isWellPainted(rc, expansionTarget)) {
                    expansionTarget = null;
                    targetAge = TARGET_TTL;
                } else {
                    Direction dir = Pathfind.bfsPathfind(rc, expansionTarget);
                    if (dir != null && rc.canMove(dir))
                        rc.move(dir);
                }
            } else {
                Direction d = Pathfind.randomWalk(rc);
                if (d != null && rc.canMove(d))
                    rc.move(d);
            }
        }

        rc.setIndicatorString("SPLASHER | paint=" + rc.getPaint() + " target=" + expansionTarget);
    }

    private static MapInfo findBestSplashTarget(RobotController rc, MapLocation myLoc)
            throws GameActionException {
        MapInfo bestInfo = null;
        int bestScore = 0;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
            MapLocation center = tile.getMapLocation();
            if (!rc.canAttack(center))
                continue;

            int score = 0;

            for (RobotInfo enemy : enemies) {
                if (enemy.getType().isTowerType()
                        && center.isWithinDistanceSquared(enemy.getLocation(), 4)) {
                    score += State.SPLASH_NEAR_ENEMY_TOWER;
                    break;
                }
            }

            for (MapInfo s : rc.senseNearbyMapInfos(center, 2)) {
                if (!s.isPassable())
                    continue;
                PaintType p = s.getPaint();
                if (p.isEnemy())
                    score += State.SPLASH_ENEMY_TO_ALLY;
                else if (p.isAlly())
                    score += State.SPLASH_FRIENDLY_FIRE;
                else if (p == PaintType.EMPTY)
                    score += State.SPLASH_EMPTY_FILL;
            }

            if (score > bestScore) {
                bestScore = score;
                bestInfo = tile;
            }
        }
        return bestInfo;
    }

    // Perbarui target ekspansi
    private static void refreshExpansionTargetIfNeeded(RobotController rc, MapLocation myLoc)
            throws GameActionException {
        if (expansionTarget != null && targetAge < TARGET_TTL)
            return;
        targetAge = 0;

        int[] dirScore = new int[8];
        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (!tile.isPassable())
                continue;
            PaintType p = tile.getPaint();
            if (p.isAlly())
                continue;
            Direction d = myLoc.directionTo(tile.getMapLocation());
            int idx = dirIndex(d);
            if (idx < 0)
                continue;
            dirScore[idx] += (p == PaintType.EMPTY) ? 3 : 1;
        }

        int bestIdx = -1, bestVal = 0;
        for (int i = 0; i < 8; i++) {
            if (dirScore[i] > bestVal) {
                bestVal = dirScore[i];
                bestIdx = i;
            }
        }

        if (bestIdx >= 0 && bestVal > 0) {
            Direction bestDir = State.DIRECTIONS[bestIdx];
            MapLocation target = myLoc;
            for (int i = 0; i < 6; i++) {
                MapLocation next = target.add(bestDir);
                if (rc.onTheMap(next))
                    target = next;
                else
                    break;
            }
            expansionTarget = target.equals(myLoc) ? null : target;
        } else {
            expansionTarget = null;
        }
    }

    private static int dirIndex(Direction d) {
        for (int i = 0; i < State.DIRECTIONS.length; i++) {
            if (State.DIRECTIONS[i] == d)
                return i;
        }
        return -1;
    }

    // Cek area sudah cukup terwarnai
    private static boolean isWellPainted(RobotController rc, MapLocation loc)
            throws GameActionException {
        if (!rc.canSenseLocation(loc))
            return false;
        int total = 0, ally = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(loc, 4)) {
            if (!tile.isPassable())
                continue;
            total++;
            if (tile.getPaint().isAlly())
                ally++;
        }
        return total > 0 && ally * 100 / total >= 70;
    }

    private static void lowPaintBehavior(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemy.getType().isTowerType() && rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                break;
            }
        }
        if (State.lastTower == null) {
            Direction d = Pathfind.randomPaintedWalk(rc);
            if (d != null && rc.canMove(d))
                rc.move(d);
            return;
        }
        MapLocation towerLoc = State.lastTower.getMapLocation();
        Direction dir = Pathfind.bfsPathfind(rc, towerLoc);
        if (dir != null && rc.canMove(dir))
            rc.move(dir);

        if (rc.canSenseRobotAtLocation(towerLoc)) {
            int towerPaint = rc.senseRobotAtLocation(towerLoc).paintAmount;
            if (rc.getPaint() < 5 && rc.canTransferPaint(towerLoc, -towerPaint)
                    && towerPaint > State.MIN_PAINT_GIVE) {
                rc.transferPaint(towerLoc, -towerPaint);
            }
        }
        int amtTransfer = rc.getPaint() - rc.getType().paintCapacity;
        if (rc.canTransferPaint(towerLoc, amtTransfer))
            rc.transferPaint(towerLoc, amtTransfer);
    }

    // Simpan paint tower terdekat
    private static void updateLastPaintTower(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        MapInfo best = null;
        for (MapInfo loc : rc.senseNearbyMapInfos()) {
            if (!loc.hasRuin())
                continue;
            MapLocation ml = loc.getMapLocation();
            if (!rc.canSenseRobotAtLocation(ml))
                continue;
            RobotInfo bot = rc.senseRobotAtLocation(ml);
            if (bot == null || !bot.getTeam().equals(rc.getTeam()))
                continue;
            if (bot.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                int d = ml.distanceSquaredTo(State.myLoc);
                if (d < minDist) {
                    minDist = d;
                    best = loc;
                }
            }
        }
        if (minDist != Integer.MAX_VALUE)
            State.lastTower = best;
        else if (State.lastTower != null
                && State.lastTower.getMapLocation().isWithinDistanceSquared(State.myLoc, 20)) {
            State.lastTower = null;
        }
    }
}
