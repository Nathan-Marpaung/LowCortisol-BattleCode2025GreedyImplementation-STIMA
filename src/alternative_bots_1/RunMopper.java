package alternative_bots_1;

import battlecode.common.*;

public class RunMopper {

    public static void run() throws GameActionException {
        RobotController rc = State.rc;
        MapLocation myLoc = State.myLoc;

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

        // Kembali ke ally paint
        if (!rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()) {
            Direction safeDir = mopperWalkToAllyPaint(rc);
            if (safeDir != null && rc.canMove(safeDir)) {
                rc.move(safeDir);
            }
        }

        // Serang best target 
        if (rc.getActionCooldownTurns() < 10) {
            MapLocation mopTarget = findBestMopTarget(rc);
            if (mopTarget != null && rc.canAttack(mopTarget)) {
                rc.attack(mopTarget);
                State.removePaint = null;
            }
        }

        // Ayun mop waktu ramai
        tryMopSwing(rc);

        // Dekati target aman
        MapLocation moveTarget = findBestMoveTarget(rc, myLoc);
        if (moveTarget != null && rc.isMovementReady()) {
            if (isNearAllyPaint(rc, moveTarget)) {
                Direction dir = Pathfind.pathfind(rc, moveTarget);
                if (dir != null && rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
        }

        // Gerak ke pusat ally paint
        if (rc.isMovementReady()) {
            MapLocation allyCenter = findAllyPaintCenter(rc);
            if (allyCenter != null) {
                Direction dir = Pathfind.pathfind(rc, allyCenter);
                if (dir != null && rc.canMove(dir))
                    rc.move(dir);
            } else {
                Direction d = Pathfind.randomWalk(rc);
                if (d != null && rc.canMove(d))
                    rc.move(d);
            }
        }

        rc.setIndicatorString("MOPPER | target=" + State.removePaint);
    }

    // Pilih tile musuh terbaik
    private static MapLocation findBestMopTarget(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
            MapLocation loc = tile.getMapLocation();
            if (!tile.getPaint().isEnemy())
                continue;
            if (!rc.canAttack(loc))
                continue;

            int score = State.MOP_BASIC;

            RobotInfo bot = rc.senseRobotAtLocation(loc);
            if (bot != null && !bot.getTeam().equals(rc.getTeam())) {
                if (bot.getType().isRobotType() && bot.paintAmount > 0) {
                    score += State.MOP_ENEMY_BOT;
                }
            }

            for (MapInfo nearby : rc.senseNearbyMapInfos(loc, 8)) {
                if (nearby.hasRuin()) {
                    MapLocation ruinLoc = nearby.getMapLocation();
                    if (!rc.canSenseRobotAtLocation(ruinLoc)) {
                        score += State.MOP_NEAR_RUIN;
                        break;
                    } else {
                        RobotInfo ruinBot = rc.senseRobotAtLocation(ruinLoc);
                        if (ruinBot != null && ruinBot.getTeam().equals(rc.getTeam())) {
                            score += State.MOP_NEAR_RUIN;
                            break;
                        }
                    }
                }
            }

            for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
                if (enemy.getType().isTowerType() && loc.isWithinDistanceSquared(enemy.getLocation(), 9)) {
                    score += State.MOP_TOWER_PENALTY;
                    break;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    // Ayun mop ke arah plg bagus
    private static void tryMopSwing(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() > 10)
            return;

        int north = 0, east = 0, south = 0, west = 0;
        MapLocation loc = rc.getLocation();

        for (RobotInfo enemy : rc.senseNearbyRobots(2, rc.getTeam().opponent())) {
            Direction d = loc.directionTo(enemy.getLocation());
            switch (d) {
                case NORTH:
                    north++;
                    break;
                case SOUTH:
                    south++;
                    break;
                case EAST:
                    east++;
                    break;
                case WEST:
                    west++;
                    break;
                case NORTHEAST:
                    north++;
                    east++;
                    break;
                case NORTHWEST:
                    north++;
                    west++;
                    break;
                case SOUTHEAST:
                    south++;
                    east++;
                    break;
                case SOUTHWEST:
                    south++;
                    west++;
                    break;
                default:
                    break;
            }
        }

        int best = Math.max(Math.max(north, south), Math.max(east, west));
        if (best <= 1)
            return;

        if (north == best && rc.canMopSwing(Direction.NORTH)) {
            rc.mopSwing(Direction.NORTH);
            return;
        }
        if (south == best && rc.canMopSwing(Direction.SOUTH)) {
            rc.mopSwing(Direction.SOUTH);
            return;
        }
        if (east == best && rc.canMopSwing(Direction.EAST)) {
            rc.mopSwing(Direction.EAST);
            return;
        }
        if (west == best && rc.canMopSwing(Direction.WEST)) {
            rc.mopSwing(Direction.WEST);
        }
    }

    // Cari target gerak terdekat
    private static MapLocation findBestMoveTarget(RobotController rc, MapLocation myLoc)
            throws GameActionException {
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (!tile.getPaint().isEnemy())
                continue;
            int d = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = tile.getMapLocation();
            }
        }
        return nearest;
    }

    // Cek dekat ally paint
    private static boolean isNearAllyPaint(RobotController rc, MapLocation loc)
            throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(loc, 9)) {
            if (tile.getPaint().isAlly())
                return true;
        }
        return false;
    }

    // Ambil langkah ke ally paint
    private static Direction mopperWalkToAllyPaint(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();
        for (MapInfo tile : rc.senseNearbyMapInfos(2)) {
            MapLocation loc = tile.getMapLocation();
            if (tile.getPaint().isAlly() && !State.last16.contains(loc) && rc.canSenseLocation(loc)) {
                return cur.directionTo(loc);
            }
        }
        return null;
    }

    // Titik tengah ally paint
    private static MapLocation findAllyPaintCenter(RobotController rc) throws GameActionException {
        long sumX = 0, sumY = 0, count = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (tile.getPaint().isAlly()) {
                sumX += tile.getMapLocation().x;
                sumY += tile.getMapLocation().y;
                count++;
            }
        }
        if (count == 0)
            return null;
        return new MapLocation((int) (sumX / count), (int) (sumY / count));
    }

}
