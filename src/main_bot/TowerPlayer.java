package main_bot;

import battlecode.common.*;

public class TowerPlayer {

    private static final UnitType[] spawn_cycle={
        UnitType.SPLASHER,
        UnitType.SOLDIER,
        UnitType.SOLDIER,
        UnitType.SPLASHER,
        UnitType.SPLASHER,
        UnitType.SOLDIER,
        UnitType.SOLDIER,
        UnitType.SOLDIER,
        UnitType.SOLDIER
    };

    private static int spawn_idx=0;

    static void run(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        UnitType unit=spawn_cycle[spawn_idx%spawn_cycle.length];
        MapLocation spawn_loc=findBestSpawn(rc,unit);
        if (spawn_loc!=null) {
            rc.buildRobot(unit,spawn_loc);
            spawn_idx++;
        }

        rc.setIndicatorString("Tower | next: "+unit+" (step "+spawn_idx+")");
    }

    private static MapLocation findBestSpawn(RobotController rc, UnitType unit)
            throws GameActionException {

        MapLocation pos=rc.getLocation();
        Team enemy=rc.getTeam().opponent();
        RobotInfo[] enemies=rc.senseNearbyRobots(-1,enemy);

        MapLocation best_loc=null;
        int best_score=Integer.MIN_VALUE;

        for (Direction d : Nav.dirs) {
            MapLocation loc=pos.add(d);
            if (!rc.canBuildRobot(unit,loc)) continue;

            int score=0;

            if (rc.canSenseLocation(loc)) {
                MapInfo info=rc.senseMapInfo(loc);
                if (info.getPaint().isAlly()) score+=10;
                else if (info.getPaint()==PaintType.EMPTY) score+=5;
            }

            for (RobotInfo enemy_bot : enemies) {
                if (loc.isAdjacentTo(enemy_bot.getLocation())) {
                    score-=15;
                    break;
                }
            }

            if (score>best_score) {
                best_score=score;
                best_loc=loc;
            }
        }

        return best_loc;
    }
}
