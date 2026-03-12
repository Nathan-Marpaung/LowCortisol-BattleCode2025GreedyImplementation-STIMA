package Raynard;

import battlecode.common.*;

public class Helper_Splasher {

    private static final int W_ENEMY=17;
    private static final int W_EMPTY=6;
    private static final int W_RUIN=7;
    private static final int W_ALLY_WASTE=13;
    private static final int W_RISK=11;
    private static final int W_CLUSTER=5;
    private static final int SPLASH_ATTACK_THRESHOLD=32;
    private static final int SPLASH_MOVE_THRESHOLD=30;

    public static int hitung_bobot_serang(int round,MapLocation posisi,RobotInfo[] musuh,int paint,int hp){
        if(musuh==null||musuh.length==0){
            return -10000;
        }
        int best=-10000;
        for(RobotInfo enemy:musuh){
            if(!enemy.getType().isTowerType()){
                continue;
            }
            int dist=posisi.distanceSquaredTo(enemy.getLocation());
            int score=225;
            score+=Math.max(0,150-enemy.getHealth())*2;
            score-=dist*4;
            if(paint<UnitType.SPLASHER.paintCost+10){
                score-=150;
            }
            if(hp<50){
                score-=120;
            }
            if(round>200){
                score+=20;
            }
            if(score>best){
                best=score;
            }
        }
        return best;
    }

    public static int hitung_bobot_warnai(int turn_count,MapLocation posisi,MapInfo[] infos,int jumlah_paint){
        if(infos==null||infos.length==0){
            return -10000;
        }
        int best=-10000;
        int enemyCount=0;
        int emptyCount=0;
        int allyCount=0;
        for(MapInfo info:infos){
            PaintType paint=info.getPaint();
            int score=0;
            if(paint.isEnemy()){
                score+=34;
                enemyCount+=1;
            }else if(paint==PaintType.EMPTY){
                score+=jumlah_paint>=UnitType.SPLASHER.paintCost?20:10;
                emptyCount+=1;
            }else if(paint.isAlly()){
                score-=16;
                allyCount+=1;
            }
            if(turn_count<180&&paint==PaintType.EMPTY){
                score+=8;
            }
            score-=posisi.distanceSquaredTo(info.getMapLocation())*2;
            if(score>best){
                best=score;
            }
        }
        return best+enemyCount*8+emptyCount*5-allyCount*4;
    }

    public static int hitung_skor_splash_terbaik(RobotController rc,MapLocation ruinHint) throws GameActionException{
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        int paint=rc.getPaint();
        int best=-10000;
        for(MapInfo info:infos){
            int score=score_splash_center(info.getMapLocation(),ruinHint,infos,musuh,paint);
            if(score>best){
                best=score;
            }
        }
        return best;
    }

    public static boolean lakukan_splash_utama(RobotController rc,MapLocation ruinHint) throws GameActionException{
        if(!rc.isActionReady()){
            return false;
        }
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        int paint=rc.getPaint();
        MapLocation attackTarget=null;
        MapLocation moveTarget=null;
        int bestAttack=-10000;
        int bestMove=-10000;
        for(MapInfo info:infos){
            MapLocation center=info.getMapLocation();
            int score=score_splash_center(center,ruinHint,infos,musuh,paint);
            if(rc.canAttack(center)){
                if(score>bestAttack){
                    bestAttack=score;
                    attackTarget=center;
                }
            }else if(score>bestMove){
                bestMove=score;
                moveTarget=center;
            }
        }
        if(attackTarget!=null&&bestAttack>=SPLASH_ATTACK_THRESHOLD&&cukup_paint_serang(rc)){
            rc.attack(attackTarget);
            return true;
        }
        if(moveTarget!=null&&bestMove>=SPLASH_MOVE_THRESHOLD){
            boolean moved=RobotPlayer.gerak_ke_target_anti_osc(rc,moveTarget);
            if(rc.isActionReady()){
                MapInfo[] infos2=rc.senseNearbyMapInfos();
                RobotInfo[] musuh2=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
                int paint2=rc.getPaint();
                MapLocation retry=null;
                int retryScore=-10000;
                for(MapInfo info:infos2){
                    MapLocation center=info.getMapLocation();
                    if(!rc.canAttack(center)){
                        continue;
                    }
                    int score=score_splash_center(center,ruinHint,infos2,musuh2,paint2);
                    if(score>retryScore){
                        retryScore=score;
                        retry=center;
                    }
                }
                if(retry!=null&&retryScore>=SPLASH_ATTACK_THRESHOLD&&cukup_paint_serang(rc)){
                    rc.attack(retry);
                    return true;
                }
            }
            return moved;
        }
        return false;
    }

