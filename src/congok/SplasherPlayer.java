package congok;

import battlecode.common.*;

public class SplasherPlayer {

    static final int refuel_paint=80;
    static final int low_paint=30;
    private static MapLocation expand_target=null;
    private static int target_age=0;
    static final int target_ttl=12;

    static void run(RobotController rc) throws GameActionException {
        MapLocation pos=rc.getLocation();
        int paint=rc.getPaint();
        target_age++;

        rc.setIndicatorString("Splasher | paint="+paint+" target="+expand_target);

        if (paint<refuel_paint) {
            MapLocation tower=findNearestTower(rc);
            if (tower!=null) {
                if (paint>low_paint && rc.isActionReady()) {
                    MapLocation splash=findBestSplash(rc);
                    if (splash!=null) rc.attack(splash);
                }
                Nav.moveToward(rc,tower);
                return;
            }
        }

        if (paint>low_paint && rc.isActionReady()) {
            MapLocation splash=findBestSplash(rc);
            if (splash!=null) rc.attack(splash);
        }

        if (rc.isMovementReady()) {
            refreshTarget(rc,pos);

            if (expand_target!=null) {
                if (pos.distanceSquaredTo(expand_target)<=4 || areaLooksDone(rc,expand_target)) {
                    expand_target=null;
                    target_age=target_ttl;
                    Nav.moveForExpand(rc,RobotPlayer.rng);
                } else {
                    Nav.moveToward(rc,expand_target);
                }
            } else {
                Nav.moveForExpand(rc,RobotPlayer.rng);
            }
        }
    }

    private static MapLocation findBestSplash(RobotController rc)
            throws GameActionException {

        int atk_rad=rc.getType().actionRadiusSquared;
        MapInfo[] attackable=rc.senseNearbyMapInfos(atk_rad);
        Team enemy=rc.getTeam().opponent();
        RobotInfo[] enemies=rc.senseNearbyRobots(-1,enemy);

        MapLocation best=null;
        int best_score=0;

        for (MapInfo tile : attackable) {
            if (!rc.canAttack(tile.getMapLocation())) continue;
            MapLocation center=tile.getMapLocation();

            int score=0;
            MapInfo[] splash_area=rc.senseNearbyMapInfos(center,2);
            for (MapInfo t : splash_area) {
                if (!t.isPassable()) continue;
                PaintType p=t.getPaint();
                if (p==PaintType.EMPTY) score+=4;
                else if (p.isEnemy()) score+=3;
            }

            for (RobotInfo enemy_bot : enemies) {
                if (center.distanceSquaredTo(enemy_bot.getLocation())<=8) {
                    score-=50;
                    break;
                }
            }

            if (score>best_score) {
                best_score=score;
                best=center;
            }
        }
        return best;
    }

    private static void refreshTarget(RobotController rc, MapLocation pos)
            throws GameActionException {
        if (expand_target!=null && target_age<target_ttl) return;
        target_age=0;

        MapInfo[] visible=rc.senseNearbyMapInfos(-1);
        int[] dir_score=new int[8];

        for (MapInfo tile : visible) {
            if (!tile.isPassable()) continue;
            PaintType paint=tile.getPaint();
            if (paint.isAlly()) continue;

            Direction d=pos.directionTo(tile.getMapLocation());
            int idx=Nav.dirIndex(d);
            if (idx<0) continue;
            dir_score[idx]+=(paint==PaintType.EMPTY)?3:1;
        }

        int best_idx=-1;
        int best_val=0;
        for (int i=0; i<8; i++) {
            if (dir_score[i]>best_val) {
                best_val=dir_score[i];
                best_idx=i;
            }
        }

        if (best_idx>=0 && best_val>0) {
            Direction best_dir=Nav.dirs[best_idx];
            MapLocation target=pos;
            for (int i=0; i<6; i++) {
                MapLocation next=target.add(best_dir);
                if (rc.onTheMap(next)) target=next;
                else break;
            }
            expand_target=target.equals(pos)?null:target;
        } else {
            expand_target=null;
        }
    }

    private static boolean areaLooksDone(RobotController rc, MapLocation loc)
            throws GameActionException {
        if (!rc.canSenseLocation(loc)) return false;
        MapInfo[] area=rc.senseNearbyMapInfos(loc,4);
        int total=0;
        int ally=0;
        for (MapInfo tile : area) {
            if (!tile.isPassable()) continue;
            total++;
            if (tile.getPaint().isAlly()) ally++;
        }
        return total>0 && ally*100/total>=70;
    }

    private static MapLocation findNearestTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation pos=rc.getLocation();
        MapLocation nearest=null;
        int best_dist=Integer.MAX_VALUE;
        for (RobotInfo ally_bot : allies) {
            if (ally_bot.getType().isTowerType()) {
                int dist=pos.distanceSquaredTo(ally_bot.getLocation());
                if (dist<best_dist) {
                    best_dist=dist;
                    nearest=ally_bot.getLocation();
                }
            }
        }
        return nearest;
    }
}
