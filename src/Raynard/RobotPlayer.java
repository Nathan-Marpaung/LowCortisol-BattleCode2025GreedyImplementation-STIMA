package Raynard;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    private enum SoldierGoal{
        BUILD_RUIN,
        ASSIST_RUIN,
        PRESSURE_TOWER,
        PAINT_FRONTIER,
        REFILL,
        RETREAT
    }

    private enum SplasherGoal{
        DENSE_SPLASH,
        SUPPORT_RUIN,
        PRESSURE_CLUSTER,
        PAINT_FRONTIER,
        REFILL,
        RETREAT
    }

    private enum MopperGoal{
        CLEAN_RUIN,
        CLEAN_FRONTLINE,
        SUPPORT_ARMY,
        REFILL,
        RETURN_WORK
    }

    static int turnCount=0;
    static boolean nextTowerMoney=false;
    static int towerSpawnCount=0;
    static int towerUpgradeStep=0;
    static final int MSG_RUIN_CLAIM=1;
    static final int MSG_ENEMY_TOWER=2;

    static final Random rng=new Random(6147);

    static final Direction[] directions={
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    private static boolean is_paint_tower(UnitType tipe){
        return tipe==UnitType.LEVEL_ONE_PAINT_TOWER||tipe==UnitType.LEVEL_TWO_PAINT_TOWER||tipe==UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    private static boolean is_money_tower(UnitType tipe){
        return tipe==UnitType.LEVEL_ONE_MONEY_TOWER||tipe==UnitType.LEVEL_TWO_MONEY_TOWER||tipe==UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    private static boolean is_defense_tower(UnitType tipe){
        return tipe==UnitType.LEVEL_ONE_DEFENSE_TOWER||tipe==UnitType.LEVEL_TWO_DEFENSE_TOWER||tipe==UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    private static int target_persen_money_tower(int round){
        if(round<=120){
            return 35;
        }
        if(round<=320){
            return 45;
        }
        if(round<=900){
            return 52;
        }
        return 55;
    }

    private static UnitType target_tower_opening_dua_eco(RobotController rc,int threat){
        int round=rc.getRoundNum();
        int totalTower=rc.getNumberTowers();
        int extraEco=Math.max(0,totalTower-GameConstants.NUMBER_INITIAL_TOWERS);
        if(round>420||extraEco>=2){
            return null;
        }
        if(threat>=9&&round>=90){
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        if(extraEco==0){
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    private static void catat_tower_ruin_selesai(UnitType tipe){
        if(tipe==UnitType.LEVEL_ONE_PAINT_TOWER){
            nextTowerMoney=true;
        }else if(tipe==UnitType.LEVEL_ONE_MONEY_TOWER){
            nextTowerMoney=false;
        }
    }

    static final IdLocationTable currentPosById=new IdLocationTable();
    static final IdLocationTable previousPosById=new IdLocationTable();
    static final IdIntTable noMoveTurnsById=new IdIntTable();
    static final IdIntTable lastMoveTargetById=new IdIntTable();
    static final IdIntTable lastMoveTargetDistanceById=new IdIntTable();
    static final IdIntTable noProgressToTargetTurnsById=new IdIntTable();
    static final IdLocationTable ruinFocusById=new IdLocationTable();
    static final IdLocationTable soldierReturnTargetById=new IdLocationTable();
    static final IdLocationTable splasherReturnTargetById=new IdLocationTable();
    static final IdLocationTable mopperReturnTargetById=new IdLocationTable();
    static final IdIntTable ruinNoProgressById=new IdIntTable();
    static final IdIntTable ruinLastRemainingById=new IdIntTable();
    static final IdLocationTable lastRuinTargetById=new IdLocationTable();
    static final IdIntTable lastRuinDistanceById=new IdIntTable();
    static final IdIntTable ruinStuckTurnsById=new IdIntTable();
    static final IdIntTable laneBiasById=new IdIntTable();
    static final IdIntTable towerSpawnFailStreakById=new IdIntTable();
    static final IdIntTable lastSeenRoundById=new IdIntTable();
    static int lastStateCleanupRound=-1;
    static MapLocation sharedEnemyTowerLoc=null;
    static int sharedEnemyTowerRound=-1;
    static int ruinClaimCacheRound=-1;
    static int ruinClaimCacheSize=0;
    static int[] ruinClaimCacheKeys=new int[32];
    static int[] ruinClaimCacheCounts=new int[32];
    static int ruinClaimSeenPairSize=0;
    static int[] ruinClaimSeenPairSenders=new int[64];
    static int[] ruinClaimSeenPairKeys=new int[64];
    static int ruinScanCacheRound=-1;
    static int ruinScanCacheRobotId=-1;
    static MapLocation ruinScanCacheLoc=null;
    static MapInfo[] ruinScanCacheInfos=null;
    static int ruinScanCacheEnemyPaint=0;
    static int ruinScanCachePassable=0;
    static int ruinScanCacheAllyMarks=0;
    static int ruinScanCacheSisa=-1;
    static int ruinScanCacheRekan=0;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException{
        while(true){
            try{
                sentuh_dan_bersihkan_state(rc);
                terima_pesan_tim(rc);
                turnCount+=1;
                updatePositionMemory(rc);
                UnitType tipe=rc.getType();
                if(tipe.isTowerType()){
                    runTower(rc);
                }else if(tipe==UnitType.SOLDIER){
                    runSoldier(rc);
                }else if(tipe==UnitType.SPLASHER){
                    runSplasher(rc);
                }else if(tipe==UnitType.MOPPER){
                    runMopper(rc);
                }
            }catch(Exception e){
                System.out.println("Error runtime di robot "+rc.getType()+" id="+rc.getID()+" round="+rc.getRoundNum());
                e.printStackTrace();
            }finally{
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException{
        coba_serang_tower(rc);
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        lapor_tower_musuh_terlihat(rc,musuh);
        if(!rc.isActionReady()){
            return;
        }
        MapLocation posisi=rc.getLocation();
        int round=rc.getRoundNum();
        int chips=rc.getChips();
        int paint=rc.getPaint();
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        MapInfo[] infos=rc.senseNearbyMapInfos();

        int soldiers=0;
        int splashers=0;
        int moppers=0;
        int allyPaintTower=0;
        int allyMoneyTower=0;
        int allyDefenseTower=0;
        for(RobotInfo info:teman){
            UnitType tipe=info.getType();
            if(tipe==UnitType.SOLDIER){
                soldiers+=1;
            }else if(tipe==UnitType.SPLASHER){
                splashers+=1;
            }else if(tipe==UnitType.MOPPER){
                moppers+=1;
            }else if(is_paint_tower(tipe)){
                allyPaintTower+=1;
            }else if(is_money_tower(tipe)){
                allyMoneyTower+=1;
            }else if(is_defense_tower(tipe)){
                allyDefenseTower+=1;
            }
        }

        int enemyPaintLocal=hitung_enemy_paint_lokal(infos);
        int emptyPaintLocal=hitung_empty_paint_lokal(infos);
        int territoryUrgency=hitung_urgensi_territory(infos);
        int dirtyRuins=hitung_ruin_kotor_lokal(rc);
        int pendingRuins=hitung_ruin_belum_jadi_lokal(rc);
        int availableSpawnCells=hitung_spawn_cell_legal(rc,posisi);
        int unitTempurDekat=soldiers+splashers+moppers;
        int reserveChipsSpawn=hitung_reserve_chips_spawn(round,enemyPaintLocal,pendingRuins,unitTempurDekat);
        int extraEco=Math.max(0,rc.getNumberTowers()-GameConstants.NUMBER_INITIAL_TOWERS);
        boolean openingEcoPending=round<=420&&extraEco<2;
        if(openingEcoPending){
            reserveChipsSpawn=Math.max(reserveChipsSpawn,UnitType.LEVEL_ONE_PAINT_TOWER.moneyCost+500);
        }

        int needSoldier=200;
        int needMopper=110;
        int needSplasher=80;

        needSoldier+=Math.max(0,3-soldiers)*130;
        needMopper+=Math.max(0,1-moppers)*190;
        needSplasher+=Math.max(0,1-splashers)*120;

        needSoldier+=dirtyRuins*55;
        needMopper+=dirtyRuins*120;
        needMopper+=enemyPaintLocal*12;
        needSoldier+=enemyPaintLocal*4;
        needSplasher+=Math.max(0,enemyPaintLocal-8)*14;
        needSoldier+=emptyPaintLocal*5;
        needSplasher+=emptyPaintLocal*7;
        needMopper-=emptyPaintLocal*3;
        if(territoryUrgency>=120){
            needSoldier+=80;
            needSplasher+=140;
            needMopper-=90;
        }
        if(territoryUrgency>=220){
            needSoldier+=90;
            needSplasher+=120;
        }

        if(round<=120){
            needSoldier+=150;
            needMopper+=40;
            needSplasher-=60;
        }else if(round<=260){
            needSoldier+=60;
            needSplasher+=40;
        }else{
            needSplasher+=80;
            needMopper+=25;
        }

        if(chips>=2200&&paint>=UnitType.SOLDIER.paintCost+UnitType.MOPPER.paintCost){
            needSoldier+=80;
            needMopper+=50;
            needSplasher+=70;
        }
        if(chips>=3600&&paint>=UnitType.SPLASHER.paintCost+15){
            needSplasher+=110;
        }
        if(paint<UnitType.SOLDIER.paintCost+20){
            needSplasher-=130;
            needSoldier-=50;
        }
        if(paint<UnitType.MOPPER.paintCost+10){
            needMopper-=100;
        }
        if(availableSpawnCells<=1){
            needMopper+=20;
            needSoldier-=10;
        }
        int chipsSpare=chips-reserveChipsSpawn;
        if(allyPaintTower>0&&allyMoneyTower>0){
            needSoldier+=90;
            needSplasher+=80;
            needMopper+=60;
        }else if(allyMoneyTower==0&&round>=140){
            needSoldier-=35;
            needSplasher-=30;
        }
        if(chipsSpare>=900&&paint>=UnitType.MOPPER.paintCost+30){
            needSoldier+=70;
            needSplasher+=95;
        }
        if(chipsSpare>=1400&&paint>=UnitType.SPLASHER.paintCost+40){
            needSoldier+=120;
            needSplasher+=130;
            needMopper+=80;
        }
        if(allyDefenseTower>=Math.max(2,allyPaintTower+allyMoneyTower)){
            needSplasher+=40;
            needSoldier+=25;
        }
        if(openingEcoPending){
            boolean openingEmergency=enemyPaintLocal>=10||musuh.length>=4||unitTempurDekat<=1||territoryUrgency>=220;
            if(!openingEmergency&&unitTempurDekat>=2){
                towerSpawnFailStreakById.put(rc.getID(),0);
                return;
            }
        }

        UnitType[] urutan=susun_spawn_berdasarkan_score(needSoldier,needMopper,needSplasher);
        if(coba_spawn_dengan_urutan(rc,posisi,urutan,reserveChipsSpawn)){
            towerSpawnCount+=1;
            towerSpawnFailStreakById.put(rc.getID(),0);
            return;
        }

        if(chips>=2500&&paint>=UnitType.SOLDIER.paintCost){
            UnitType[] fallback;
            if(paint>=UnitType.SPLASHER.paintCost&&round>=200){
                fallback=new UnitType[]{UnitType.SOLDIER,UnitType.SPLASHER,UnitType.MOPPER,UnitType.SOLDIER};
            }else if(paint>=UnitType.MOPPER.paintCost){
                fallback=new UnitType[]{UnitType.SOLDIER,UnitType.MOPPER,UnitType.SOLDIER};
            }else{
                fallback=new UnitType[]{UnitType.SOLDIER};
            }
            if(coba_spawn_dengan_urutan(rc,posisi,fallback,reserveChipsSpawn)){
                towerSpawnCount+=1;
                towerSpawnFailStreakById.put(rc.getID(),0);
                return;
            }
        }

        int fail=towerSpawnFailStreakById.getOrDefault(rc.getID(),0)+1;
        towerSpawnFailStreakById.put(rc.getID(),fail);
        if(layak_upgrade_setelah_gagal_spawn(round,chips,paint,unitTempurDekat)&&coba_upgrade_tower_prioritas(rc)){
            return;
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException{
        int id=rc.getID();
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        lapor_tower_musuh_terlihat(rc,musuh);
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation[] ruins=rc.senseNearbyRuins(-1);
        MapLocation ruin=pilih_ruin_fokus(rc,posisi,ruins);

        int hp=rc.getHealth();
        int paint=rc.getPaint();
        int crowd=hitung_crowding_lokal(posisi,teman);
        int territoryUrgency=hitung_urgensi_territory(infos);
        boolean territoryMode=mode_territory_utama(rc,infos);
        int paintFrontier=Helper_Soldier.hitung_bobot_warnai(rc.getRoundNum(),posisi,infos,paint)-crowd*12+territoryUrgency;
        if(territoryMode){
            paintFrontier+=170;
        }
        int pressure=Helper_Soldier.hitung_bobot_serang(rc.getRoundNum(),posisi,musuh,paint,hp);
        MapLocation intelTower=target_tower_musuh_terbagi(rc,posisi);
        if(intelTower!=null){
            int intelPressure=640-posisi.distanceSquaredTo(intelTower)*3;
            if(paint<UnitType.SOLDIER.paintCost+8){
                intelPressure-=120;
            }
            if(hp<45){
                intelPressure-=140;
            }
            pressure=Math.max(pressure,intelPressure);
        }else{
            MapLocation prediksi=target_tower_musuh_prediksi_simetri(rc);
            if(prediksi!=null&&rc.getRoundNum()>=120){
                int prediksiPressure=380-posisi.distanceSquaredTo(prediksi)*2;
                pressure=Math.max(pressure,prediksiPressure);
            }
        }
        if(territoryMode){
            pressure-=220;
        }
        int refill=paint<UnitType.SOLDIER.paintCost+20?860-paint*6:-10000;
        int retreat=(hp<32||(hp<55&&musuh.length>=3))?880-hp*8:-10000;

        int build=-10000;
        int assist=-10000;
        int sisaRuin=-1;
        int enemyRuin=0;
        int rekanRuin=0;
        boolean ruinClosePressure=false;
        if(ruin!=null){
            int dist=posisi.distanceSquaredTo(ruin);
            enemyRuin=hitung_enemy_paint_sekitar_ruin(rc,ruin);
            rekanRuin=hitung_rekan_dekat_ruin(rc,ruin);
            sisaRuin=hitung_sisa_pattern_ruin(rc,ruin);
            int passable=hitung_passable_sekitar_ruin(rc,ruin);
            ruinClosePressure=dist<=36&&rekanRuin<=2&&(sisaRuin<0||sisaRuin<=6);
            if(passable>=10){
                build=1040-dist*3-enemyRuin*28-Math.max(0,rekanRuin-1)*320;
                assist=700-dist*2-enemyRuin*18-Math.max(0,rekanRuin-2)*220;
                if(sisaRuin>=0){
                    build+=Math.max(0,210-sisaRuin*14);
                    assist+=Math.max(0,140-sisaRuin*10);
                }else{
                    build+=140;
                    assist+=60;
                }
                if(enemyRuin<=3&&rekanRuin<=1&&paint>=UnitType.SOLDIER.paintCost+10){
                    build+=340;
                }
                if(enemyRuin<=4&&dist<=36){
                    build+=220;
                    assist+=100;
                }
                if(enemyRuin>=9){
                    build-=220;
                }
                if(rekanRuin>=4){
                    build-=500;
                    assist-=260;
                }
            }
        }
        if(territoryMode){
            int buildPenalty=220;
            int assistPenalty=150;
            if(rc.getRoundNum()<=320&&ruinClosePressure){
                buildPenalty=40;
                assistPenalty=25;
            }
            build-=buildPenalty;
            assist-=assistPenalty;
            if(enemyRuin>=8){
                assist+=140;
            }
            if(sisaRuin>=0&&sisaRuin<=2&&rekanRuin<=2){
                build+=260;
            }
        }

        if(ruin!=null&&sisaRuin>=0){
            int lama=ruinLastRemainingById.getOrDefault(id,sisaRuin);
            int noProgress=ruinNoProgressById.getOrDefault(id,0);
            if(sisaRuin>=lama){
                noProgress+=1;
            }else{
                noProgress=0;
            }
            ruinLastRemainingById.put(id,sisaRuin);
            ruinNoProgressById.put(id,noProgress);
            if(noProgress>=8){
                hapus_ruin_fokus(id);
                build-=400;
                assist-=300;
            }
        }

        SoldierGoal goal=SoldierGoal.PAINT_FRONTIER;
        int best=paintFrontier;
        if(build>best){
            best=build;
            goal=SoldierGoal.BUILD_RUIN;
        }
        if(assist>best){
            best=assist;
            goal=SoldierGoal.ASSIST_RUIN;
        }
        if(pressure>best){
            best=pressure;
            goal=SoldierGoal.PRESSURE_TOWER;
        }
        if(refill>best){
            best=refill;
            goal=SoldierGoal.REFILL;
        }
        if(retreat>best){
            goal=SoldierGoal.RETREAT;
        }
        if(territoryMode&&goal!=SoldierGoal.REFILL&&goal!=SoldierGoal.RETREAT){
            boolean ruinFinishWindow=ruin!=null&&sisaRuin>=0&&sisaRuin<=2&&enemyRuin<=5&&rekanRuin<=2;
            boolean ruinProgressWindow=ruinFinishWindow;
            if(!ruinProgressWindow&&ruin!=null&&sisaRuin>=0&&sisaRuin<=4&&enemyRuin<=8&&rekanRuin<=2){
                ruinProgressWindow=true;
            }
            if(!ruinProgressWindow&&rc.getRoundNum()<=320&&ruinClosePressure){
                ruinProgressWindow=true;
            }
            if(!ruinProgressWindow&&paintFrontier+90>=best){
                goal=SoldierGoal.PAINT_FRONTIER;
                best=paintFrontier;
            }
        }

        if(goal==SoldierGoal.REFILL){
            MapLocation returnTarget=ruin!=null?ruin:soldierReturnTargetById.get(id);
            if(jalankan_refill(rc,id,returnTarget,UnitType.SOLDIER.paintCost+35,soldierReturnTargetById)){
                return;
            }
            goal=SoldierGoal.PAINT_FRONTIER;
        }
        if(goal==SoldierGoal.RETREAT){
            if(mundur_ke_tower_terdekat(rc)){
                return;
            }
            Helper_Soldier.warnai_petak(rc,infos);
            return;
        }
        if(goal==SoldierGoal.BUILD_RUIN||goal==SoldierGoal.ASSIST_RUIN){
            boolean assistGoal=goal==SoldierGoal.ASSIST_RUIN;
            if(jalankan_goal_ruin(rc,ruin,assistGoal)){
                return;
            }
            if(ruin!=null&&gerak_ke_target_anti_osc(rc,ruin)){
                jalankan_goal_ruin(rc,ruin,assistGoal);
                return;
            }
        }
        if(goal==SoldierGoal.PRESSURE_TOWER){
            if(!territoryMode||rc.getRoundNum()>=950||pressure>=paintFrontier+180){
                Helper_Soldier.lakukan_penyerangan(rc);
                return;
            }
        }
        if(!territoryMode&&coba_ruin_denial(rc,ruin,ruins)){
            return;
        }
        Helper_Soldier.warnai_petak(rc,infos);
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        int id=rc.getID();
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        lapor_tower_musuh_terlihat(rc,musuh);
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation[] ruins=rc.senseNearbyRuins(-1);
        MapLocation ruin=pilih_ruin_fokus(rc,posisi,ruins);

        int paint=rc.getPaint();
        int hp=rc.getHealth();
        int crowd=hitung_crowding_lokal(posisi,teman);
        int territoryUrgency=hitung_urgensi_territory(infos);
        boolean territoryMode=mode_territory_utama(rc,infos);
        int dense=Helper_Splasher.hitung_skor_splash_terbaik(rc,ruin)-crowd*10+territoryUrgency/2;
        int swing=Helper_Splasher.hitung_bobot_warnai(rc.getRoundNum(),posisi,infos,paint)-crowd*8+territoryUrgency;
        if(territoryMode){
            dense+=70;
            swing+=150;
        }
        int pressure=Helper_Splasher.hitung_bobot_serang(rc.getRoundNum(),posisi,musuh,paint,hp)+musuh.length*22;
        MapLocation intelTower=target_tower_musuh_terbagi(rc,posisi);
        if(intelTower!=null){
            int intelPressure=600-posisi.distanceSquaredTo(intelTower)*3;
            if(paint<UnitType.SPLASHER.paintCost+10){
                intelPressure-=120;
            }
            if(hp<50){
                intelPressure-=120;
            }
            pressure=Math.max(pressure,intelPressure);
        }else{
            MapLocation prediksi=target_tower_musuh_prediksi_simetri(rc);
            if(prediksi!=null&&rc.getRoundNum()>=130){
                int prediksiPressure=360-posisi.distanceSquaredTo(prediksi)*2;
                pressure=Math.max(pressure,prediksiPressure);
            }
        }
        if(territoryMode){
            pressure-=220;
        }
        int refill=paint<UnitType.SPLASHER.paintCost+28?860-paint*5:-10000;
        int retreat=(hp<40||(hp<62&&musuh.length>=3))?860-hp*7:-10000;

        int support=-10000;
        int enemyRuin=0;
        int rekanRuin=0;
        int sisaRuin=-1;
        boolean ruinMomentum=false;
        if(ruin!=null){
            enemyRuin=hitung_enemy_paint_sekitar_ruin(rc,ruin);
            rekanRuin=hitung_rekan_dekat_ruin(rc,ruin);
            sisaRuin=hitung_sisa_pattern_ruin(rc,ruin);
            int dist=posisi.distanceSquaredTo(ruin);
            support=700+enemyRuin*34-dist*2-Math.max(0,rekanRuin-2)*210;
            if(enemyRuin>=8){
                support+=60;
            }
            ruinMomentum=dist<=49&&rekanRuin<=2&&(enemyRuin>=7||(sisaRuin>=0&&sisaRuin<=5));
        }
        if(territoryMode){
            int supportPenalty=100;
            if(rc.getRoundNum()<=320&&ruinMomentum){
                supportPenalty=25;
            }
            support-=supportPenalty;
            if(enemyRuin>=9){
                support+=140;
            }
        }

        SplasherGoal goal=SplasherGoal.PAINT_FRONTIER;
        int best=swing;
        if(dense>best){
            best=dense;
            goal=SplasherGoal.DENSE_SPLASH;
        }
        if(support>best){
            best=support;
            goal=SplasherGoal.SUPPORT_RUIN;
        }
        if(pressure>best){
            best=pressure;
            goal=SplasherGoal.PRESSURE_CLUSTER;
        }
        if(refill>best){
            best=refill;
            goal=SplasherGoal.REFILL;
        }
        if(retreat>best){
            goal=SplasherGoal.RETREAT;
        }
        if(territoryMode&&goal!=SplasherGoal.REFILL&&goal!=SplasherGoal.RETREAT){
            boolean ruinCrisis=ruin!=null&&enemyRuin>=10&&rekanRuin<=2;
            if(!ruinCrisis&&!ruinMomentum&&swing+95>=best){
                goal=SplasherGoal.PAINT_FRONTIER;
                best=swing;
            }
        }

        if(goal==SplasherGoal.REFILL){
            MapLocation returnTarget=ruin!=null?ruin:splasherReturnTargetById.get(id);
            if(jalankan_refill(rc,id,returnTarget,UnitType.SPLASHER.paintCost+38,splasherReturnTargetById)){
                return;
            }
            goal=SplasherGoal.PAINT_FRONTIER;
        }
        if(goal==SplasherGoal.RETREAT){
            if(mundur_ke_tower_terdekat(rc)){
                return;
            }
            Helper_Splasher.warnai_petak(rc,infos);
            return;
        }
        if(goal==SplasherGoal.SUPPORT_RUIN&&ruin!=null){
            if(territoryMode&&!ruinMomentum&&enemyRuin<7&&(sisaRuin<0||sisaRuin>4)&&swing+110>=support){
                goal=SplasherGoal.PAINT_FRONTIER;
            }else{
            if(bersihkan_ruin_dulu(rc,ruin)){
                return;
            }
            if(rekanRuin<=2&&jalankan_goal_ruin(rc,ruin,true)){
                return;
            }
            if(gerak_ke_target_anti_osc(rc,ruin)){
                jalankan_goal_ruin(rc,ruin,true);
                return;
            }
            }
        }
        if(goal==SplasherGoal.DENSE_SPLASH||goal==SplasherGoal.SUPPORT_RUIN){
            if(Helper_Splasher.lakukan_splash_utama(rc,ruin)){
                return;
            }
            goal=SplasherGoal.PAINT_FRONTIER;
        }
        if(goal==SplasherGoal.PRESSURE_CLUSTER){
            if(!territoryMode||rc.getRoundNum()>=950||pressure>=swing+180){
                Helper_Splasher.lakukan_penyerangan(rc);
                return;
            }
        }
        Helper_Splasher.warnai_petak(rc,infos);
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        int id=rc.getID();
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        lapor_tower_musuh_terlihat(rc,musuh);
        MapLocation[] ruins=rc.senseNearbyRuins(-1);
        MapLocation ruin=pilih_ruin_fokus(rc,posisi,ruins);

        int paint=rc.getPaint();
        int enemyLocal=hitung_enemy_paint_lokal(infos);
        int territoryUrgency=hitung_urgensi_territory(infos);
        boolean territoryMode=mode_territory_utama(rc,infos);
        int enemyRuin=ruin==null?0:hitung_enemy_paint_sekitar_ruin(rc,ruin);
        MapLocation returnWork=mopperReturnTargetById.get(id);

        int cleanRuin=ruin==null?-10000:840+enemyRuin*42-posisi.distanceSquaredTo(ruin)*2;
        int cleanFront=260+enemyLocal*30+territoryUrgency/2;
        int support=musuh.length>0?310+musuh.length*32:-10000;
        int refill=paint<UnitType.MOPPER.paintCost+14?820-paint*7:-10000;
        int returnScore=returnWork!=null?220-posisi.distanceSquaredTo(returnWork):-10000;
        if(territoryMode){
            cleanRuin-=80;
            support-=120;
        }

        MopperGoal goal=MopperGoal.CLEAN_FRONTLINE;
        int best=cleanFront;
        if(cleanRuin>best){
            best=cleanRuin;
            goal=MopperGoal.CLEAN_RUIN;
        }
        if(support>best){
            best=support;
            goal=MopperGoal.SUPPORT_ARMY;
        }
        if(refill>best){
            best=refill;
            goal=MopperGoal.REFILL;
        }
        if(returnScore>best){
            goal=MopperGoal.RETURN_WORK;
        }
        if(territoryMode&&goal==MopperGoal.SUPPORT_ARMY&&cleanFront+40>=support){
            goal=MopperGoal.CLEAN_FRONTLINE;
        }

        if(goal==MopperGoal.REFILL){
            MapLocation returnTarget=returnWork!=null?returnWork:ruin;
            if(jalankan_refill(rc,id,returnTarget,UnitType.MOPPER.paintCost+22,mopperReturnTargetById)){
                return;
            }
            goal=MopperGoal.CLEAN_FRONTLINE;
        }
        if(goal==MopperGoal.CLEAN_RUIN&&ruin!=null){
            mopperReturnTargetById.put(id,ruin);
            if(Helper_Mopper.bersihkan_prioritas(rc,ruin,true)){
                return;
            }
        }
        if(goal==MopperGoal.SUPPORT_ARMY||goal==MopperGoal.CLEAN_FRONTLINE){
            if(Helper_Mopper.bersihkan_prioritas(rc,ruin,false)){
                return;
            }
        }
        if(goal==MopperGoal.RETURN_WORK&&returnWork!=null&&gerak_ke_target_anti_osc(rc,returnWork)){
            return;
        }
        boolean ancaman=Helper_Mopper.ada_ancaman(musuh);
        if(Helper_Mopper.heal_rekan_prioritas(rc,posisi,teman,ancaman)){
            return;
        }
        RobotInfo escort=Helper_Mopper.cari_unit_tempur_untuk_dikawal(posisi,teman);
        if(escort!=null&&gerak_ke_target_anti_osc(rc,escort.getLocation())){
            return;
        }
        Helper_Mopper.gerak_acak(rc);
    }

    private static boolean coba_serang_tower(RobotController rc) throws GameActionException{
        if(!rc.isActionReady()){
            return false;
        }
        MapLocation posisi=rc.getLocation();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        MapLocation target=null;
        int best=-10000;
        for(RobotInfo enemy:musuh){
            MapLocation loc=enemy.getLocation();
            if(!rc.canAttack(loc)){
                continue;
            }
            int score=60;
            if(enemy.getType().isTowerType()){
                score+=220;
            }
            score+=Math.max(0,140-enemy.getHealth())*2;
            score-=posisi.distanceSquaredTo(loc)*3;
            if(score>best){
                best=score;
                target=loc;
            }
        }
        if(target!=null&&best>=100&&bisa_serang_sekarang(rc)&&rc.canAttack(target)){
            rc.attack(target);
            return true;
        }
        return false;
    }

    private static boolean bisa_serang_sekarang(RobotController rc){
        return rc.isActionReady()&&rc.getPaint()>=rc.getType().attackCost;
    }

    private static UnitType[] susun_spawn_berdasarkan_score(int soldier,int mopper,int splasher){
        UnitType[] types=new UnitType[]{UnitType.SOLDIER,UnitType.MOPPER,UnitType.SPLASHER};
        int[] scores=new int[]{soldier,mopper,splasher};
        for(int i=0;i<3;i++){
            for(int j=i+1;j<3;j++){
                if(scores[j]>scores[i]){
                    int ts=scores[i];
                    scores[i]=scores[j];
                    scores[j]=ts;
                    UnitType tt=types[i];
                    types[i]=types[j];
                    types[j]=tt;
                }
            }
        }
        if(scores[0]-scores[1]<40){
            if(types[0]==UnitType.SOLDIER&&types[1]==UnitType.MOPPER){
                return new UnitType[]{UnitType.SOLDIER,UnitType.MOPPER,UnitType.SOLDIER,types[2]};
            }
            if(types[0]==UnitType.MOPPER&&types[1]==UnitType.SOLDIER){
                return new UnitType[]{UnitType.MOPPER,UnitType.SOLDIER,UnitType.MOPPER,types[2]};
            }
        }
        return new UnitType[]{types[0],types[1],types[2],types[0]};
    }

    private static boolean coba_spawn_dengan_urutan(RobotController rc,MapLocation posisi,UnitType[] urutan,int reserveChips) throws GameActionException{
        if(urutan==null||urutan.length==0){
            return false;
        }
        boolean triedSoldier=false;
        boolean triedMopper=false;
        boolean triedSplasher=false;
        for(UnitType tipe:urutan){
            if(tipe==UnitType.SOLDIER){
                triedSoldier=true;
            }else if(tipe==UnitType.MOPPER){
                triedMopper=true;
            }else if(tipe==UnitType.SPLASHER){
                triedSplasher=true;
            }
            if(!bisa_biaya_spawn(rc,tipe,reserveChips)){
                continue;
            }
            if(coba_build_unit(rc,posisi,tipe,reserveChips)){
                return true;
            }
        }
        if(!triedSoldier&&bisa_biaya_spawn(rc,UnitType.SOLDIER,reserveChips)&&coba_build_unit(rc,posisi,UnitType.SOLDIER,reserveChips)){
            return true;
        }
        if(!triedMopper&&bisa_biaya_spawn(rc,UnitType.MOPPER,reserveChips)&&coba_build_unit(rc,posisi,UnitType.MOPPER,reserveChips)){
            return true;
        }
        if(!triedSplasher&&bisa_biaya_spawn(rc,UnitType.SPLASHER,reserveChips)&&coba_build_unit(rc,posisi,UnitType.SPLASHER,reserveChips)){
            return true;
        }
        return false;
    }

    private static boolean bisa_biaya_spawn(RobotController rc,UnitType tipe,int reserveChips){
        return rc.getChips()-reserveChips>=tipe.moneyCost&&rc.getPaint()>=tipe.paintCost;
    }

    private static boolean coba_build_unit(RobotController rc,MapLocation posisi,UnitType tipe,int reserveChips) throws GameActionException{
        MapLocation[] kandidat=rc.getAllLocationsWithinRadiusSquared(posisi,GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        if(kandidat==null||kandidat.length==0){
            return false;
        }
        MapInfo[] infosSekitar=rc.senseNearbyMapInfos();
        RobotInfo[] rekanDekat=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation best=null;
        int bestScore=-10000;
        int mapW=rc.getMapWidth();
        int mapH=rc.getMapHeight();
        int laneSpawn=Math.floorMod(rc.getID()+towerSpawnCount,3);
        for(MapLocation loc:kandidat){
            if(loc.equals(posisi)){
                continue;
            }
            if(!rc.canBuildRobot(tipe,loc)){
                continue;
            }
            int open=0;
            int openNear=0;
            int enemy=0;
            int ally=0;
            int wall=0;
            for(MapInfo petak:infosSekitar){
                int d=loc.distanceSquaredTo(petak.getMapLocation());
                if(d>4){
                    continue;
                }
                if(petak.isPassable()&&!petak.isWall()){
                    open+=1;
                    if(d<=2){
                        openNear+=1;
                    }
                }
                if(petak.isWall()){
                    wall+=1;
                }
                if(petak.getPaint().isEnemy()){
                    enemy+=1;
                }else if(petak.getPaint().isAlly()){
                    ally+=1;
                }
            }
            int crowd=0;
            int veryCrowd=0;
            for(RobotInfo info:rekanDekat){
                if(info.getType().isTowerType()){
                    continue;
                }
                int d=loc.distanceSquaredTo(info.getLocation());
                if(d>4){
                    continue;
                }
                crowd+=1;
                if(d<=2){
                    veryCrowd+=1;
                }
            }
            int edge=Math.min(Math.min(loc.x,mapW-1-loc.x),Math.min(loc.y,mapH-1-loc.y));
            int laneBonus;
            if(mapH>=mapW){
                int band=Math.min(2,Math.max(0,(loc.y*3)/Math.max(1,mapH)));
                laneBonus=band==laneSpawn?24:-10;
            }else{
                int band=Math.min(2,Math.max(0,(loc.x*3)/Math.max(1,mapW)));
                laneBonus=band==laneSpawn?24:-10;
            }
            int score=40;
            score+=open*4;
            score+=openNear*6;
            score+=enemy*4;
            score+=ally*2;
            score-=wall*3;
            score-=crowd*10;
            score-=veryCrowd*12;
            score+=edge*5;
            score+=laneBonus;
            if(openNear<=2){
                score-=35;
            }
            if(edge<=1){
                score-=20;
            }
            if(tipe==UnitType.MOPPER){
                score+=enemy*3;
            }else if(tipe==UnitType.SPLASHER){
                score+=open*2;
                if(openNear<3){
                    score-=18;
                }
            }else if(tipe==UnitType.SOLDIER){
                score+=openNear>=4?10:0;
            }
            score+=Math.floorMod(rc.getRoundNum()+loc.x*7+loc.y*11+rc.getID(),7);
            if(score>bestScore){
                bestScore=score;
                best=loc;
            }
        }
        if(best!=null&&rc.canBuildRobot(tipe,best)&&bisa_biaya_spawn(rc,tipe,reserveChips)){
            try{
                rc.buildRobot(tipe,best);
                return true;
            }catch(GameActionException e){
                return false;
            }
        }
        return false;
    }

    private static int hitung_spawn_cell_legal(RobotController rc,MapLocation posisi) throws GameActionException{
        MapLocation[] kandidat=rc.getAllLocationsWithinRadiusSquared(posisi,GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        int count=0;
        if(kandidat==null){
            return 0;
        }
        for(MapLocation loc:kandidat){
            if(loc.equals(posisi)){
                continue;
            }
            if(rc.canSenseLocation(loc)){
                MapInfo info=rc.senseMapInfo(loc);
                if(info.isPassable()&&!info.isWall()&&!rc.canSenseRobotAtLocation(loc)){
                    count+=1;
                }
            }
        }
        return count;
    }

    private static int hitung_enemy_paint_lokal(MapInfo[] infos){
        if(infos==null){
            return 0;
        }
        int count=0;
        for(MapInfo info:infos){
            if(info.getPaint().isEnemy()){
                count+=1;
            }
        }
        return count;
    }

    private static int hitung_empty_paint_lokal(MapInfo[] infos){
        if(infos==null){
            return 0;
        }
        int count=0;
        for(MapInfo info:infos){
            if(info.getPaint()==PaintType.EMPTY){
                count+=1;
            }
        }
        return count;
    }

    private static int hitung_ally_paint_lokal(MapInfo[] infos){
        if(infos==null){
            return 0;
        }
        int count=0;
        for(MapInfo info:infos){
            if(info.getPaint().isAlly()){
                count+=1;
            }
        }
        return count;
    }

    private static int hitung_urgensi_territory(MapInfo[] infos){
        if(infos==null||infos.length==0){
            return 0;
        }
        int enemy=hitung_enemy_paint_lokal(infos);
        int empty=hitung_empty_paint_lokal(infos);
        int ally=hitung_ally_paint_lokal(infos);
        int total=Math.max(1,enemy+empty+ally);
        int allyPct=(ally*100)/total;
        int urgency=enemy*16+empty*9-ally*5;
        if(allyPct<35){
            urgency+=220;
        }else if(allyPct<45){
            urgency+=150;
        }else if(allyPct<55){
            urgency+=95;
        }else if(allyPct<65){
            urgency+=40;
        }
        return Math.max(0,urgency);
    }

    private static boolean mode_territory_utama(RobotController rc,MapInfo[] infos){
        int round=rc.getRoundNum();
        int urgency=hitung_urgensi_territory(infos);
        int ally=hitung_ally_paint_lokal(infos);
        int enemy=hitung_enemy_paint_lokal(infos);
        int empty=hitung_empty_paint_lokal(infos);
        int total=Math.max(1,ally+enemy+empty);
        int allyPct=(ally*100)/total;
        if(round<=320){
            return urgency>=65||allyPct<58;
        }
        if(round<=950){
            return urgency>=45;
        }
        return urgency>=95;
    }

    private static int hitung_ruin_kotor_lokal(RobotController rc) throws GameActionException{
        MapLocation[] ruins=rc.senseNearbyRuins(-1);
        if(ruins==null||ruins.length==0){
            return 0;
        }
        int count=0;
        for(MapLocation ruin:ruins){
            if(hitung_enemy_paint_sekitar_ruin(rc,ruin)>=4){
                count+=1;
            }
        }
        return count;
    }

    private static int hitung_ruin_belum_jadi_lokal(RobotController rc) throws GameActionException{
        MapLocation[] ruins=rc.senseNearbyRuins(-1);
        if(ruins==null||ruins.length==0){
            return 0;
        }
        int count=0;
        for(MapLocation ruin:ruins){
            if(rc.canSenseRobotAtLocation(ruin)){
                RobotInfo robot=rc.senseRobotAtLocation(ruin);
                if(robot!=null&&robot.getType().isTowerType()){
                    continue;
                }
            }
            count+=1;
        }
        return count;
    }

    private static int hitung_reserve_chips_spawn(int round,int enemyPaintLocal,int pendingRuins,int unitTempurDekat){
        if(pendingRuins<=0){
            return 0;
        }
        int reserve=UnitType.LEVEL_ONE_PAINT_TOWER.moneyCost+120;
        if(enemyPaintLocal>=10){
            reserve-=180;
        }
        if(unitTempurDekat<=2){
            reserve-=200;
        }else if(unitTempurDekat<=4){
            reserve-=120;
        }
        if(round>=1200){
            reserve-=240;
        }else if(round>=800){
            reserve-=140;
        }
        return Math.max(UnitType.LEVEL_ONE_PAINT_TOWER.moneyCost,reserve);
    }

    private static int[] hitung_komposisi_tower_ally_lokal(RobotController rc) throws GameActionException{
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        int paint=0;
        int money=0;
        int defense=0;
        if(teman==null){
            return new int[]{0,0,0};
        }
        for(RobotInfo info:teman){
            UnitType tipe=info.getType();
            if(is_paint_tower(tipe)){
                paint+=1;
            }else if(is_money_tower(tipe)){
                money+=1;
            }else if(is_defense_tower(tipe)){
                defense+=1;
            }
        }
        return new int[]{paint,money,defense};
    }

    private static int hitung_crowding_lokal(MapLocation posisi,RobotInfo[] teman){
        if(teman==null){
            return 0;
        }
        int count=0;
        for(RobotInfo info:teman){
            if(info.getType().isTowerType()){
                continue;
            }
            if(posisi.distanceSquaredTo(info.getLocation())<=10){
                count+=1;
            }
        }
        return count;
    }

    private static void invalidate_ruin_scan_cache(){
        ruinScanCacheRound=-1;
        ruinScanCacheRobotId=-1;
        ruinScanCacheLoc=null;
        ruinScanCacheInfos=null;
        ruinScanCacheEnemyPaint=0;
        ruinScanCachePassable=0;
        ruinScanCacheAllyMarks=0;
        ruinScanCacheSisa=-1;
        ruinScanCacheRekan=0;
    }

    private static void ensure_ruin_scan_cache(RobotController rc,MapLocation ruin) throws GameActionException{
        if(ruin==null){
            invalidate_ruin_scan_cache();
            return;
        }
        int round=rc.getRoundNum();
        int id=rc.getID();
        if(ruinScanCacheRound==round&&ruinScanCacheRobotId==id&&ruinScanCacheLoc!=null&&ruinScanCacheLoc.equals(ruin)){
            return;
        }
        MapInfo[] infos=rc.senseNearbyMapInfos(ruin,8);
        int enemy=0;
        int passable=0;
        int allyMarks=0;
        int marked=0;
        int sisa=0;
        for(MapInfo info:infos){
            if(info.getPaint().isEnemy()){
                enemy+=1;
            }
            if(info.isPassable()&&!info.isWall()){
                passable+=1;
            }
            PaintType mark=info.getMark();
            if(mark.isAlly()){
                allyMarks+=1;
                marked+=1;
                if(info.getPaint()!=mark){
                    sisa+=1;
                }
            }
        }
        RobotInfo[] rekan=rc.senseNearbyRobots(ruin,8,rc.getTeam());
        int worker=0;
        for(RobotInfo info:rekan){
            UnitType tipe=info.getType();
            if(tipe==UnitType.SOLDIER||tipe==UnitType.SPLASHER){
                worker+=1;
            }
        }
        ruinScanCacheRound=round;
        ruinScanCacheRobotId=id;
        ruinScanCacheLoc=ruin;
        ruinScanCacheInfos=infos;
        ruinScanCacheEnemyPaint=enemy;
        ruinScanCachePassable=passable;
        ruinScanCacheAllyMarks=allyMarks;
        ruinScanCacheSisa=marked==0?-1:sisa;
        ruinScanCacheRekan=worker;
    }

    private static MapInfo[] infos_ruin_cached(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCacheInfos==null?new MapInfo[0]:ruinScanCacheInfos;
    }

    private static int hitung_enemy_paint_sekitar_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCacheEnemyPaint;
    }

    private static int hitung_passable_sekitar_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCachePassable;
    }

    private static boolean jalankan_goal_ruin(RobotController rc,MapLocation ruin,boolean assistMode) throws GameActionException{
        if(ruin==null){
            return false;
        }
        int id=rc.getID();
        int rekanRuin=hitung_rekan_dekat_ruin(rc,ruin);
        int enemyRuin=hitung_enemy_paint_sekitar_ruin(rc,ruin);
        int sisaRuin=hitung_sisa_pattern_ruin(rc,ruin);
        if(rekanRuin>=4&&!assistMode){
            return false;
        }
        if(enemyRuin>=4&&bersihkan_ruin_dulu(rc,ruin)){
            return true;
        }
        if(assistMode&&rekanRuin>=2&&sisaRuin>4&&bersihkan_ruin_dulu(rc,ruin)){
            return true;
        }

        UnitType targetTower=pilih_target_tower_ruin(rc);
        if(coba_complete_tower_ruin(rc,ruin,targetTower)){
            hapus_ruin_fokus(id);
            ruinNoProgressById.remove(id);
            ruinLastRemainingById.remove(id);
            return true;
        }

        if(!assistMode||rekanRuin<=1||sisaRuin<0){
            kirim_klaim_ruin(rc,ruin);
            coba_mark_pattern_ruin(rc,ruin,targetTower);
        }
        if(coba_cat_pattern_ruin(rc,ruin)){
            if(coba_complete_tower_ruin(rc,ruin,targetTower)){
                hapus_ruin_fokus(id);
                ruinNoProgressById.remove(id);
                ruinLastRemainingById.remove(id);
            }
            return true;
        }

        MapLocation targetKerja=pilih_target_cat_pattern_ruin(rc,ruin);
        if(targetKerja==null){
            targetKerja=pilih_petak_kerja_ruin(rc,rc.getLocation(),ruin);
        }
        boolean moved=gerak_ke_target_anti_osc(rc,targetKerja);
        if(coba_cat_pattern_ruin(rc,ruin)){
            return true;
        }
        if(coba_complete_tower_ruin(rc,ruin,targetTower)){
            hapus_ruin_fokus(id);
            ruinNoProgressById.remove(id);
            ruinLastRemainingById.remove(id);
            return true;
        }
        return moved;
    }

    private static boolean bersihkan_ruin_dulu(RobotController rc,MapLocation ruin) throws GameActionException{
        MapLocation target=pilih_target_enemy_paint_dekat_ruin(rc,ruin);
        if(target==null){
            return false;
        }
        if(bisa_serang_sekarang(rc)&&rc.canAttack(target)){
            rc.attack(target);
            invalidate_ruin_scan_cache();
            return true;
        }
        boolean moved=gerak_ke_target_anti_osc(rc,target);
        if(bisa_serang_sekarang(rc)&&rc.canAttack(target)){
            rc.attack(target);
            invalidate_ruin_scan_cache();
            return true;
        }
        return moved;
    }

    private static MapLocation pilih_target_enemy_paint_dekat_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        if(ruin==null){
            return null;
        }
        MapInfo[] infos=infos_ruin_cached(rc,ruin);
        MapLocation posisi=rc.getLocation();
        MapLocation best=null;
        int bestScore=-10000;
        for(MapInfo info:infos){
            if(!info.getPaint().isEnemy()){
                continue;
            }
            MapLocation loc=info.getMapLocation();
            int cluster=0;
            for(MapInfo p:infos){
                if(loc.distanceSquaredTo(p.getMapLocation())>4){
                    continue;
                }
                if(p.getPaint().isEnemy()){
                    cluster+=1;
                }
            }
            int score=cluster*20-posisi.distanceSquaredTo(loc)*3;
            if(score>bestScore){
                bestScore=score;
                best=loc;
            }
        }
        return best;
    }

    private static boolean coba_ruin_denial(RobotController rc,MapLocation focused,MapLocation[] ruins) throws GameActionException{
        if(!rc.isActionReady()){
            return false;
        }
        if(ruins==null||ruins.length==0){
            return false;
        }
        MapLocation posisi=rc.getLocation();
        MapLocation bestRuin=null;
        int bestScore=-10000;
        for(MapLocation ruin:ruins){
            if(focused!=null&&focused.equals(ruin)){
                continue;
            }
            if(rc.canSenseRobotAtLocation(ruin)){
                RobotInfo robot=rc.senseRobotAtLocation(ruin);
                if(robot!=null&&robot.getType().isTowerType()){
                    continue;
                }
            }
            int mark=hitung_mark_ally_ruin(rc,ruin);
            if(mark>0){
                continue;
            }
            int dist=posisi.distanceSquaredTo(ruin);
            int score=140-dist*2;
            if(score>bestScore){
                bestScore=score;
                bestRuin=ruin;
            }
        }
        if(bestRuin==null||bestScore<90){
            return false;
        }
        MapInfo[] infos=rc.senseNearbyMapInfos(bestRuin,8);
        MapLocation bestTile=null;
        int bestTileScore=-10000;
        for(MapInfo info:infos){
            MapLocation loc=info.getMapLocation();
            if(loc.equals(bestRuin)||info.isWall()||!info.isPassable()){
                continue;
            }
            int score=90-posisi.distanceSquaredTo(loc)*2;
            if(info.getPaint()==PaintType.EMPTY){
                score+=16;
            }else if(info.getPaint().isEnemy()){
                score+=24;
            }else if(info.getPaint().isAlly()){
                score-=30;
            }
            if(score>bestTileScore){
                bestTileScore=score;
                bestTile=loc;
            }
        }
        if(bestTile==null){
            return false;
        }
        if(bisa_serang_sekarang(rc)&&rc.canAttack(bestTile)){
            rc.attack(bestTile);
            return true;
        }
        boolean moved=gerak_ke_target_anti_osc(rc,bestTile);
        if(bisa_serang_sekarang(rc)&&rc.canAttack(bestTile)){
            rc.attack(bestTile);
            return true;
        }
        return moved;
    }

    private static UnitType pilih_target_tower_ruin(RobotController rc) throws GameActionException{
        int round=rc.getRoundNum();
        int chips=rc.getChips();
        int paint=rc.getPaint();
        RobotInfo[] musuh=rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        int[] komposisi=hitung_komposisi_tower_ally_lokal(rc);
        int paintTower=komposisi[0];
        int moneyTower=komposisi[1];
        int defenseTower=komposisi[2];
        int threat=0;
        for(RobotInfo enemy:musuh){
            threat+=enemy.getType().isTowerType()?2:1;
        }
        UnitType openingTarget=target_tower_opening_dua_eco(rc,threat);
        if(openingTarget!=null){
            return openingTarget;
        }
        int ecoTower=paintTower+moneyTower;
        int targetMoneyPct=target_persen_money_tower(round);
        boolean moneyKurang=ecoTower>0&&moneyTower*100<targetMoneyPct*ecoTower;
        boolean paintKurang=ecoTower>0&&paintTower*100<(100-targetMoneyPct)*ecoTower;
        if(threat>=4&&round>=100&&defenseTower<=Math.max(1,ecoTower/3)){
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        boolean paintStarved=paint<UnitType.SOLDIER.paintCost+UnitType.MOPPER.paintCost;
        boolean chipStarved=chips<900;
        if(ecoTower==0){
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if(paintStarved){
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if(chipStarved&&paint>=UnitType.SOLDIER.paintCost*2){
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        if(moneyKurang&&round>=110){
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        if(paintKurang){
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if(chips>=2300&&round>=180&&!paintStarved){
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return nextTowerMoney?UnitType.LEVEL_ONE_MONEY_TOWER:UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    private static boolean coba_complete_tower_ruin(RobotController rc,MapLocation ruin,UnitType prefer) throws GameActionException{
        if(ruin==null){
            return false;
        }
        UnitType[] urutan=urutan_tipe_tower_ruin(rc,prefer);
        for(UnitType tipe:urutan){
            if(!rc.canCompleteTowerPattern(tipe,ruin)){
                continue;
            }
            rc.completeTowerPattern(tipe,ruin);
            catat_tower_ruin_selesai(tipe);
            invalidate_ruin_scan_cache();
            return true;
        }
        return false;
    }

    private static void coba_mark_pattern_ruin(RobotController rc,MapLocation ruin,UnitType prefer) throws GameActionException{
        if(ruin==null||ada_mark_pattern_ruin(rc,ruin)){
            return;
        }
        UnitType[] urutan=urutan_tipe_tower_ruin(rc,prefer);
        for(UnitType tipe:urutan){
            if(!rc.canMarkTowerPattern(tipe,ruin)){
                continue;
            }
            rc.markTowerPattern(tipe,ruin);
            invalidate_ruin_scan_cache();
            return;
        }
    }

    private static UnitType[] urutan_tipe_tower_ruin(RobotController rc,UnitType prefer) throws GameActionException{
        int extraEco=Math.max(0,rc.getNumberTowers()-GameConstants.NUMBER_INITIAL_TOWERS);
        boolean openingEcoPending=rc.getRoundNum()<=420&&extraEco<2;
        if(openingEcoPending){
            if(extraEco==0){
                return new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER};
            }
            if(extraEco==1){
                return new UnitType[]{UnitType.LEVEL_ONE_MONEY_TOWER};
            }
        }
        if(prefer==UnitType.LEVEL_ONE_DEFENSE_TOWER){
            return new UnitType[]{UnitType.LEVEL_ONE_DEFENSE_TOWER,UnitType.LEVEL_ONE_PAINT_TOWER,UnitType.LEVEL_ONE_MONEY_TOWER};
        }
        if(prefer==UnitType.LEVEL_ONE_MONEY_TOWER){
            return new UnitType[]{UnitType.LEVEL_ONE_MONEY_TOWER,UnitType.LEVEL_ONE_PAINT_TOWER,UnitType.LEVEL_ONE_DEFENSE_TOWER};
        }
        return new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER,UnitType.LEVEL_ONE_MONEY_TOWER,UnitType.LEVEL_ONE_DEFENSE_TOWER};
    }

    private static boolean ada_mark_pattern_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        return hitung_mark_ally_ruin(rc,ruin)>0;
    }

    private static MapLocation pilih_target_cat_pattern_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        if(ruin==null){
            return null;
        }
        MapLocation posisi=rc.getLocation();
        MapInfo[] sekitar=infos_ruin_cached(rc,ruin);
        MapLocation best=null;
        int bestScore=-10000;
        for(MapInfo petak:sekitar){
            PaintType mark=petak.getMark();
            if(!mark.isAlly()){
                continue;
            }
            PaintType paint=petak.getPaint();
            if(paint==mark){
                continue;
            }
            MapLocation loc=petak.getMapLocation();
            int score=80-posisi.distanceSquaredTo(loc)*2;
            if(paint.isEnemy()){
                score+=30;
            }else if(paint==PaintType.EMPTY){
                score+=12;
            }
            if(score>bestScore){
                bestScore=score;
                best=loc;
            }
        }
        return best;
    }

    private static boolean coba_cat_pattern_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        if(ruin==null||!rc.isActionReady()){
            return false;
        }
        MapLocation target=pilih_target_cat_pattern_ruin(rc,ruin);
        if(target==null||!bisa_serang_sekarang(rc)||!rc.canAttack(target)){
            return false;
        }
        PaintType mark=rc.senseMapInfo(target).getMark();
        rc.attack(target,mark==PaintType.ALLY_SECONDARY);
        invalidate_ruin_scan_cache();
        return true;
    }

    private static int hitung_rekan_dekat_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCacheRekan;
    }

    private static int hitung_mark_ally_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCacheAllyMarks;
    }

    private static int hitung_sisa_pattern_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        ensure_ruin_scan_cache(rc,ruin);
        return ruinScanCacheSisa;
    }

    private static MapLocation pilih_petak_kerja_ruin(RobotController rc,MapLocation posisi,MapLocation ruin) throws GameActionException{
        MapInfo[] sekitar=infos_ruin_cached(rc,ruin);
        MapLocation previous=previousPosById.get(rc.getID());
        MapLocation best=ruin;
        int bestScore=-10000;
        for(MapInfo petak:sekitar){
            MapLocation loc=petak.getMapLocation();
            if(loc.equals(ruin)||petak.isWall()||!petak.isPassable()){
                continue;
            }
            int score=50-posisi.distanceSquaredTo(loc);
            if(petak.getMark().isAlly()&&petak.getPaint()!=petak.getMark()){
                score+=40;
            }else if(petak.getMark().isAlly()){
                score+=10;
            }
            if(previous!=null&&previous.equals(loc)){
                score-=25;
            }
            if(rc.canSenseRobotAtLocation(loc)){
                RobotInfo robot=rc.senseRobotAtLocation(loc);
                if(robot!=null){
                    score-=22;
                }
            }
            if(score>bestScore){
                bestScore=score;
                best=loc;
            }
        }
        return best;
    }

    private static MapLocation pilih_ruin_fokus(RobotController rc,MapLocation posisi,MapLocation[] ruins) throws GameActionException{
        int id=rc.getID();
        MapLocation focus=ruinFocusById.get(id);
        if(focus!=null){
            if(rc.canSenseRobotAtLocation(focus)){
                RobotInfo robot=rc.senseRobotAtLocation(focus);
                if(robot!=null&&robot.getType().isTowerType()){
                    ruinFocusById.remove(id);
                }else{
                    return focus;
                }
            }else{
                return focus;
            }
        }
        MapLocation baru=pilih_ruin_kosong_terdekat(rc,posisi,ruins);
        if(baru!=null){
            ruinFocusById.put(id,baru);
        }else{
            ruinFocusById.remove(id);
        }
        return baru;
    }

    private static MapLocation pilih_ruin_kosong_terdekat(RobotController rc,MapLocation posisi,MapLocation[] ruins) throws GameActionException{
        if(ruins==null||ruins.length==0){
            return null;
        }
        int id=rc.getID();
        MapLocation prevTarget=lastRuinTargetById.get(id);
        int prevDist=lastRuinDistanceById.getOrDefault(id,Integer.MAX_VALUE);
        int prevStuck=ruinStuckTurnsById.getOrDefault(id,0);
        MapLocation best=null;
        int bestScore=-10000;
        for(MapLocation ruin:ruins){
            if(rc.canSenseRobotAtLocation(ruin)){
                RobotInfo robot=rc.senseRobotAtLocation(ruin);
                if(robot!=null&&robot.getType().isTowerType()){
                    continue;
                }
            }
            int dist=posisi.distanceSquaredTo(ruin);
            int klaim=hitung_klaim_ruin(rc,ruin);
            int rekan=hitung_rekan_dekat_ruin(rc,ruin);
            int mark=hitung_mark_ally_ruin(rc,ruin);
            int sisa=hitung_sisa_pattern_ruin(rc,ruin);
            int score=0;
            score-=dist*2;
            score-=klaim*8;
            int penaltiRekan;
            if(sisa>=0&&sisa<=3){
                penaltiRekan=Math.max(0,rekan-3)*36;
            }else{
                penaltiRekan=Math.max(0,rekan-1)*50;
            }
            score-=penaltiRekan;
            score+=mark*9;
            if(rekan==0){
                score+=20;
            }else if(rekan==1){
                score+=14;
            }
            if(sisa>=0){
                score+=Math.max(0,150-sisa*16);
            }
            if(prevTarget!=null&&prevTarget.equals(ruin)){
                score+=35;
                if(dist>=prevDist){
                    score-=(prevStuck+1)*10;
                }
            }
            if(score>bestScore){
                bestScore=score;
                best=ruin;
            }
        }
        if(best!=null){
            int newDist=posisi.distanceSquaredTo(best);
            int newStuck=0;
            if(prevTarget!=null&&prevTarget.equals(best)&&newDist>=prevDist){
                newStuck=prevStuck+1;
            }
            lastRuinTargetById.put(id,best);
            lastRuinDistanceById.put(id,newDist);
            ruinStuckTurnsById.put(id,newStuck);
        }
        return best;
    }

    private static void hapus_ruin_fokus(int id){
        ruinFocusById.remove(id);
    }

    private static void kirim_klaim_ruin(RobotController rc,MapLocation ruin) throws GameActionException{
        if(ruin==null||!rc.canBroadcastMessage()){
            return;
        }
        rc.broadcastMessage(encodeRuinClaim(ruin));
    }

    private static int hitung_klaim_ruin(RobotController rc,MapLocation ruin){
        if(ruin==null||ruinClaimCacheRound!=rc.getRoundNum()){
            return 0;
        }
        int ruinKey=((ruin.x&0xFFF)<<12)|(ruin.y&0xFFF);
        for(int i=0;i<ruinClaimCacheSize;i++){
            if(ruinClaimCacheKeys[i]==ruinKey){
                return ruinClaimCacheCounts[i];
            }
        }
        return 0;
    }

    private static int encodeRuinClaim(MapLocation ruin){
        return(MSG_RUIN_CLAIM<<24)|((ruin.x&0xFFF)<<12)|(ruin.y&0xFFF);
    }

    private static void lapor_tower_musuh_terlihat(RobotController rc,RobotInfo[] musuh) throws GameActionException{
        if(musuh==null||musuh.length==0){
            return;
        }
        MapLocation posisi=rc.getLocation();
        MapLocation best=null;
        int bestDist=Integer.MAX_VALUE;
        for(RobotInfo info:musuh){
            if(!info.getType().isTowerType()){
                continue;
            }
            int d=posisi.distanceSquaredTo(info.getLocation());
            if(d<bestDist){
                bestDist=d;
                best=info.getLocation();
            }
        }
        if(best==null){
            return;
        }
        sharedEnemyTowerLoc=best;
        sharedEnemyTowerRound=rc.getRoundNum();
        if(rc.canBroadcastMessage()){
            rc.broadcastMessage(encodeEnemyTower(best));
        }
    }

    private static void terima_pesan_tim(RobotController rc){
        int round=rc.getRoundNum();
        if(ruinClaimCacheRound==round){
            return;
        }
        ruinClaimCacheRound=round;
        ruinClaimCacheSize=0;
        ruinClaimSeenPairSize=0;
        int start=Math.max(0,round-3);
        for(int r=start;r<round;r++){
            Message[] messages=rc.readMessages(r);
            for(Message msg:messages){
                int bytes=msg.getBytes();
                int tipe=(bytes>>>24)&0xFF;
                if(tipe==MSG_ENEMY_TOWER){
                    int x=(bytes>>>12)&0xFFF;
                    int y=bytes&0xFFF;
                    if(sharedEnemyTowerLoc==null||r>=sharedEnemyTowerRound){
                        sharedEnemyTowerLoc=new MapLocation(x,y);
                        sharedEnemyTowerRound=r;
                    }
                    continue;
                }
                if(tipe==MSG_RUIN_CLAIM){
                    if(msg.getSenderID()==rc.getID()){
                        continue;
                    }
                    int ruinKey=bytes&0xFFFFFF;
                    int sender=msg.getSenderID();
                    if(ruin_claim_pair_sudah(sender,ruinKey)){
                        continue;
                    }
                    tambah_pasangan_klaim(sender,ruinKey);
                    tambah_klaim_ruin_cache(ruinKey);
                }
            }
        }
        if(sharedEnemyTowerLoc!=null&&round-sharedEnemyTowerRound>55){
            sharedEnemyTowerLoc=null;
            sharedEnemyTowerRound=-1;
        }
    }

    private static boolean ruin_claim_pair_sudah(int sender,int ruinKey){
        for(int i=0;i<ruinClaimSeenPairSize;i++){
            if(ruinClaimSeenPairSenders[i]==sender&&ruinClaimSeenPairKeys[i]==ruinKey){
                return true;
            }
        }
        return false;
    }

    private static void tambah_pasangan_klaim(int sender,int ruinKey){
        if(ruinClaimSeenPairSize>=ruinClaimSeenPairSenders.length){
            int newSize=ruinClaimSeenPairSenders.length*2;
            int[] newSenders=new int[newSize];
            int[] newKeys=new int[newSize];
            System.arraycopy(ruinClaimSeenPairSenders,0,newSenders,0,ruinClaimSeenPairSenders.length);
            System.arraycopy(ruinClaimSeenPairKeys,0,newKeys,0,ruinClaimSeenPairKeys.length);
            ruinClaimSeenPairSenders=newSenders;
            ruinClaimSeenPairKeys=newKeys;
        }
        ruinClaimSeenPairSenders[ruinClaimSeenPairSize]=sender;
        ruinClaimSeenPairKeys[ruinClaimSeenPairSize]=ruinKey;
        ruinClaimSeenPairSize+=1;
    }

    private static void tambah_klaim_ruin_cache(int ruinKey){
        for(int i=0;i<ruinClaimCacheSize;i++){
            if(ruinClaimCacheKeys[i]==ruinKey){
                ruinClaimCacheCounts[i]+=1;
                return;
            }
        }
        if(ruinClaimCacheSize>=ruinClaimCacheKeys.length){
            int newSize=ruinClaimCacheKeys.length*2;
            int[] newKeys=new int[newSize];
            int[] newCounts=new int[newSize];
            System.arraycopy(ruinClaimCacheKeys,0,newKeys,0,ruinClaimCacheKeys.length);
            System.arraycopy(ruinClaimCacheCounts,0,newCounts,0,ruinClaimCacheCounts.length);
            ruinClaimCacheKeys=newKeys;
            ruinClaimCacheCounts=newCounts;
        }
        ruinClaimCacheKeys[ruinClaimCacheSize]=ruinKey;
        ruinClaimCacheCounts[ruinClaimCacheSize]=1;
        ruinClaimCacheSize+=1;
    }

    public static MapLocation target_tower_musuh_terbagi(RobotController rc,MapLocation posisi) throws GameActionException{
        if(sharedEnemyTowerLoc==null){
            return null;
        }
        if(rc.canSenseLocation(sharedEnemyTowerLoc)){
            if(!rc.canSenseRobotAtLocation(sharedEnemyTowerLoc)){
                sharedEnemyTowerLoc=null;
                sharedEnemyTowerRound=-1;
                return null;
            }
            RobotInfo info=rc.senseRobotAtLocation(sharedEnemyTowerLoc);
            if(info==null||!info.getType().isTowerType()||info.getTeam()!=rc.getTeam().opponent()){
                sharedEnemyTowerLoc=null;
                sharedEnemyTowerRound=-1;
                return null;
            }
        }
        return sharedEnemyTowerLoc;
    }

    public static MapLocation target_tower_musuh_prediksi_simetri(RobotController rc){
        MapLocation home=tower_ally_terdekat(rc);
        if(home==null){
            return null;
        }
        int tx=Math.max(0,Math.min(rc.getMapWidth()-1,rc.getMapWidth()-1-home.x));
        int ty=Math.max(0,Math.min(rc.getMapHeight()-1,rc.getMapHeight()-1-home.y));
        return new MapLocation(tx,ty);
    }

    private static int encodeEnemyTower(MapLocation loc){
        return(MSG_ENEMY_TOWER<<24)|((loc.x&0xFFF)<<12)|(loc.y&0xFFF);
    }

    private static MapLocation cari_tower_refill_terdekat(RobotController rc,boolean preferPaint) throws GameActionException{
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation posisi=rc.getLocation();
        MapLocation best=null;
        int bestScore=-10000;
        for(RobotInfo info:teman){
            UnitType tipe=info.getType();
            if(!tipe.isTowerType()){
                continue;
            }
            boolean paintTower=tipe==UnitType.LEVEL_ONE_PAINT_TOWER||tipe==UnitType.LEVEL_TWO_PAINT_TOWER||tipe==UnitType.LEVEL_THREE_PAINT_TOWER;
            int score=0;
            if(preferPaint&&paintTower){
                score+=130;
            }else if(paintTower){
                score+=40;
            }
            score+=info.getPaintAmount()/4;
            score-=posisi.distanceSquaredTo(info.getLocation())*2;
            if(score>bestScore){
                bestScore=score;
                best=info.getLocation();
            }
        }
        return best;
    }

    private static boolean jalankan_refill(RobotController rc,int id,MapLocation returnTarget,int targetPaint,IdLocationTable returnMap) throws GameActionException{
        if(rc.getPaint()>=targetPaint){
            MapLocation back=returnMap.get(id);
            if(back!=null){
                if(rc.getLocation().distanceSquaredTo(back)<=4){
                    returnMap.remove(id);
                }else if(gerak_ke_target_anti_osc(rc,back)){
                    return true;
                }
            }
            return false;
        }
        if(returnTarget!=null){
            returnMap.put(id,returnTarget);
        }
        MapLocation tower=cari_tower_refill_terdekat(rc,true);
        if(tower==null){
            if(rc.isMovementReady()){
                return dorong_ke_frontier(rc);
            }
            return false;
        }
        int need=Math.max(10,Math.min(rc.getType().paintCapacity-rc.getPaint(),targetPaint-rc.getPaint()+20));
        if(rc.canTransferPaint(tower,-need)){
            rc.transferPaint(tower,-need);
            return true;
        }
        boolean moved=gerak_ke_target_anti_osc(rc,tower);
        int need2=Math.max(10,Math.min(rc.getType().paintCapacity-rc.getPaint(),targetPaint-rc.getPaint()+20));
        if(rc.canTransferPaint(tower,-need2)){
            rc.transferPaint(tower,-need2);
            return true;
        }
        return moved;
    }

    private static boolean mundur_ke_tower_terdekat(RobotController rc) throws GameActionException{
        MapLocation tower=cari_tower_refill_terdekat(rc,false);
        if(tower!=null){
            return gerak_ke_target_anti_osc(rc,tower);
        }
        return dorong_ke_frontier(rc);
    }

    public static boolean gerak_ke_target_anti_osc(RobotController rc,MapLocation target) throws GameActionException{
        if(target==null||!rc.isMovementReady()){
            return false;
        }
        int noProgress=updateNoProgressToTarget(rc,target);
        if((getNoMoveTurns(rc)>=2||noProgress>=2)&&gerak_pintar_saat_stagnan(rc,target)){
            return true;
        }
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] rekan=rc.senseNearbyRobots(-1,rc.getTeam());
        Direction utama=posisi.directionTo(target);
        if(utama==Direction.CENTER){
            return false;
        }
        Direction[] prioritas=new Direction[]{
            utama,
            utama.rotateLeft(),
            utama.rotateRight(),
            utama.rotateLeft().rotateLeft(),
            utama.rotateRight().rotateRight(),
            utama.opposite().rotateLeft(),
            utama.opposite().rotateRight()
        };
        MapLocation previous=previousPosById.get(rc.getID());
        Direction fallback=null;
        int bestScore=-10000;
        Direction best=null;
        int currentDist=posisi.distanceSquaredTo(target);
        for(Direction dir:prioritas){
            if(!rc.canMove(dir)){
                continue;
            }
            MapLocation next=posisi.add(dir);
            int score=(currentDist-next.distanceSquaredTo(target))*34;
            score-=next.distanceSquaredTo(target)*2;
            score+=bonus_lane_lokasi(rc,next)/3;
            score+=bonus_dorong_depan(rc,next);
            score-=hitung_penalti_kerumunan(next,rekan)*18;
            score-=hitung_penalti_dekat_tower(next,rekan)*22;
            score+=hitung_bonus_cat(next,infos);
            if(previous!=null&&previous.equals(next)){
                score-=90;
            }
            if(fallback==null){
                fallback=dir;
            }
            if(score>bestScore){
                bestScore=score;
                best=dir;
            }
        }
        if(best!=null&&rc.canMove(best)){
            rc.move(best);
            return true;
        }
        if(fallback!=null&&rc.canMove(fallback)){
            rc.move(fallback);
            return true;
        }
        return false;
    }

    public static boolean dorong_ke_frontier(RobotController rc) throws GameActionException{
        MapLocation target=target_depan_global(rc);
        if(target!=null&&gerak_ke_target_anti_osc(rc,target)){
            return true;
        }
        return gerak_netral_anti_osc(rc);
    }

    public static boolean gerak_netral_anti_osc(RobotController rc) throws GameActionException{
        if(!rc.isMovementReady()){
            return false;
        }
        if(getNoMoveTurns(rc)>=2&&gerak_pintar_saat_stagnan(rc,null)){
            return true;
        }
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] rekan=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation previous=previousPosById.get(rc.getID());
        Direction best=null;
        int bestScore=-10000;
        Direction fallback=null;
        for(Direction dir:directions){
            if(!rc.canMove(dir)){
                continue;
            }
            MapLocation next=posisi.add(dir);
            if(fallback==null){
                fallback=dir;
            }
            int score=skor_arah_eksplorasi(rc,dir);
            score+=bonus_lane_lokasi(rc,next)/3;
            score+=bonus_dorong_depan(rc,next);
            score-=hitung_penalti_kerumunan(next,rekan)*20;
            score-=hitung_penalti_dekat_tower(next,rekan)*26;
            score+=hitung_bonus_cat(next,infos)*2;
            if(previous!=null&&previous.equals(next)){
                score-=80;
            }
            if(score>bestScore){
                bestScore=score;
                best=dir;
            }
        }
        if(best!=null&&rc.canMove(best)){
            rc.move(best);
            return true;
        }
        if(fallback!=null&&rc.canMove(fallback)){
            rc.move(fallback);
            return true;
        }
        return false;
    }

    private static boolean gerak_pintar_saat_stagnan(RobotController rc,MapLocation target) throws GameActionException{
        if(!rc.isMovementReady()){
            return false;
        }
        MapLocation posisi=rc.getLocation();
        MapInfo[] infos=rc.senseNearbyMapInfos();
        RobotInfo[] rekan=rc.senseNearbyRobots(-1,rc.getTeam());
        MapLocation previous=previousPosById.get(rc.getID());
        int awal=target==null?0:posisi.distanceSquaredTo(target);
        Direction prefer=directions[Math.floorMod(rc.getID(),directions.length)];
        Direction best=null;
        int bestScore=-10000;
        for(Direction dir:directions){
            if(!rc.canMove(dir)){
                continue;
            }
            MapLocation next=posisi.add(dir);
            int score=0;
            if(target!=null){
                int baru=next.distanceSquaredTo(target);
                score+=(awal-baru)*35;
                score-=baru*2;
            }
            score+=(4-jarak_arah(dir,prefer))*3;
            score+=bonus_lane_lokasi(rc,next)/4;
            score+=bonus_dorong_depan(rc,next);
            score-=hitung_penalti_kerumunan(next,rekan)*22;
            score-=hitung_penalti_dekat_tower(next,rekan)*26;
            score+=hitung_bonus_cat(next,infos)*2;
            if(previous!=null&&previous.equals(next)){
                score-=90;
            }
            score+=Math.floorMod(rc.getRoundNum()+rc.getID()+dir.ordinal(),5);
            if(score>bestScore){
                bestScore=score;
                best=dir;
            }
        }
        if(best!=null&&rc.canMove(best)){
            rc.move(best);
            return true;
        }
        return false;
    }

    public static int skor_arah_eksplorasi(RobotController rc,Direction dir){
        Direction prefer=directions[Math.floorMod(rc.getID(),directions.length)];
        int diff=jarak_arah(dir,prefer);
        int score=(4-diff)*2+Math.floorMod(rc.getRoundNum()+dir.ordinal()+rc.getID(),5);
        MapLocation next=rc.getLocation().add(dir);
        score+=bonus_lane_lokasi(rc,next)/8;
        score+=bonus_dorong_depan(rc,next);
        return score;
    }

    private static int hitung_bonus_cat(MapLocation center,MapInfo[] infos){
        int enemy=0;
        int empty=0;
        int ally=0;
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
                ally+=1;
            }
        }
        int score=enemy*4+empty*3-ally*4;
        if(enemy+empty>=ally+2){
            score+=enemy*2+empty;
        }
        return score;
    }

    private static int hitung_penalti_kerumunan(MapLocation center,RobotInfo[] rekan){
        int crowd=0;
        for(RobotInfo info:rekan){
            if(info.getType().isTowerType()){
                continue;
            }
            int d=center.distanceSquaredTo(info.getLocation());
            if(d<=1){
                crowd+=4;
            }else if(d<=2){
                crowd+=3;
            }else if(d<=4){
                crowd+=2;
            }else{
                crowd+=1;
            }
        }
        return crowd;
    }

    private static int hitung_penalti_dekat_tower(MapLocation center,RobotInfo[] rekan){
        int penalti=0;
        for(RobotInfo info:rekan){
            if(!info.getType().isTowerType()){
                continue;
            }
            int d=center.distanceSquaredTo(info.getLocation());
            if(d<=2){
                penalti+=8;
            }else if(d<=8){
                penalti+=4;
            }else if(d<=18){
                penalti+=1;
            }
        }
        return penalti;
    }

    public static int bonus_dorong_depan(RobotController rc,MapLocation next){
        MapLocation front=target_depan_global(rc);
        int score=0;
        if(front!=null){
            MapLocation now=rc.getLocation();
            score+=(now.distanceSquaredTo(front)-next.distanceSquaredTo(front))*8;
        }
        MapLocation home=tower_ally_terdekat(rc);
        if(home!=null){
            MapLocation now=rc.getLocation();
            score+=(next.distanceSquaredTo(home)-now.distanceSquaredTo(home))*4;
        }
        return score;
    }

    public static MapLocation target_depan_global(RobotController rc){
        int mapW=rc.getMapWidth();
        int mapH=rc.getMapHeight();
        MapLocation home=tower_ally_terdekat(rc);
        int lane=getLaneBias(rc.getID());
        int laneX=Math.min(mapW-2,Math.max(1,((lane*2+1)*mapW)/6));
        int laneY=Math.min(mapH-2,Math.max(1,((lane*2+1)*mapH)/6));
        if(home!=null){
            int tx=Math.min(mapW-2,Math.max(1,mapW-1-home.x));
            int ty=Math.min(mapH-2,Math.max(1,mapH-1-home.y));
            if(mapH>=mapW){
                tx=laneX;
            }else{
                ty=laneY;
            }
            return new MapLocation(tx,ty);
        }
        if(mapH>=mapW){
            return new MapLocation(laneX,mapH/2);
        }
        return new MapLocation(mapW/2,laneY);
    }

    private static MapLocation tower_ally_terdekat(RobotController rc){
        try{
            RobotInfo[] rekan=rc.senseNearbyRobots(-1,rc.getTeam());
            MapLocation now=rc.getLocation();
            MapLocation best=null;
            int bestDist=Integer.MAX_VALUE;
            for(RobotInfo info:rekan){
                if(!info.getType().isTowerType()){
                    continue;
                }
                int d=now.distanceSquaredTo(info.getLocation());
                if(d<bestDist){
                    bestDist=d;
                    best=info.getLocation();
                }
            }
            return best;
        }catch(GameActionException e){
            return null;
        }
    }

    private static int bonus_lane_lokasi(RobotController rc,MapLocation lokasi){
        int lane=getLaneBias(rc.getID());
        int mapW=rc.getMapWidth();
        int mapH=rc.getMapHeight();
        if(mapH>=mapW){
            int band=Math.min(2,Math.max(0,(lokasi.y*3)/Math.max(1,mapH)));
            return band==lane?12:-4;
        }
        int band=Math.min(2,Math.max(0,(lokasi.x*3)/Math.max(1,mapW)));
        return band==lane?12:-4;
    }

    private static int getLaneBias(int id){
        if(laneBiasById.contains(id)){
            return laneBiasById.get(id);
        }
        int lane=Math.floorMod(id,3);
        laneBiasById.put(id,lane);
        return lane;
    }

    private static int jarak_arah(Direction a,Direction b){
        if(a==Direction.CENTER||b==Direction.CENTER){
            return 4;
        }
        int ia=0;
        int ib=0;
        for(int i=0;i<directions.length;i++){
            if(directions[i]==a){
                ia=i;
            }
            if(directions[i]==b){
                ib=i;
            }
        }
        int d=Math.abs(ia-ib);
        return Math.min(d,directions.length-d);
    }

    private static int getNoMoveTurns(RobotController rc){
        return noMoveTurnsById.getOrDefault(rc.getID(),0);
    }

    private static int updateNoProgressToTarget(RobotController rc,MapLocation target){
        int id=rc.getID();
        int key=((target.x&0xFFFF)<<16)|(target.y&0xFFFF);
        int dist=rc.getLocation().distanceSquaredTo(target);
        int prevKey=lastMoveTargetById.getOrDefault(id,Integer.MIN_VALUE);
        int prevDist=lastMoveTargetDistanceById.getOrDefault(id,Integer.MAX_VALUE);
        int noProgress=0;
        if(prevKey==key&&dist>=prevDist){
            noProgress=noProgressToTargetTurnsById.getOrDefault(id,0)+1;
        }
        lastMoveTargetById.put(id,key);
        lastMoveTargetDistanceById.put(id,dist);
        noProgressToTargetTurnsById.put(id,noProgress);
        return noProgress;
    }

    private static void updatePositionMemory(RobotController rc){
        int id=rc.getID();
        MapLocation now=rc.getLocation();
        MapLocation prevNow=currentPosById.get(id);
        if(prevNow==null){
            currentPosById.put(id,now);
            noMoveTurnsById.put(id,0);
            return;
        }
        if(prevNow.equals(now)){
            noMoveTurnsById.put(id,noMoveTurnsById.getOrDefault(id,0)+1);
        }else{
            previousPosById.put(id,prevNow);
            currentPosById.put(id,now);
            noMoveTurnsById.put(id,0);
        }
    }

    private static void sentuh_dan_bersihkan_state(RobotController rc){
        int round=rc.getRoundNum();
        int id=rc.getID();
        lastSeenRoundById.put(id,round);
        if(lastStateCleanupRound==round||round%50!=0){
            return;
        }
        lastStateCleanupRound=round;
        int cutoff=round-120;
        int[] staleIds=new int[16];
        int staleCount=0;
        for(int key=0;key<lastSeenRoundById.capacity();key++){
            if(!lastSeenRoundById.contains(key)){
                continue;
            }
            if(lastSeenRoundById.get(key)>=cutoff){
                continue;
            }
            if(staleCount==staleIds.length){
                int[] grown=new int[staleIds.length*2];
                System.arraycopy(staleIds,0,grown,0,staleIds.length);
                staleIds=grown;
            }
            staleIds[staleCount++]=key;
        }
        for(int i=0;i<staleCount;i++){
            int staleId=staleIds[i];
            hapus_state_robot(staleId);
            lastSeenRoundById.remove(staleId);
        }
    }

    private static void hapus_state_robot(int id){
        currentPosById.remove(id);
        previousPosById.remove(id);
        noMoveTurnsById.remove(id);
        lastMoveTargetById.remove(id);
        lastMoveTargetDistanceById.remove(id);
        noProgressToTargetTurnsById.remove(id);
        ruinFocusById.remove(id);
        soldierReturnTargetById.remove(id);
        splasherReturnTargetById.remove(id);
        mopperReturnTargetById.remove(id);
        ruinNoProgressById.remove(id);
        ruinLastRemainingById.remove(id);
        lastRuinTargetById.remove(id);
        lastRuinDistanceById.remove(id);
        ruinStuckTurnsById.remove(id);
        laneBiasById.remove(id);
        towerSpawnFailStreakById.remove(id);
    }

    private static boolean layak_upgrade_setelah_gagal_spawn(int round,int chips,int paint,int unitTempurDekat){
        if(round<220||unitTempurDekat<4){
            return false;
        }
        if(chips<1800){
            return false;
        }
        if(paint<UnitType.MOPPER.paintCost){
            return chips>=2400||(round>=420&&chips>=2100);
        }
        if(chips>=2800){
            return true;
        }
        return round>=420&&chips>=2100&&unitTempurDekat>=6;
    }

    private static boolean coba_upgrade_tower_prioritas(RobotController rc) throws GameActionException{
        RobotInfo[] teman=rc.senseNearbyRobots(-1,rc.getTeam());
        int[] komposisi=hitung_komposisi_tower_ally_lokal(rc);
        int paintTower=komposisi[0];
        int moneyTower=komposisi[1];
        int ecoTower=paintTower+moneyTower;
        int targetMoneyPct=target_persen_money_tower(rc.getRoundNum());
        boolean targetMoney=towerUpgradeStep%3==2;
        if(ecoTower>0){
            if(moneyTower*100<targetMoneyPct*ecoTower){
                targetMoney=true;
            }else if(paintTower*100<(100-targetMoneyPct)*ecoTower){
                targetMoney=false;
            }
        }
        if(rc.getChips()<1400){
            targetMoney=true;
        }
        if(rc.getPaint()<UnitType.SOLDIER.paintCost){
            targetMoney=false;
        }
        MapLocation loc=pilih_lokasi_upgrade(rc,teman,targetMoney,true);
        if(loc==null){
            loc=pilih_lokasi_upgrade(rc,teman,targetMoney,false);
        }
        if(loc==null){
            return false;
        }
        rc.upgradeTower(loc);
        towerUpgradeStep=(towerUpgradeStep+1)%4;
        return true;
    }

    private static MapLocation pilih_lokasi_upgrade(RobotController rc,RobotInfo[] teman,boolean targetMoney,boolean strict){
        if(teman==null){
            return null;
        }
        MapLocation posisi=rc.getLocation();
        MapLocation best=null;
        int bestPriority=-10000;
        int bestDist=Integer.MAX_VALUE;
        for(RobotInfo rekan:teman){
            UnitType tipe=rekan.getType();
            if(!tipe.isTowerType()){
                continue;
            }
            MapLocation loc=rekan.getLocation();
            if(!rc.canUpgradeTower(loc)){
                continue;
            }
            int priority=prioritas_upgrade_tower(tipe,targetMoney,strict);
            if(priority<Integer.MIN_VALUE/2){
                continue;
            }
            int dist=posisi.distanceSquaredTo(loc);
            if(priority>bestPriority||(priority==bestPriority&&dist<bestDist)){
                bestPriority=priority;
                bestDist=dist;
                best=loc;
            }
        }
        return best;
    }

    private static int prioritas_upgrade_tower(UnitType tipe,boolean targetMoney,boolean strict){
        boolean isMoney=is_money_tower(tipe);
        boolean isPaint=is_paint_tower(tipe);
        boolean isDefense=is_defense_tower(tipe);
        if(strict){
            if(targetMoney&&!isMoney){
                return Integer.MIN_VALUE;
            }
            if(!targetMoney&&isMoney){
                return Integer.MIN_VALUE;
            }
        }
        if(targetMoney){
            if(isMoney){
                return 5;
            }
            if(isPaint){
                return 2;
            }
            if(isDefense){
                return 1;
            }
            return 0;
        }
        if(isPaint){
            return 5;
        }
        if(isDefense){
            return 4;
        }
        if(isMoney){
            return 3;
        }
        return 0;
    }

    private static final class IdIntTable{
        private boolean[] present;
        private int[] values;

        private IdIntTable(){
            this.present=new boolean[64];
            this.values=new int[64];
        }

        int capacity(){
            return present.length;
        }

        boolean contains(int id){
            return id>=0&&id<present.length&&present[id];
        }

        int get(int id){
            if(!contains(id)){
                return 0;
            }
            return values[id];
        }

        int getOrDefault(int id,int defaultValue){
            if(!contains(id)){
                return defaultValue;
            }
            return values[id];
        }

        void put(int id,int value){
            if(id<0){
                return;
            }
            ensureCapacity(id);
            present[id]=true;
            values[id]=value;
        }

        void remove(int id){
            if(id<0||id>=present.length){
                return;
            }
            present[id]=false;
        }

        private void ensureCapacity(int id){
            if(id<present.length){
                return;
            }
            int newSize=present.length;
            while(newSize<=id){
                newSize*=2;
            }
            boolean[] newPresent=new boolean[newSize];
            int[] newValues=new int[newSize];
            System.arraycopy(present,0,newPresent,0,present.length);
            System.arraycopy(values,0,newValues,0,values.length);
            present=newPresent;
            values=newValues;
        }
    }

    private static final class IdLocationTable{
        private boolean[] present;
        private MapLocation[] values;

        private IdLocationTable(){
            this.present=new boolean[64];
            this.values=new MapLocation[64];
        }

        boolean contains(int id){
            return id>=0&&id<present.length&&present[id];
        }

        MapLocation get(int id){
            if(!contains(id)){
                return null;
            }
            return values[id];
        }

        void put(int id,MapLocation value){
            if(id<0){
                return;
            }
            ensureCapacity(id);
            present[id]=true;
            values[id]=value;
        }

        void remove(int id){
            if(id<0||id>=present.length){
                return;
            }
            present[id]=false;
            values[id]=null;
        }

        private void ensureCapacity(int id){
            if(id<present.length){
                return;
            }
            int newSize=present.length;
            while(newSize<=id){
                newSize*=2;
            }
            boolean[] newPresent=new boolean[newSize];
            MapLocation[] newValues=new MapLocation[newSize];
            System.arraycopy(present,0,newPresent,0,present.length);
            System.arraycopy(values,0,newValues,0,values.length);
            present=newPresent;
            values=newValues;
        }
    }
}
