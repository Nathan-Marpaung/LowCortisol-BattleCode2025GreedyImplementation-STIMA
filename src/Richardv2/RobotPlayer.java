package Richardv2;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static MapLocation lastLocation = null;
    static MapLocation lastLastLocation = null;
    static MapLocation explorationTarget = null;
    static int explorationTargetRefreshRound = -999;
    static final int EARLY_GAME_END = 200;
    static final int MID_GAME_END = 600;
    enum GamePhase {EARLY, MID, LATE};


    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                trackMovementMemory(rc);
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        UnitType primary;
        UnitType secondary;
        if ((rc.getRoundNum() + rc.getID()) % 2 == 0) {
            primary = UnitType.SOLDIER;
            secondary = UnitType.MOPPER;
        } else {
            primary = UnitType.MOPPER;
            secondary = UnitType.SOLDIER;
        }

        boolean built = tryBuildAnyDirection(rc, primary);
        if (!built) built = tryBuildAnyDirection(rc, secondary);

        // Use spare economy to increase map presence.
        if (!built && rc.getMoney() > UnitType.SOLDIER.moneyCost * 2) {
            tryBuildAnyDirection(rc, UnitType.SOLDIER);
        }

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            MapLocation enemyLoc = enemy.getLocation();
            if (rc.canAttack(enemyLoc)) {
                rc.attack(enemyLoc);
                break;
            }
        }
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    // public static void runSoldier(RobotController rc) throws GameActionException{
    //     // Sense information about all visible nearby tiles.
    //     MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
    //     // Search for a nearby ruin to complete.
    //     MapInfo curRuin = null;
    //     for (MapInfo tile : nearbyTiles){
    //         if (tile.hasRuin()){
    //             curRuin = tile;
    //         }
    //     }
    //     if (curRuin != null){
    //         MapLocation targetLoc = curRuin.getMapLocation();
    //         Direction dir = rc.getLocation().directionTo(targetLoc);
    //         if (rc.canMove(dir))
    //             rc.move(dir);
    //         // Mark the pattern we need to draw to build a tower here if we haven't already.
    //         MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
    //         if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
    //             rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
    //             System.out.println("Trying to build a tower at " + targetLoc);
    //         }
    //         // Fill in any spots in the pattern with the appropriate paint.
    //         for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
    //             if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
    //                 boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
    //                 if (rc.canAttack(patternTile.getMapLocation()))
    //                     rc.attack(patternTile.getMapLocation(), useSecondaryColor);
    //             }
    //         }
    //         // Complete the ruin if we can.
    //         if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
    //             rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
    //             rc.setTimelineMarker("Tower built", 0, 255, 0);
    //             System.out.println("Built a tower at " + targetLoc + "!");
    //         }
    //     }

    //     // Move and attack randomly if no objective.
    //     Direction dir = directions[rng.nextInt(directions.length)];
    //     MapLocation nextLoc = rc.getLocation().add(dir);
    //     if (rc.canMove(dir)){
    //         rc.move(dir);
    //     }
    //     // Try to paint beneath us as we walk to avoid paint penalties.
    //     // Avoiding wasting paint by re-painting our own tiles.
    //     MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
    //     if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
    //         rc.attack(rc.getLocation());
    //     }
    // }

    public static void runSoldier(RobotController rc) throws GameActionException{
        GamePhase phase = getPhase(rc);
        MapLocation ruinTarget = findNearestRuin(rc);
        boolean shouldExplore = shouldExplore(rc, phase, ruinTarget);
        MapLocation curExploreTarget = getOrRefreshExplorationTarget(rc, phase, shouldExplore);

        rc.setIndicatorString("Soldier " + phase + " explore=" + shouldExplore + " target=" + curExploreTarget);
        tryRuinActions(rc, ruinTarget);
        tryGreedyPaint(rc);

        Direction bestDir = chooseBestMoveSoldier(rc, phase, ruinTarget, shouldExplore, curExploreTarget);
        if (bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }

        tryRuinActions(rc, ruinTarget);
        tryGreedyPaint(rc);

        MapLocation paintTarget = chooseBestPaintTarget(rc);
        if (paintTarget != null && rc.canAttack(paintTarget)) {
            rc.attack(paintTarget);
        }
    }

    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    // public static void runMopper(RobotController rc) throws GameActionException{
    //     // Move and attack randomly.
    //     Direction dir = directions[rng.nextInt(directions.length)];
    //     MapLocation nextLoc = rc.getLocation().add(dir);
    //     if (rc.canMove(dir)){
    //         rc.move(dir);
    //     }
    //     if (rc.canMopSwing(dir)){
    //         rc.mopSwing(dir);
    //         System.out.println("Mop Swing! Booyah!");
    //     }
    //     else if (rc.canAttack(nextLoc)){
    //         rc.attack(nextLoc);
    //     }
    //     // We can also move our code into different methods or classes to better organize it!
    //     updateEnemyRobots(rc);
    // }

    public static void runMopper(RobotController rc) throws GameActionException{
        GamePhase phase = getPhase(rc);
        boolean shouldExplore = shouldExplore(rc, phase, null);
        MapLocation curExploreTarget = getOrRefreshExplorationTarget(rc, phase, shouldExplore);
        rc.setIndicatorString("Mopper " + phase + " explore=" + shouldExplore + " target=" + curExploreTarget);

        Direction bestDir = chooseBestMoveMopper(rc, phase, shouldExplore, curExploreTarget);
        if (bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }

        Direction mopDir = bestMopDirection(rc);
        if (mopDir != null && rc.canMopSwing(mopDir)) {
            rc.mopSwing(mopDir);
        } else {
            tryGreedyPaint(rc);
            MapLocation paintTarget = chooseBestPaintTarget(rc);
            if (paintTarget != null && rc.canAttack(paintTarget)) {
                rc.attack(paintTarget);
            }
        }

        updateEnemyRobots(rc);
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically 
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }

   public static GamePhase getPhase(RobotController rc) {
        int round = rc.getRoundNum();
        if (round <= EARLY_GAME_END) return GamePhase.EARLY;
        if (round <= MID_GAME_END) return GamePhase.MID;
        return GamePhase.LATE;
    }

    public static void trackMovementMemory(RobotController rc) {
        lastLastLocation = lastLocation;
        lastLocation = rc.getLocation();
    }

    public static MapLocation findNearestRuin(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();
            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < bestDist) {
                bestDist = dist;
                best = ruinLoc;
            }
        }
        return best;
    }

    public static void tryRuinActions(RobotController rc, MapLocation ruinTarget) throws GameActionException {
        if (ruinTarget == null || !rc.canSenseLocation(ruinTarget)) return;
        UnitType preferredTower = preferredTowerType(rc);

        Direction dirToRuin = rc.getLocation().directionTo(ruinTarget);
        MapLocation shouldBeMarked = ruinTarget.subtract(dirToRuin);

        if (rc.canSenseLocation(shouldBeMarked)
                && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                && rc.canMarkTowerPattern(preferredTower, ruinTarget)) {
            rc.markTowerPattern(preferredTower, ruinTarget);
        } else if (rc.canSenseLocation(shouldBeMarked)
                && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget)) {
            // Fallback when preferred pattern is not legal here.
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget);
        }

        for (MapInfo patternTile : rc.senseNearbyMapInfos(ruinTarget, 8)) {
            if (patternTile.getMark() == PaintType.EMPTY) continue;
            if (patternTile.getMark() == patternTile.getPaint()) continue;
            boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(patternTile.getMapLocation())) {
                rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                return;
            }
        }

        if (rc.canCompleteTowerPattern(preferredTower, ruinTarget)) {
            rc.completeTowerPattern(preferredTower, ruinTarget);
            rc.setTimelineMarker("Tower built", 0, 255, 0);
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget);
            rc.setTimelineMarker("Tower built", 0, 255, 0);
        }
    }

    public static Direction chooseBestMoveSoldier(RobotController rc, GamePhase phase, MapLocation ruinTarget, boolean shouldExplore, MapLocation exploreTarget) throws GameActionException {
        Direction bestDir = null;
        double bestScore = -1e18;

        for (Direction d : directions) {
            if (!rc.canMove(d)) continue;
            MapLocation candidate = rc.getLocation().add(d);
            double score = scoreSoldierTile(rc, candidate, phase, ruinTarget, shouldExplore, exploreTarget);
            score += rng.nextDouble() * 1e-3;
            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        double stayScore = scoreSoldierTile(rc, rc.getLocation(), phase, ruinTarget, shouldExplore, exploreTarget) - 0.25;
        if (bestDir == null || stayScore > bestScore) return null;
        return bestDir;
    }

    public static Direction chooseBestMoveMopper(RobotController rc, GamePhase phase, boolean shouldExplore, MapLocation exploreTarget) throws GameActionException {
        Direction bestDir = null;
        double bestScore = -1e18;

        for (Direction d : directions) {
            if (!rc.canMove(d)) continue;
            MapLocation candidate = rc.getLocation().add(d);
            double score = scoreMopperTile(rc, candidate, phase, shouldExplore, exploreTarget);
            score += rng.nextDouble() * 1e-3;
            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        double stayScore = scoreMopperTile(rc, rc.getLocation(), phase, shouldExplore, exploreTarget) - 0.25;
        if (bestDir == null || stayScore > bestScore) return null;
        return bestDir;
    }

    public static double scoreSoldierTile(RobotController rc, MapLocation tile, GamePhase phase, MapLocation ruinTarget, boolean shouldExplore, MapLocation exploreTarget) throws GameActionException {
        double ruinWeight = (phase == GamePhase.EARLY) ? 5.0 : (phase == GamePhase.MID ? 2.0 : 0.5);
        double enemyTerritoryWeight = (phase == GamePhase.LATE) ? 4.0 : (phase == GamePhase.MID ? 1.5 : 0.5);
        double paintNeedWeight = (phase == GamePhase.EARLY) ? 2.5 : (phase == GamePhase.MID ? 2.0 : 1.5);
        double enemyRobotWeight = (phase == GamePhase.EARLY) ? 1.0 : (phase == GamePhase.MID ? 2.0 : 3.0);
        double allySupportWeight = 1.0;
        double riskWeight = (phase == GamePhase.EARLY) ? 3.0 : (phase == GamePhase.MID ? 3.5 : 4.0);
        double exploreWeight = (phase == GamePhase.EARLY) ? 1.5 : (phase == GamePhase.MID ? 3.5 : 2.0);

        double score = 0.0;

        if (ruinTarget != null) {
            score += ruinWeight * inverseDistanceScore(tile.distanceSquaredTo(ruinTarget));
        }
        if (shouldExplore && exploreTarget != null) {
            score += exploreWeight * inverseDistanceScore(tile.distanceSquaredTo(exploreTarget));
        }
        score += enemyTerritoryWeight * enemyTerritorySignal(rc, tile);
        score += paintNeedWeight * paintNeedSignal(rc, tile);
        score += enemyRobotWeight * nearbyEnemySignal(rc, tile);
        score += allySupportWeight * nearbyAllySignal(rc, tile);
        score -= riskWeight * riskSignal(rc, tile);
        score -= oscillationPenalty(tile);
        return score;
    }

    public static double scoreMopperTile(RobotController rc, MapLocation tile, GamePhase phase, boolean shouldExplore, MapLocation exploreTarget) throws GameActionException {
        double enemyPaintWeight = (phase == GamePhase.EARLY) ? 3.0 : (phase == GamePhase.MID ? 4.0 : 4.5);
        double enemyRobotWeight = (phase == GamePhase.EARLY) ? 2.0 : (phase == GamePhase.MID ? 2.5 : 3.0);
        double allySupportWeight = 1.2;
        double riskWeight = (phase == GamePhase.EARLY) ? 2.5 : (phase == GamePhase.MID ? 3.0 : 3.5);
        double exploreWeight = (phase == GamePhase.EARLY) ? 1.2 : (phase == GamePhase.MID ? 3.0 : 1.5);

        double score = 0.0;
        if (shouldExplore && exploreTarget != null) {
            score += exploreWeight * inverseDistanceScore(tile.distanceSquaredTo(exploreTarget));
        }
        score += enemyPaintWeight * enemyTerritorySignal(rc, tile);
        score += enemyRobotWeight * nearbyEnemySignal(rc, tile);
        score += allySupportWeight * nearbyAllySignal(rc, tile);
        score -= riskWeight * riskSignal(rc, tile);
        score -= oscillationPenalty(tile);
        return score;
    }

    public static double inverseDistanceScore(int distSquared) {
        return 1.0 / (distSquared + 1.0);
    }

    public static double enemyTerritorySignal(RobotController rc, MapLocation tile) throws GameActionException {
        double score = 0.0;
        if (rc.canSenseLocation(tile)) {
            PaintType p = rc.senseMapInfo(tile).getPaint();
            if (p.isEnemy()) score += 1.0;
            else if (p == PaintType.EMPTY) score += 0.3;
        }

        for (MapInfo info : rc.senseNearbyMapInfos(tile, 2)) {
            if (info.getPaint().isEnemy()) score += 0.25;
        }
        return score;
    }

    public static double paintNeedSignal(RobotController rc, MapLocation tile) throws GameActionException {
        if (!rc.canSenseLocation(tile)) return 0.0;
        PaintType p = rc.senseMapInfo(tile).getPaint();
        if (p.isEnemy()) return 1.0;
        if (p == PaintType.EMPTY) return 0.5;
        return -0.2;
    }

    public static double nearbyEnemySignal(RobotController rc, MapLocation tile) throws GameActionException {
        double score = 0.0;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            score += inverseDistanceScore(tile.distanceSquaredTo(enemy.getLocation()));
        }
        return score;
    }

    public static double nearbyAllySignal(RobotController rc, MapLocation tile) throws GameActionException {
        double score = 0.0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getLocation().equals(rc.getLocation())) continue;
            score += 0.5 * inverseDistanceScore(tile.distanceSquaredTo(ally.getLocation()));
        }
        return score;
    }

    public static double riskSignal(RobotController rc, MapLocation tile) throws GameActionException {
        double risk = 0.0;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            int dist = tile.distanceSquaredTo(enemy.getLocation());
            if (dist <= enemy.getType().actionRadiusSquared) risk += 1.0;
            else risk += 0.25 * inverseDistanceScore(dist);
        }
        return risk;
    }

    public static double oscillationPenalty(MapLocation tile) {
        double penalty = 0.0;
        if (lastLocation != null && tile.equals(lastLocation)) penalty += 0.4;
        if (lastLastLocation != null && tile.equals(lastLastLocation)) penalty += 0.6;
        return penalty;
    }

    public static MapLocation chooseBestPaintTarget(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation best = null;
        double bestScore = -1e18;

        for (MapInfo info : nearby) {
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            double score = 0.0;
            PaintType paint = info.getPaint();
            if (paint.isEnemy()) score += 3.0;
            else if (paint == PaintType.EMPTY) score += 1.5;
            else score -= 1.0;

            if (loc.equals(rc.getLocation())) score += 0.4;

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    public static void tryGreedyPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation target = chooseBestPaintTarget(rc);
        if (target != null && rc.canAttack(target)) {
            rc.attack(target);
        }
    }

    public static Direction bestMopDirection(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;

        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (RobotInfo enemy : enemies) {
            Direction d = myLoc.directionTo(enemy.getLocation());
            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    public static boolean tryBuildAnyDirection(RobotController rc, UnitType unit) throws GameActionException {
        for (Direction d : directions) {
            MapLocation spawnLoc = rc.getLocation().add(d);
            if (rc.canBuildRobot(unit, spawnLoc)) {
                rc.buildRobot(unit, spawnLoc);
                return true;
            }
        }
        return false;
    }

    public static UnitType preferredTowerType(RobotController rc) {
        GamePhase phase = getPhase(rc);
        if (phase == GamePhase.EARLY || phase == GamePhase.MID) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    public static boolean shouldExplore(RobotController rc, GamePhase phase, MapLocation ruinTarget) throws GameActionException {
        if (phase == GamePhase.MID || phase == GamePhase.LATE) return true;
        if (ruinTarget == null) return true;
        return localCoverageHigh(rc);
    }

    public static boolean localCoverageHigh(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        if (nearby.length == 0) return false;

        int allyOrMarked = 0;
        int ruins = 0;
        for (MapInfo info : nearby) {
            if (info.hasRuin()) ruins++;
            PaintType p = info.getPaint();
            if (p.isAlly() || p != PaintType.EMPTY) allyOrMarked++;
        }
        return ruins == 0 || allyOrMarked * 100 / nearby.length >= 70;
    }

    public static MapLocation getOrRefreshExplorationTarget(RobotController rc, GamePhase phase, boolean shouldExplore) {
        if (!shouldExplore) return null;

        if (explorationTarget != null && rc.getLocation().distanceSquaredTo(explorationTarget) <= 4) {
            explorationTarget = null;
        }

        int refreshWindow = (phase == GamePhase.EARLY) ? 70 : 50;
        if (explorationTarget == null || rc.getRoundNum() - explorationTargetRefreshRound >= refreshWindow) {
            explorationTarget = pickExplorationTarget(rc);
            explorationTargetRefreshRound = rc.getRoundNum();
        }
        return explorationTarget;
    }

    public static MapLocation pickExplorationTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        MapLocation[] candidates = new MapLocation[]{
            new MapLocation(1, 1),
            new MapLocation(w - 2, 1),
            new MapLocation(1, h - 2),
            new MapLocation(w - 2, h - 2),
            new MapLocation(w / 2, h / 2)
        };

        int idx = Math.abs(rc.getID() + rc.getRoundNum() / 40) % candidates.length;
        return candidates[idx];
    }
}
