package introvert;

import battlecode.common.*;

public class RunTower {

    private static Direction spawnDir = null;
    private static int spawnCooldown = 0;

    public static void run() throws GameActionException {
        RobotController rc = State.rc;

        if (spawnDir == null) {
            spawnDir = findSpawnDirection(rc);
        }

        attackLowest(rc);
        aoeAttack(rc);
        tryUpgrade(rc);

        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }

        if (rc.getRoundNum() == 1) {
            tryBuild(rc, UnitType.SOLDIER, spawnDir);
            return;
        }
        if (rc.getRoundNum() == 2) {
            tryBuild(rc, UnitType.SOLDIER, spawnDir != null ? spawnDir.rotateRight() : Direction.NORTH);
            return;
        }

        UnitType toBuild;
        if (rc.getRoundNum() >= 1000 && rc.getRoundNum() % 3 == 0) {
            toBuild = UnitType.SPLASHER;
        } else {
            int soldierScore = greedySoldierScore(rc);
            int mopperScore = greedyMopperScore(rc);
            int splasherScore = greedySplasherScore(rc);

            if (mopperScore >= soldierScore && mopperScore >= splasherScore) {
                toBuild = UnitType.MOPPER;
            } else if (splasherScore >= soldierScore) {
                toBuild = UnitType.SPLASHER;
            } else {
                toBuild = UnitType.SOLDIER;
            }
        }

        if (rc.getMoney() >= toBuild.moneyCost && rc.getPaint() >= toBuild.paintCost) {
            boolean built = tryBuild(rc, toBuild, spawnDir);
            if (built)
                spawnCooldown = 3;
        }
    }

    private static int greedySoldierScore(RobotController rc) throws GameActionException {
        int score = 5;
        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (tile.hasRuin() && !rc.canSenseRobotAtLocation(tile.getMapLocation())) {
                score += 12;
                break;
            }
        }
        return score;
    }

    private static int greedyMopperScore(RobotController rc) throws GameActionException {
        int score = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (tile.getPaint().isEnemy())
                score += 3;
        }
        for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (bot.getType().isRobotType()) {
                score += 10;
                break;
            }
        }
        return Math.min(score, 30);
    }

    private static int greedySplasherScore(RobotController rc) throws GameActionException {
        int score = 0;
        int enemyPaintCount = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (tile.getPaint().isEnemy())
                enemyPaintCount++;
        }
        if (enemyPaintCount > 5)
            score += 8;
        return score;
    }

    private static void attackLowest(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10)
            return;
        RobotInfo weakest = null;
        int lowestHP = Integer.MAX_VALUE;
        for (RobotInfo bot : rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent())) {
            if (bot.health < lowestHP && rc.canAttack(bot.getLocation())) {
                lowestHP = bot.health;
                weakest = bot;
            }
        }
        if (weakest != null)
            rc.attack(weakest.getLocation());
    }

    private static void aoeAttack(RobotController rc) throws GameActionException {
    }

    private static void tryUpgrade(RobotController rc) throws GameActionException {
        int money = rc.getMoney();
        UnitType type = rc.getType();
        if (type == UnitType.LEVEL_ONE_PAINT_TOWER && money > 5000)
            rc.upgradeTower(State.myLoc);
        if (type == UnitType.LEVEL_ONE_MONEY_TOWER && money > 7500)
            rc.upgradeTower(State.myLoc);
        if (type == UnitType.LEVEL_TWO_PAINT_TOWER && money > 7500)
            rc.upgradeTower(State.myLoc);
        if (type == UnitType.LEVEL_TWO_MONEY_TOWER && money > 10000)
            rc.upgradeTower(State.myLoc);
        if (type == UnitType.LEVEL_ONE_DEFENSE_TOWER && money > 5000)
            rc.upgradeTower(State.myLoc);
        if (type == UnitType.LEVEL_TWO_DEFENSE_TOWER && money > 7500)
            rc.upgradeTower(State.myLoc);
    }

    private static boolean tryBuild(RobotController rc, UnitType type, Direction preferred)
            throws GameActionException {
        if (preferred != null && rc.canBuildRobot(type, State.myLoc.add(preferred))) {
            rc.buildRobot(type, State.myLoc.add(preferred));
            return true;
        }
        for (Direction d : State.DIRECTIONS) {
            if (rc.canBuildRobot(type, State.myLoc.add(d))) {
                rc.buildRobot(type, State.myLoc.add(d));
                return true;
            }
        }
        return false;
    }

    private static Direction findSpawnDirection(RobotController rc) throws GameActionException {
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        Direction toCenter = State.myLoc.directionTo(center);
        for (Direction d : State.DIRECTIONS) {
            if (d == toCenter && rc.canBuildRobot(UnitType.SOLDIER, State.myLoc.add(d)))
                return d;
        }
        for (Direction d : State.DIRECTIONS) {
            if (rc.canBuildRobot(UnitType.SOLDIER, State.myLoc.add(d)))
                return d;
        }
        return Direction.NORTH;
    }
}