    public static void lakukan_penyerangan(RobotController rc) throws GameActionException{
        MapLocation posisi=rc.getLocation();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        MapLocation target=null;
        int best=-10000;
        for(RobotInfo enemy:musuh){
            if(!enemy.getType().isTowerType()){
                continue;
            }
            int score=240;
            score+=Math.max(0,150-enemy.getHealth())*2;
            score-=posisi.distanceSquaredTo(enemy.getLocation())*4;
            if(score>best){
                best=score;
                target=enemy.getLocation();
            }
        }
        if(target==null){
            target=RobotPlayer.target_tower_musuh_terbagi(rc,posisi);
            if(target==null){
                target=RobotPlayer.target_tower_musuh_prediksi_simetri(rc);
                if(target==null){
                    RobotPlayer.dorong_ke_frontier(rc);
                    return;
                }
            }
        }
        if(cukup_paint_serang(rc)&&rc.canAttack(target)){
            rc.attack(target);
            return;
        }
        RobotPlayer.gerak_ke_target_anti_osc(rc,target);
        if(cukup_paint_serang(rc)&&rc.canAttack(target)){
            rc.attack(target);
        }
    }

    public static void warnai_petak(RobotController rc,MapInfo[] infos) throws GameActionException{
        if(lakukan_splash_utama(rc,null)){
            return;
        }
        if(infos==null){
            infos=rc.senseNearbyMapInfos();
        }
        MapLocation posisi=rc.getLocation();
        MapLocation bestAttack=null;
        MapLocation bestMove=null;
        int bestAttackScore=-10000;
        int bestMoveScore=-10000;
        for(MapInfo info:infos){
            MapLocation loc=info.getMapLocation();
            int score=score_petak_runtime(posisi,info,infos);
            if(rc.canAttack(loc)){
                if(score>bestAttackScore){
                    bestAttackScore=score;
                    bestAttack=loc;
                }
            }else if(score>bestMoveScore){
                bestMoveScore=score;
                bestMove=loc;
            }
        }
        if(bestAttack!=null&&bestAttackScore>=8&&cukup_paint_serang(rc)){
            rc.attack(bestAttack);
            return;
        }
        if(bestMove!=null&&bestMoveScore>=4){
            RobotPlayer.gerak_ke_target_anti_osc(rc,bestMove);
            if(cukup_paint_serang(rc)&&rc.canAttack(bestMove)){
                rc.attack(bestMove);
                return;
            }
        }
        RobotPlayer.dorong_ke_frontier(rc);
    }

    private static boolean cukup_paint_serang(RobotController rc){
        return rc.isActionReady()&&rc.getPaint()>=rc.getType().attackCost;
    }

    private static int score_petak_runtime(MapLocation posisi,MapInfo info,MapInfo[] infos){
        MapLocation loc=info.getMapLocation();
        PaintType paint=info.getPaint();
        int enemy=0;
        int ally=0;
        int empty=0;
        for(MapInfo petak:infos){
            if(loc.distanceSquaredTo(petak.getMapLocation())>4){
                continue;
            }
            if(petak.getPaint().isEnemy()){
                enemy+=1;
            }else if(petak.getPaint().isAlly()){
                ally+=1;
            }else if(petak.getPaint()==PaintType.EMPTY){
                empty+=1;
            }
        }
        int score=0;
        if(paint.isEnemy()){
            score+=52;
        }else if(paint==PaintType.EMPTY){
            score+=26;
        }else if(paint.isAlly()){
            score-=22;
        }
        score+=enemy*7;
        score+=empty*3;
        score-=ally*5;
        score-=posisi.distanceSquaredTo(loc)*2;
        return score;
    }

    private static int score_splash_center(MapLocation center,MapLocation ruinHint,MapInfo[] infos,RobotInfo[] musuh,int paint){
        int enemy=0;
        int empty=0;
        int allyWaste=0;
        for(MapInfo info:infos){
            if(center.distanceSquaredTo(info.getMapLocation())>4){
                continue;
            }
            PaintType p=info.getPaint();
            if(p.isEnemy()){
                enemy+=1;
            }else if(p==PaintType.EMPTY){
                empty+=1;
            }else if(p.isAlly()){
                allyWaste+=1;
            }
        }
        int ruinBonus=0;
        if(ruinHint!=null){
            int dist=center.distanceSquaredTo(ruinHint);
            if(dist<=25){
                ruinBonus=25-dist;
            }
        }
        int risk=0;
        for(RobotInfo enemyInfo:musuh){
            if(center.distanceSquaredTo(enemyInfo.getLocation())>4){
                continue;
            }
            if(enemyInfo.getType().isTowerType()){
                risk+=3;
            }else{
                risk+=1;
            }
        }
        int cluster=(enemy*2)+empty-allyWaste;
        int score=0;
        score+=enemy*W_ENEMY;
        score+=empty*W_EMPTY;
        score+=ruinBonus*W_RUIN;
        score+=cluster*W_CLUSTER;
        score-=allyWaste*W_ALLY_WASTE;
        score-=risk*W_RISK;
        if(paint<UnitType.SPLASHER.paintCost+10){
            score-=30;
        }
        return score;
    }
}
