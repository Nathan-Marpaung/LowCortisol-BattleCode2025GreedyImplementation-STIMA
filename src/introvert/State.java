package introvert;

import battlecode.common.*;
import java.util.ArrayDeque;
import java.util.Random;

public class State {

    // Data utama robot
    public static RobotController rc;
    public static MapLocation myLoc;
    public static int turnCount = 0;
    public static int botRoundNum = 0;

    // Riwayat posisi terakhir
    public static ArrayDeque<MapLocation> last16 = new ArrayDeque<>();

    // Paint tower terdekat yang pernah terlihat
    public static MapInfo lastTower = null;
    public static boolean seenPaintTower = false;

    // State pathfinding
    public static boolean isTracing = false;
    public static Direction tracingDir = null;
    public static MapLocation stoppedLocation = null;
    public static int tracingTurns = 0;
    public static int bug1Turns = 0;
    public static int smallestDist = 10_000_000;
    public static MapLocation closestLoc = null;
    public static int stuckTurnCount = 0;
    public static int closestPath = -1;
    public static boolean inBugNav = false;
    public static MapLocation acrossWall = null;
    public static MapLocation oppositeCorner = null;

    // State soldier
    public enum SoldierState {
        EXPLORE, FILLING_RUIN, LOW_PAINT, LATE_EXPLORE
    }

    public static SoldierState soldierState = SoldierState.EXPLORE;
    public static SoldierState storedState = SoldierState.EXPLORE;

    public static MapLocation ruinToFill = null;
    public static UnitType fillTowerType = null;
    public static MapLocation intermediateTarget = null;
    public static MapLocation prevIntermediate = null;
    public static MapLocation wanderTarget = null;
    public static MapLocation lateExploreTarget = null;

    // State mopper dan splasher
    public static MapInfo removePaint = null;
    public static boolean isLowPaint = false;
    public static MapInfo prevLocInfo = null;

    public static final Random rng = new Random();

    // General const
    public static final int LOW_PAINT_THRESHOLD = 30;
    public static final int REFUEL_THRESHOLD_SOLDIER = 50;
    public static final int REFUEL_THRESHOLD_SPLASHER = 80;
    public static final int LOW_PAINT_MONEY_THRESHOLD = 5000;
    public static final int MIN_PAINT_GIVE = 50;
    public static final double RANDOM_STEP_PROBABILITY = 0.25;

    // Bobot paint soldier
    public static final int TILE_EMPTY_BASIC = 2;
    public static final int TILE_NEAR_RUIN = 5;
    public static final int TILE_SRP_BONUS = 3;
    public static final int TILE_MISMATCH_FIX = 1;

    // Bobot pilih ruin
    public static final int RUIN_DIST_WEIGHT = 1;
    public static final int RUIN_TILES_DONE_BONUS = 200;
    public static final int RUIN_CENTER_BONUS = 150;
    public static final int RUIN_ALLY_NEARBY_PENALTY = 300;

    // Bobot mopper
    public static final int MOP_ENEMY_BOT = 10;
    public static final int MOP_NEAR_RUIN = 5;
    public static final int MOP_BASIC = 3;
    public static final int MOP_TOWER_PENALTY = -5;

    // Bobot splasher
    public static final int SPLASH_NEAR_ENEMY_TOWER = 10;
    public static final int SPLASH_ENEMY_TO_ALLY = 3;
    public static final int SPLASH_FRIENDLY_FIRE = -5;
    public static final int SPLASH_EMPTY_FILL = 2;

    public static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    // Offset target explore
    public static final int[][] EXPLORE_OFFSETS = {
        { -6, -6 }, { -6, 0 }, { -6, 6 }, { 0, -6 },
        { 0, 6 }, { 6, -6 }, { 6, 0 }, { 6, 6 }
    };

    public static void init(RobotController controller) {
        rc = controller;
        myLoc = rc.getLocation();
        turnCount++;
        botRoundNum++;
    }

    // Reset state pathfinding
    public static void resetPathfinding() {
        isTracing = false;
        tracingDir = null;
        stoppedLocation = null;
        tracingTurns = 0;
        bug1Turns = 0;
        smallestDist = 10_000_000;
        closestLoc = null;
        stuckTurnCount = 0;
        closestPath = -1;
        inBugNav = false;
        acrossWall = null;
        fillTowerType = null;
    }

    // Simpan posisi terbaru
    public static void updateLast16() {
        if (last16.size() >= 16)
            last16.pollFirst();
        last16.addLast(myLoc);
    }
}
