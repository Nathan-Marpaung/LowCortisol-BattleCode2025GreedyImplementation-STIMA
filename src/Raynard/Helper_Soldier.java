package Raynard;

import battlecode.common.*;

public class Helper_Soldier {

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
            int score=250;
            score+=Math.max(0,140-enemy.getHealth())*2;
            score-=dist*4;
            if(paint<UnitType.SOLDIER.paintCost+10){
                score-=170;
            }
            if(hp<45){
                score-=120;
            }
            if(round>180){
                score+=25;
            }
            if(round>320){
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
            int score=score_petak(info,infos,jumlah_paint);
            score-=posisi.distanceSquaredTo(info.getMapLocation())*2;
            if(turn_count<180&&paint==PaintType.EMPTY){
                score+=6;
            }
            if(paint.isEnemy()){
                enemyCount+=1;
            }else if(paint==PaintType.EMPTY){
                emptyCount+=1;
            }else if(paint.isAlly()){
                allyCount+=1;
            }
            if(score>best){
                best=score;
            }
        }
        return best+enemyCount*10+emptyCount*4-allyCount*3;
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
            MapLocation loc=enemy.getLocation();
            int score=260;
            score+=Math.max(0,160-enemy.getHealth())*2;
            score-=posisi.distanceSquaredTo(loc)*4;
            if(score>best){
                best=score;
                target=loc;
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
            }else{
                if(score>bestMoveScore){
                    bestMoveScore=score;
                    bestMove=loc;
                }
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
        if(bestAttack!=null&&bestAttackScore>=2&&cukup_paint_serang(rc)&&rc.canAttack(bestAttack)){
            rc.attack(bestAttack);
            return;
        }
        RobotPlayer.dorong_ke_frontier(rc);
    }

    private static boolean cukup_paint_serang(RobotController rc){
        return rc.isActionReady()&&rc.getPaint()>=rc.getType().attackCost;
    }

    private static int score_petak(MapInfo info,MapInfo[] infos,int jumlah_paint){
        PaintType paint=info.getPaint();
        int score=0;
        if(paint.isEnemy()){
            score+=44;
        }else if(paint==PaintType.EMPTY){
            score+=jumlah_paint>=UnitType.SOLDIER.paintCost?26:14;
        }else if(paint.isAlly()){
            score-=12;
        }
        score+=nilai_frontier(info,infos);
        return score;
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
            PaintType p=petak.getPaint();
            if(p.isEnemy()){
                enemy+=1;
            }else if(p.isAlly()){
                ally+=1;
            }else if(p==PaintType.EMPTY){
                empty+=1;
            }
        }
        int score=0;
        if(paint.isEnemy()){
            score+=56;
        }else if(paint==PaintType.EMPTY){
            score+=24;
        }else if(paint.isAlly()){
            score-=18;
        }
        score+=enemy*8;
        score+=empty*3;
        score-=ally*4;
        if(enemy+empty>=ally+2){
            score+=10;
        }
        score-=posisi.distanceSquaredTo(loc)*2;
        return score;
    }

    private static int nilai_frontier(MapInfo center,MapInfo[] all){
        int nilai=0;
        PaintType paint=center.getPaint();
        for(MapInfo info:all){
            int d=center.getMapLocation().distanceSquaredTo(info.getMapLocation());
            if(d>4){
                continue;
            }
            PaintType p=info.getPaint();
            if(paint.isEnemy()){
                if(p.isEnemy()){
                    nilai+=2;
                }else if(p==PaintType.EMPTY){
                    nilai+=3;
                }else if(p.isAlly()){
                    nilai-=1;
                }
            }else if(paint==PaintType.EMPTY){
                if(p.isEnemy()){
                    nilai+=3;
                }else if(p.isAlly()){
                    nilai+=1;
                }
            }else if(paint.isAlly()){
                if(p.isEnemy()){
                    nilai+=2;
                }else if(p==PaintType.EMPTY){
                    nilai+=1;
                }else if(p.isAlly()){
                    nilai-=1;
                }
            }
        }
        return nilai;
    }
}
