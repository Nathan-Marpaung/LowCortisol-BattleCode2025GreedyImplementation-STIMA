package congok;

import battlecode.common.*;

public class SoldierPlayer {

    static final int refuel_paint=50;
    static final int low_paint=20;
    private static MapLocation assigned_ruins=null;

    static void run(RobotController rc) throws GameActionException {
        MapLocation pos=rc.getLocation();
        int paint=rc.getPaint();

        rc.setIndicatorString("Soldier | ruins="+assigned_ruins+" paint="+paint);

        if (paint<refuel_paint) {
            MapLocation tower=findNearbyTower(rc);
            if (tower!=null) {
                Nav.moveToward(rc,tower);
                return;
            }
        }

        if (assigned_ruins!=null) {
            UnitType tower_type=Comms.getNextTowerType(rc);
            if (rc.canCompleteTowerPattern(tower_type,assigned_ruins)) {
                rc.completeTowerPattern(tower_type,assigned_ruins);
                Comms.onTowerDone(rc);
                Comms.releaseRuin(rc,assigned_ruins);
                assigned_ruins=null;
                return;
            }
        }

        if (assigned_ruins==null) {
            assigned_ruins=findBestSafeRuins(rc);
        }

        if (assigned_ruins!=null) {
            workRuins(rc,assigned_ruins,pos,paint);
            return;
        }

        if (paint>low_paint && rc.isActionReady()) {
            MapLocation target=findBestPaintTarget(rc);
            if (target!=null) {
                tryPaint(rc,target);
            }
        }

        if (rc.isMovementReady()) {
            Nav.moveForExpand(rc,RobotPlayer.rng);
        }
    }

    private static void workRuins(RobotController rc, MapLocation ruins,
                                  MapLocation pos, int paint)
            throws GameActionException {

        UnitType tower_type=Comms.getNextTowerType(rc);

        if (pos.distanceSquaredTo(ruins)<=8 && rc.canMarkTowerPattern(tower_type,ruins)) {
            rc.markTowerPattern(tower_type,ruins);
        }

        if (paint>low_paint && rc.isActionReady()) {
            paintOnePatternTile(rc,ruins);
        }

        if (rc.canCompleteTowerPattern(tower_type,ruins)) {
            rc.completeTowerPattern(tower_type,ruins);
            Comms.onTowerDone(rc);
            Comms.releaseRuin(rc,ruins);
            assigned_ruins=null;
            return;
        }

        if (rc.isMovementReady()) {
            MapLocation tile=findUnpaintedPatternTile(rc,ruins);
            if (tile!=null) {
                Nav.moveToward(rc,tile);
            } else {
                Nav.moveToward(rc,ruins);
            }
        }
    }

    private static void paintOnePatternTile(RobotController rc, MapLocation ruins)
            throws GameActionException {
        for (int dx=-2; dx<=2; dx++) {
            for (int dy=-2; dy<=2; dy++) {
                MapLocation loc=ruins.translate(dx,dy);
                if (!rc.canAttack(loc)) continue;
                if (!rc.canSenseLocation(loc)) continue;
                MapInfo info=rc.senseMapInfo(loc);
                if (!info.isPassable()) continue;

                PaintType mark=info.getMark();
                PaintType paint=info.getPaint();

                if (mark==PaintType.EMPTY) continue;
                if (paintMatchesMark(paint,mark)) continue;

                boolean secondary=(mark==PaintType.ALLY_SECONDARY);
                rc.attack(loc,secondary);
                return;
            }
        }
    }

    private static MapLocation findUnpaintedPatternTile(RobotController rc, MapLocation ruins)
            throws GameActionException {
        for (int dx=-2; dx<=2; dx++) {
            for (int dy=-2; dy<=2; dy++) {
                MapLocation loc=ruins.translate(dx,dy);
                if (!rc.canSenseLocation(loc)) continue;
                MapInfo info=rc.senseMapInfo(loc);
                if (!info.isPassable()) continue;

                PaintType mark=info.getMark();
                PaintType paint=info.getPaint();

                if (mark==PaintType.EMPTY) continue;
                if (!paintMatchesMark(paint,mark)) return loc;
            }
        }
        return null;
    }

    private static boolean paintMatchesMark(PaintType paint, PaintType mark) {
        return mark==PaintType.ALLY_PRIMARY && paint==PaintType.ALLY_PRIMARY
            || mark==PaintType.ALLY_SECONDARY && paint==PaintType.ALLY_SECONDARY;
    }

    private static MapLocation findBestSafeRuins(RobotController rc) throws GameActionException {
        MapLocation pos=rc.getLocation();
        Team enemy=rc.getTeam().opponent();
        MapInfo[] tiles=rc.senseNearbyMapInfos(-1);

        MapLocation best=null;
        int best_dist=Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruins=tile.getMapLocation();

            if (Comms.isRuinClaimed(rc,ruins)) continue;

            RobotInfo[] ally_on_ruins=rc.senseNearbyRobots(ruins,0,rc.getTeam());
            if (ally_on_ruins.length>0) continue;

            RobotInfo[] enemy_near=rc.senseNearbyRobots(ruins,16,enemy);
            if (enemy_near.length>0) continue;

            int dist=pos.distanceSquaredTo(ruins);
            if (dist<best_dist) {
                best_dist=dist;
                best=ruins;
            }
        }

        if (best!=null) {
            Comms.claimRuin(rc,best);
        }
        return best;
    }

    private static MapLocation findBestPaintTarget(RobotController rc)
            throws GameActionException {

        int atk_rad=rc.getType().actionRadiusSquared;
        MapInfo[] attackable=rc.senseNearbyMapInfos(atk_rad);
        Team enemy=rc.getTeam().opponent();
        RobotInfo[] enemies=rc.senseNearbyRobots(-1,enemy);

        MapLocation best=null;
        int best_score=Integer.MIN_VALUE;

        for (MapInfo tile : attackable) {
            if (!tile.isPassable()) continue;
            if (!rc.canAttack(tile.getMapLocation())) continue;

            PaintType paint=tile.getPaint();
            if (paint.isAlly()) continue;

            MapLocation loc=tile.getMapLocation();
            int score=0;

            if (paint==PaintType.EMPTY) score+=10;
            else score+=4;

            MapInfo[] around=rc.senseNearbyMapInfos(loc,2);
            for (MapInfo t : around) {
                if (t.getPaint().isAlly()) score+=2;
            }

            for (RobotInfo foe : enemies) {
                if (loc.distanceSquaredTo(foe.getLocation())<=9) {
                    score-=100;
                    break;
                }
            }

            if (score>best_score) {
                best_score=score;
                best=loc;
            }
        }
        return best;
    }

    private static void tryPaint(RobotController rc, MapLocation loc)
            throws GameActionException {
        if (!rc.canAttack(loc)) return;
        MapInfo info=rc.senseMapInfo(loc);
        boolean secondary=(info.getMark()==PaintType.ALLY_SECONDARY);
        rc.attack(loc,secondary);
    }

    private static MapLocation findNearbyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies=rc.senseNearbyRobots(-1,rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                return ally.getLocation();
            }
        }
        return null;
    }
}
