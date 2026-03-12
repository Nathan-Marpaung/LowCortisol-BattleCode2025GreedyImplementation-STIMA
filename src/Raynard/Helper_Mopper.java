package Raynard;

import battlecode.common.*;

public class Helper_Mopper {

    public static boolean ada_ancaman(RobotInfo[] musuh){
        return musuh!=null&&musuh.length>0;
    }

    public static RobotInfo cari_unit_tempur_untuk_dikawal(MapLocation posisi,RobotInfo[] teman){
        if(teman==null){
            return null;
        }
        RobotInfo best=null;
        int bestScore=-10000;
        for(RobotInfo ally:teman){
            UnitType tipe=ally.getType();
            if(tipe!=UnitType.SOLDIER&&tipe!=UnitType.SPLASHER){
                continue;
            }
            int score=220-posisi.distanceSquaredTo(ally.getLocation())*3;
            score+=Math.max(0,120-ally.getPaintAmount())*2;
            if(score>bestScore){
                bestScore=score;
                best=ally;
            }
        }
        return best;
    }

    public static boolean bersihkan_prioritas(RobotController rc,MapLocation ruinHint,boolean prioritaskanRuin) throws GameActionException{
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        Direction bestSwing=null;
        int bestSwingScore=0;
        for(Direction dir:RobotPlayer.directions){
            if(!rc.canMopSwing(dir)){
                continue;
            }
            int score=score_mop_swing(posisi,infos,dir,ruinHint,prioritaskanRuin);
            if(score>bestSwingScore){
                bestSwingScore=score;
                bestSwing=dir;
            }
        }
        if(bestSwing!=null&&bestSwingScore>=18&&rc.canMopSwing(bestSwing)){
            rc.mopSwing(bestSwing);
            return true;
        }
        MapLocation target=pilih_target_enemy_paint(rc,ruinHint,prioritaskanRuin);
        if(target!=null&&rc.canAttack(target)){
            rc.attack(target);
            return true;
        }
        if(target!=null){
            boolean moved=RobotPlayer.gerak_ke_target_anti_osc(rc,target);
            if(rc.canAttack(target)){
                rc.attack(target);
                return true;
            }
            return moved;
        }
        return false;
    }

    public static boolean heal_rekan_prioritas(RobotController rc,MapLocation posisi,RobotInfo[] teman,boolean ancaman) throws GameActionException{
        if(teman==null||rc.getPaint()<55){
            return false;
        }
        RobotInfo best=null;
        int bestScore=-10000;
        for(RobotInfo ally:teman){
            UnitType tipe=ally.getType();
            if(tipe!=UnitType.SOLDIER&&tipe!=UnitType.SPLASHER){
                continue;
            }
            int lack=tipe.paintCapacity-ally.getPaintAmount();
            if(lack<25){
                continue;
            }
            int score=lack*4-posisi.distanceSquaredTo(ally.getLocation())*4;
            if(ancaman&&ally.getPaintAmount()<45){
                score+=70;
            }
            if(score>bestScore){
                bestScore=score;
                best=ally;
            }
        }
        if(best==null||bestScore<30){
            return false;
        }
        int reserve=ancaman?70:55;
        int send=Math.min(40,rc.getPaint()-reserve);
        if(send<15){
            return false;
        }
        MapLocation loc=best.getLocation();
        if(rc.canTransferPaint(loc,send)){
            rc.transferPaint(loc,send);
            return true;
        }
        boolean moved=RobotPlayer.gerak_ke_target_anti_osc(rc,loc);
        send=Math.min(40,rc.getPaint()-reserve);
        if(send>=15&&rc.canTransferPaint(loc,send)){
            rc.transferPaint(loc,send);
            return true;
        }
        return moved;
    }

    public static void gerak_ke_target(RobotController rc,MapLocation target) throws GameActionException{
        if(target==null){
            return;
        }
        RobotPlayer.gerak_ke_target_anti_osc(rc,target);
    }

    public static void gerak_acak(RobotController rc) throws GameActionException{
        RobotPlayer.dorong_ke_frontier(rc);
    }

    private static int score_mop_swing(MapLocation posisi,MapInfo[] infos,Direction arah,MapLocation ruinHint,boolean prioritaskanRuin){
        int score=0;
        for(MapInfo info:infos){
            if(!info.getPaint().isEnemy()){
                continue;
            }
            MapLocation loc=info.getMapLocation();
            Direction d=posisi.directionTo(loc);
            if(d==Direction.CENTER){
                continue;
            }
            if(d==arah||d==arah.rotateLeft()||d==arah.rotateRight()){
                int local=20-posisi.distanceSquaredTo(loc)*2;
                if(prioritaskanRuin&&ruinHint!=null){
                    int distRuin=loc.distanceSquaredTo(ruinHint);
                    if(distRuin<=20){
                        local+=25-distRuin;
                    }
                }
                score+=local;
            }
        }
        return score;
    }

    private static MapLocation pilih_target_enemy_paint(RobotController rc,MapLocation ruinHint,boolean prioritaskanRuin) throws GameActionException{
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        MapLocation best=null;
        int bestScore=-10000;
        for(MapInfo info:infos){
            if(!info.getPaint().isEnemy()){
                continue;
            }
            MapLocation loc=info.getMapLocation();
            int cluster=0;
            for(MapInfo petak:infos){
                if(loc.distanceSquaredTo(petak.getMapLocation())>4){
                    continue;
                }
                if(petak.getPaint().isEnemy()){
                    cluster+=1;
                }
            }
            int score=cluster*18-posisi.distanceSquaredTo(loc)*3;
            if(prioritaskanRuin&&ruinHint!=null){
                int distRuin=loc.distanceSquaredTo(ruinHint);
                if(distRuin<=25){
                    score+=90-distRuin*2;
                }
            }
            if(score>bestScore){
                bestScore=score;
                best=loc;
            }
        }
        return best;
    }
}
