package alternative_bots_1;

import battlecode.common.*;

public class Pathfind {

    // Pathfinding utama
    public static Direction pathfind(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation cur = rc.getLocation();
        int dist = cur.distanceSquaredTo(target);

        if (dist == 0) {
            State.resetPathfinding();
            return null;
        }

        if (State.stuckTurnCount < 5 && !State.inBugNav) {
            if (dist < State.closestPath || State.closestPath == -1) {
                State.closestPath = dist;
            } else {
                State.stuckTurnCount++;
            }
            return lessOriginalPathfind(rc, target);
        } else if (State.inBugNav) {
            MapLocation navTarget = State.acrossWall != null ? State.acrossWall : target;
            if (cur.distanceSquaredTo(navTarget) == 0) {
                State.resetPathfinding();
                return null;
            }
            return bug1(rc, navTarget);
        } else {
            State.inBugNav = true;
            State.stuckTurnCount = 0;
            State.acrossWall = target;
            return bug1(rc, target);
        }
    }

    // Pilih langkah terdekat
    public static Direction lessOriginalPathfind(RobotController rc, MapLocation target) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        PaintType bestPaint = PaintType.EMPTY;
        MapLocation cur = rc.getLocation();
        MapInfo bestLoc = null;

        for (Direction dir : State.DIRECTIONS) {
            if (!rc.canMove(dir)) continue;
            MapInfo adj = rc.senseMapInfo(cur.add(dir));
            int d = adj.getMapLocation().distanceSquaredTo(target);
            PaintType p = adj.getPaint();
            if (d < minDist) {
                minDist = d;
                bestPaint = p;
                bestLoc = adj;
            } else if (d == minDist) {
                if (bestPaint.isEnemy() && !p.isEnemy() || bestPaint == PaintType.EMPTY && p.isAlly()) {
                    bestPaint = p;
                    bestLoc = adj;
                }
            }
        }
        return (bestLoc != null) ? cur.directionTo(bestLoc.getMapLocation()) : null;
    }

    // Prioritaskan tile ally paint
    public static Direction pathfindPainted(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.getPaint() < 6) {
            Direction d = rc.getLocation().directionTo(target);
            Direction l = d.rotateLeft(), r = d.rotateRight();
            if (rc.canMove(d)) return d;
            if (rc.canMove(l) && rc.senseMapInfo(rc.getLocation().add(l)).getPaint().isAlly()) return l;
            if (rc.canMove(r) && rc.senseMapInfo(rc.getLocation().add(r)).getPaint().isAlly()) return r;
        }
        return pathfind(rc, target);
    }

    // Ikuti dinding saat macet
    public static Direction bug1(RobotController rc, MapLocation target) throws GameActionException {
        if (!State.isTracing) {
            Direction dir = rc.getLocation().directionTo(target);
            if (rc.canMove(dir)) return dir;
            State.isTracing = true;
            State.tracingDir = dir;
            State.bug1Turns = 0;
        } else {
            if ((rc.getLocation().equals(State.closestLoc) && State.bug1Turns != 0)
                    || State.bug1Turns > 2 * (rc.getMapWidth() + rc.getMapHeight())) {
                State.resetPathfinding();
            } else {
                int dist = rc.getLocation().distanceSquaredTo(target);
                if (dist < State.smallestDist) {
                    State.smallestDist = dist;
                    State.closestLoc = rc.getLocation();
                }
                if (rc.canMove(State.tracingDir)) {
                    Direction ret = State.tracingDir;
                    State.tracingDir = State.tracingDir.rotateRight().rotateRight();
                    State.bug1Turns++;
                    return ret;
                } else {
                    for (int i = 0; i < 8; i++) {
                        State.tracingDir = State.tracingDir.rotateLeft();
                        if (rc.canMove(State.tracingDir)) {
                            Direction ret = State.tracingDir;
                            State.tracingDir = State.tracingDir.rotateRight().rotateRight();
                            State.bug1Turns++;
                            return ret;
                        }
                    }
                }
            }
        }
        return null;
    }

    // downperform BFS (Masih belum terpakai karena di assign ke splasher)
    public static Direction bfsPathfind(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation cur = rc.getLocation();
        int curDist = cur.distanceSquaredTo(target);
        if (curDist == 0) {
            State.resetPathfinding();
            return null;
        }
        MapLocation[] queue = new MapLocation[120];
        int head = 0, tail = 0;
        int offX = cur.x - 6;
        int offY = cur.y - 6;
        Direction[][] firstMove = new Direction[13][13];
        boolean[][] visited = new boolean[13][13];
        visited[6][6] = true;
        for (Direction d : State.DIRECTIONS) {
            if (!rc.canMove(d)) continue;
            MapLocation next = cur.add(d);
            int nx = next.x - offX;
            int ny = next.y - offY;
            if (nx >= 0 && nx < 13 && ny >= 0 && ny < 13) {
                visited[nx][ny] = true;
                firstMove[nx][ny] = d;
                queue[tail++] = next;
                if (next.equals(target)) {
                    State.resetPathfinding();
                    return d;
                }
            }
        }

        Direction bestDir = null;
        int closestDist = curDist;
        while (head < tail) {
            MapLocation currNode = queue[head++];
            int cnx = currNode.x - offX;
            int cny = currNode.y - offY;
            Direction initialMove = firstMove[cnx][cny];
            int dist = currNode.distanceSquaredTo(target);
            if (dist < closestDist) {
                closestDist = dist;
                bestDir = initialMove;
            }
            if (currNode.equals(target)) {
                State.resetPathfinding();
                return initialMove;
            }
            for (Direction d : State.DIRECTIONS) {
                MapLocation next = currNode.add(d);
                int nx = next.x - offX;
                int ny = next.y - offY;
                if (nx >= 0 && nx < 13 && ny >= 0 && ny < 13 && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    if (rc.canSenseLocation(next)) {
                        MapInfo info = rc.senseMapInfo(next);
                        if (info.isPassable()) {
                            boolean occupiedByAlly = false;
                            if (rc.canSenseRobotAtLocation(next)) {
                                RobotInfo bot = rc.senseRobotAtLocation(next);
                                if (bot.getTeam() == rc.getTeam()) occupiedByAlly = true;
                            }

                            if (!occupiedByAlly) {
                                firstMove[nx][ny] = initialMove;
                                queue[tail++] = next;
                            }
                        }
                    }
                }
            }

            if (head > 45) break;
        }
        if (bestDir != null) {
            State.resetPathfinding();
            return bestDir;
        }
        return pathfind(rc, target);
    }
    public static Direction randomWalk(RobotController rc) throws GameActionException {
        for (int i = 0; i < 6; i++) {
            Direction d = State.DIRECTIONS[State.rng.nextInt(8)];
            if (rc.canMove(d) && !State.last16.contains(rc.getLocation().add(d))) return d;
        }
        for (Direction d : State.DIRECTIONS) {
            if (rc.canMove(d)) return d;
        }
        return null;
    }
    public static Direction randomPaintedWalk(RobotController rc) throws GameActionException {
        for (int i = 0; i < 6; i++) {
            Direction d = State.DIRECTIONS[State.rng.nextInt(8)];
            if (rc.canMove(d)) {
                MapInfo adj = rc.senseMapInfo(rc.getLocation().add(d));
                if (adj.getPaint().isAlly() && !State.last16.contains(adj.getMapLocation())) return d;
            }
        }
        return randomWalk(rc);
    }
}
