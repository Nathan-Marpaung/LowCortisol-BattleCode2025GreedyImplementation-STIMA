package congok;

import battlecode.common.*;

public class Nav {

    static Direction lastMoveDir = Direction.CENTER;

    static final Direction[] dirs={
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return false;
        MapLocation pos=rc.getLocation();
        if (pos.equals(target)) return false;

        Direction best=pos.directionTo(target);
        Direction[] tries={
            best,
            best.rotateLeft(),
            best.rotateRight(),
            best.rotateLeft().rotateLeft(),
            best.rotateRight().rotateRight(),
            best.rotateLeft().rotateLeft().rotateLeft(),
            best.rotateRight().rotateRight().rotateRight(),
        };
        for (Direction d : tries) {
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    static boolean moveAway(RobotController rc, MapLocation threat) throws GameActionException {
        if (!rc.isMovementReady()) return false;
        MapLocation pos=rc.getLocation();
        Direction away=threat.directionTo(pos);
        Direction[] tries={
            away,
            away.rotateLeft(),
            away.rotateRight(),
            away.rotateLeft().rotateLeft(),
            away.rotateRight().rotateRight(),
        };
        for (Direction d : tries) {
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    static boolean moveForExpand(RobotController rc, java.util.Random rng) throws GameActionException {
        if (!rc.isMovementReady()) return false;
        MapLocation pos=rc.getLocation();
        Team enemy=rc.getTeam().opponent();

        MapInfo[] tiles=rc.senseNearbyMapInfos(-1);
        RobotInfo[] enemies=rc.senseNearbyRobots(-1,enemy);
        int[] dir_score=new int[8];

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint=tile.getPaint();
            if (paint.isAlly()) continue;

            Direction d=pos.directionTo(tile.getMapLocation());
            int idx=dirIndex(d);
            if (idx<0) continue;
            dir_score[idx]+=(paint==PaintType.EMPTY)?3:1;
        }

        for (RobotInfo foe : enemies) {
            Direction d=pos.directionTo(foe.getLocation());
            int idx=dirIndex(d);
            if (idx>=0) dir_score[idx]-=20;
        }

        int best_idx=-1;
        int best_val=0;
        for (int i=0; i<8; i++) {
            if (dir_score[i]>best_val && rc.canMove(dirs[i])) {
                best_val=dir_score[i];
                best_idx=i;
            }
        }
        if (best_idx>=0) {
            rc.move(dirs[best_idx]);
            return true;
        }
        return moveRandomly(rc,rng);
    }

    static boolean moveRandomly(RobotController rc, java.util.Random rng) throws GameActionException {
        if (!rc.isMovementReady()) return false;

        Direction reverse=lastMoveDir.opposite();

        Direction[] valid=new Direction[8];
        int count=0;
        for (Direction d : dirs) {
            if (rc.canMove(d) && d != reverse) {
                valid[count++]=d;
            }
        }

        if (count==0) {
            for (Direction d : dirs) {
                if (rc.canMove(d)){
                    valid[count++]=d;
                }
            }
        }

        if (count==0) return false;

        Direction chosen=valid[rng.nextInt(count)];
        rc.move(chosen);
        lastMoveDir=chosen;
        return true;
    }

    static int dirIndex(Direction d) {
        switch (d) {
            case NORTH: return 0;
            case NORTHEAST: return 1;
            case EAST: return 2;
            case SOUTHEAST: return 3;
            case SOUTH: return 4;
            case SOUTHWEST: return 5;
            case WEST: return 6;
            case NORTHWEST: return 7;
            default: return -1;
        }
    }
}
