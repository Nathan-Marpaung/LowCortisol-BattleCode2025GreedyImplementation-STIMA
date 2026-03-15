package Richardv2;

import battlecode.common.*;

public class RunSoldier {

    public static void run() throws GameActionException {
        RobotController rc = State.rc;
        MapLocation myLoc = State.myLoc;
        updateLastPaintTower(rc);

        boolean isLateExplorer = (rc.getID() % 3 == 0) && (rc.getRoundNum() >= 1000);
        if (isLateExplorer
                && State.soldierState != State.SoldierState.LOW_PAINT
                && State.soldierState != State.SoldierState.LATE_EXPLORE) {
            State.soldierState = State.SoldierState.LATE_EXPLORE;
            State.lateExploreTarget = pickEdgeTarget(rc);
            State.resetPathfinding();
        }

        if (State.botRoundNum == 1) {
            paintIfPossible(rc, myLoc);
            State.wanderTarget = new MapLocation(
                    rc.getMapWidth() - myLoc.x,
                    rc.getMapHeight() - myLoc.y);
        }

        if (State.wanderTarget != null && myLoc.distanceSquaredTo(State.wanderTarget) <= 8) {
            State.wanderTarget = new MapLocation(State.rng.nextInt(rc.getMapWidth()),
                    State.rng.nextInt(rc.getMapHeight()));
        }

        // Transisi state
        if (rc.getPaint() < State.REFUEL_THRESHOLD_SOLDIER
                && (rc.getMoney() < State.LOW_PAINT_MONEY_THRESHOLD
                        || State.soldierState == State.SoldierState.FILLING_RUIN)) {
            if (State.soldierState != State.SoldierState.LOW_PAINT) {
                State.storedState = State.soldierState;
                State.soldierState = State.SoldierState.LOW_PAINT;
                State.resetPathfinding();
            }
        }
        // Keluar dari low paint
        else if (State.soldierState == State.SoldierState.LOW_PAINT
                && rc.getPaint() >= State.REFUEL_THRESHOLD_SOLDIER) {
            State.soldierState = State.storedState;
            State.resetPathfinding();
        }
        // Cari ruin yang bisa diisi
        else if (State.soldierState == State.SoldierState.EXPLORE) {
            MapLocation bestRuin = findBestRuin(rc, myLoc);
            if (bestRuin != null) {
                State.ruinToFill = bestRuin;
                State.soldierState = State.SoldierState.FILLING_RUIN;
                State.resetPathfinding();
            }
        }
        // Batalkan ruin yang sudah tidak valid
        else if (State.soldierState == State.SoldierState.FILLING_RUIN
                && State.ruinToFill != null) {
            if (!canBuildTower(rc, State.ruinToFill)) {
                State.ruinToFill = null;
                State.fillTowerType = null;
                State.soldierState = State.SoldierState.EXPLORE;
                State.resetPathfinding();
            }
        }

        // state aktif
        switch (State.soldierState) {

            case LOW_PAINT: {
                rc.setIndicatorString("SOLDIER LOW_PAINT");
                lowPaintBehavior(rc);
                break;
            }

            case FILLING_RUIN: {
                rc.setIndicatorString("SOLDIER FILLING_RUIN " + State.ruinToFill);
                fillInRuin(rc, State.ruinToFill);
                break;
            }

            case EXPLORE:
            default: {
                rc.setIndicatorString("SOLDIER EXPLORE -> " + State.wanderTarget);
                greedyPaint(rc, myLoc);
                Direction dir = greedyExplore(rc, myLoc);
                if (dir != null && rc.canMove(dir)) {
                    rc.move(dir);
                    paintIfPossible(rc, rc.getLocation());
                }
                break;
            }
        }
    }

    // Pilih ruin terbaik
    private static MapLocation findBestRuin(RobotController rc, MapLocation myLoc)
            throws GameActionException {

        MapLocation bestRuin = null;
        int bestScore = Integer.MIN_VALUE;
        int cx = rc.getMapWidth() / 2;
        int cy = rc.getMapHeight() / 2;
        MapLocation center = new MapLocation(cx, cy);

        for (MapLocation ruinLoc : rc.senseNearbyRuins(-1)) {
            if (!canBuildTower(rc, ruinLoc))
                continue;

            int distSq = myLoc.distanceSquaredTo(ruinLoc);
            int score = 1000 - distSq;

            UnitType guess = guessTowerType(rc, ruinLoc);
            boolean[][] pattern = getPattern(guess);
            if (pattern != null) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        MapLocation loc = ruinLoc.translate(dx, dy);
                        if (!rc.canSenseLocation(loc))
                            continue;
                        MapInfo info = rc.senseMapInfo(loc);
                        boolean wantSecondary = pattern[dx + 2][dy + 2];
                        PaintType current = info.getPaint();
                        boolean isCorrect = wantSecondary ? current == PaintType.ALLY_SECONDARY
                                : current == PaintType.ALLY_PRIMARY;
                        if (isCorrect) {
                            score += State.RUIN_TILES_DONE_BONUS;
                        }
                    }
                }
            }
            if (ruinLoc.distanceSquaredTo(center) < (rc.getMapWidth() * rc.getMapWidth()) / 4) {
                score += State.RUIN_CENTER_BONUS;
            }
            RobotInfo[] nearRuin = rc.senseNearbyRobots(ruinLoc, 4, rc.getTeam());
            for (RobotInfo ally : nearRuin) {
                if (ally.getType().isRobotType()) {
                    score -= State.RUIN_ALLY_NEARBY_PENALTY;
                    break;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestRuin = ruinLoc;
            }
        }
        return bestRuin;
    }

    // Cek ruin masih bisa dibangun
    private static boolean canBuildTower(RobotController rc, MapLocation ruinLoc)
            throws GameActionException {
        if (!rc.canSenseLocation(ruinLoc))
            return true;
        if (!rc.senseMapInfo(ruinLoc).hasRuin())
            return false;
        if (rc.canSenseRobotAtLocation(ruinLoc)) {
            RobotInfo bot = rc.senseRobotAtLocation(ruinLoc);
            if (bot != null)
                return false;
        }
        return true;
    }

    // Isi pola ruin
    private static void fillInRuin(RobotController rc, MapLocation ruinLoc)
            throws GameActionException {

        if (!rc.canSenseLocation(ruinLoc)) {
            Direction dir = Pathfind.pathfind(rc, ruinLoc);
            if (dir != null && rc.canMove(dir))
                rc.move(dir);
            return;
        }

        if (State.fillTowerType == null) {
            State.fillTowerType = guessTowerType(rc, ruinLoc);
        }
        boolean[][] boolPattern = getPattern(State.fillTowerType);

        if (ruinLoc.isWithinDistanceSquared(State.myLoc, 8)) {
            if (rc.canMarkTowerPattern(State.fillTowerType, ruinLoc)) {
                rc.markTowerPattern(State.fillTowerType, ruinLoc);
            }
        }

        if (rc.getActionCooldownTurns() < 10 && boolPattern != null) {
            paintOnePatternTile(rc, ruinLoc, boolPattern);
        }

        tryCompleteRuin(rc, ruinLoc);
        if (State.soldierState != State.SoldierState.FILLING_RUIN)
            return;

        MapLocation target = findUnpaintedPatternTile(rc, ruinLoc, boolPattern);
        if (target == null)
            target = ruinLoc;
        Direction dir = Pathfind.pathfind(rc, target);
        if (dir != null && rc.canMove(dir)) {
            rc.move(dir);
        }

        tryCompleteRuin(rc, ruinLoc);
    }

    private static void paintOnePatternTile(RobotController rc, MapLocation ruinLoc,
            boolean[][] pattern) throws GameActionException {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = ruinLoc.translate(dx, dy);
                if (!rc.canAttack(loc))
                    continue;
                if (!rc.canSenseLocation(loc))
                    continue;
                MapInfo info = rc.senseMapInfo(loc);
                if (!info.isPassable())
                    continue;

                if (dx == 0 && dy == 0) continue;
                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expected = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                PaintType current = info.getPaint();

                if (current == expected)
                    continue;
                if (current.isEnemy())
                    continue;

                rc.attack(loc, wantSecondary);
                return;
            }
        }
    }

    private static MapLocation findUnpaintedPatternTile(RobotController rc, MapLocation ruinLoc,
            boolean[][] pattern) throws GameActionException {
        MapLocation myLoc = State.myLoc;
        MapLocation nearestUnpainted = null;
        int nearestDist = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation loc = ruinLoc.translate(dx, dy);
                if (!rc.canSenseLocation(loc))
                    continue;
                MapInfo info = rc.senseMapInfo(loc);
                if (!info.isPassable())
                    continue;

                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expected = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                PaintType current = info.getPaint();
                if (current == expected)
                    continue;
                if (current.isEnemy())
                    continue;

                int dist = myLoc.distanceSquaredTo(loc);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestUnpainted = loc;
                }
            }
        }
        return nearestUnpainted;
    }

    private static void tryCompleteRuin(RobotController rc, MapLocation ruinLoc)
            throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLoc)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLoc);
            State.ruinToFill = null;
            State.fillTowerType = null;
            State.soldierState = State.SoldierState.LOW_PAINT;
            State.storedState = State.SoldierState.EXPLORE;
            State.resetPathfinding();
            return;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
            State.ruinToFill = null;
            State.fillTowerType = null;
            State.soldierState = State.SoldierState.LOW_PAINT;
            State.storedState = State.SoldierState.EXPLORE;
            State.resetPathfinding();
            return;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLoc)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLoc);
            State.ruinToFill = null;
            State.fillTowerType = null;
            State.soldierState = State.SoldierState.LOW_PAINT;
            State.storedState = State.SoldierState.EXPLORE;
            State.resetPathfinding();
        }
    }

    // Cat tile terbaik
    private static void greedyPaint(RobotController rc, MapLocation myLoc)
            throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10)
            return;
        if (rc.getPaint() <= State.LOW_PAINT_THRESHOLD)
            return;

        MapLocation bestTile = null;
        int bestScore = 0;
        boolean bestSecondary = false;

        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc))
                continue;
            if (!tile.isPassable())
                continue;

            PaintType paint = tile.getPaint();
            int score = 0;

            if (paint == PaintType.EMPTY) {
                score += State.TILE_EMPTY_BASIC;

                for (MapInfo nearby : rc.senseNearbyMapInfos(loc, 8)) {
                    if (nearby.hasRuin() && !rc.canSenseRobotAtLocation(nearby.getMapLocation())) {
                        score += State.TILE_NEAR_RUIN;
                        break;
                    }
                }

                boolean isPrimary = isResourcePatternPrimary(rc, loc);
                if (isPrimary)
                    score += State.TILE_SRP_BONUS;

                if (score > bestScore) {
                    bestScore = score;
                    bestTile = loc;
                    bestSecondary = !isPrimary;
                }

            } else if (paint.isAlly()) {
                boolean expectedSecondary = !isResourcePatternPrimary(rc, loc);
                boolean isSecondary = (paint == PaintType.ALLY_SECONDARY);
                if (expectedSecondary != isSecondary && tile.getMark() == PaintType.EMPTY) {
                    score = State.TILE_MISMATCH_FIX;
                    if (score > bestScore) {
                        bestScore = score;
                        bestTile = loc;
                        bestSecondary = expectedSecondary;
                    }
                }
            }
        }

        if (bestTile != null && rc.canAttack(bestTile)) {
            rc.attack(bestTile, bestSecondary);
        }
    }

    // Cek pola primer resource
    private static boolean isResourcePatternPrimary(RobotController rc, MapLocation loc) {
        int x = ((loc.x % 4) + 4) % 4;
        int y = ((loc.y % 4) + 4) % 4;
        return !((x == 0 && y == 0) || (x == 2 && y == 0) || (x == 0 && y == 2) || (x == 2 && y == 2));
    }

    private static void paintIfPossible(RobotController rc, MapLocation loc)
            throws GameActionException {
        if (!rc.canPaint(loc))
            return;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.getPaint() == PaintType.EMPTY) {
            boolean secondary = !isResourcePatternPrimary(rc, loc);
            rc.attack(loc, secondary);
        }
    }

    // Tentukan arah explore
    private static Direction greedyExplore(RobotController rc, MapLocation myLoc)
            throws GameActionException {

        if (State.intermediateTarget != null) {
            int breakScore = 0;
            for (int[] off : State.EXPLORE_OFFSETS) {
                MapLocation check = new MapLocation(myLoc.x + off[0], myLoc.y + off[1]);
                if (rc.onTheMap(check)) {
                    breakScore = Math.max(breakScore, scoreTile(rc, check));
                }
            }
            if (breakScore > 45) {
                State.intermediateTarget = null;
                State.resetPathfinding();
            }
        }

        if (State.intermediateTarget == null
                || myLoc.equals(State.intermediateTarget)
                || myLoc.isWithinDistanceSquared(State.intermediateTarget, 2)) {

            if (myLoc.equals(State.intermediateTarget)) {
                State.resetPathfinding();
            }

            int cumSum = 0;
            int minScore = -1;
            int[] weighted = new int[8];
            int curDist = (State.wanderTarget != null)
                    ? myLoc.distanceSquaredTo(State.wanderTarget)
                    : Integer.MAX_VALUE;

            for (int i = 0; i < 8; i++) {
                int score = 0;
                int[] off = State.EXPLORE_OFFSETS[i];
                MapLocation candidate = myLoc.translate(off[0], off[1]);
                if (rc.onTheMap(candidate)) {
                    score = scoreTile(rc, candidate);
                    if (State.wanderTarget != null) {
                        int newDist = candidate.distanceSquaredTo(State.wanderTarget);
                        if (curDist > newDist)
                            score += 20;
                        else if (curDist == newDist)
                            score += 10;
                    }
                }
                if (minScore == -1 || score < minScore)
                    minScore = score;
                cumSum += score;
                weighted[i] = cumSum;
            }

            if (minScore != 0)
                minScore--;
            for (int i = 0; i < 8; i++)
                weighted[i] -= minScore * (i + 1);

            if (weighted[7] > 0) {
                int rand = State.rng.nextInt(weighted[7]);
                for (int i = 0; i < 8; i++) {
                    if (rand < weighted[i]) {
                        int[] off = State.EXPLORE_OFFSETS[i];
                        State.intermediateTarget = myLoc.translate(off[0], off[1]);
                        break;
                    }
                }
            }
        }

        if (State.intermediateTarget == null) {
            return Pathfind.randomWalk(rc);
        }
        return Pathfind.pathfind(rc, State.intermediateTarget);
    }

    // Nilai kandidat explore
    private static int scoreTile(RobotController rc, MapLocation loc) throws GameActionException {
        int score = 0;
        for (MapInfo nearby : rc.senseNearbyMapInfos(loc, 2)) {
            PaintType p = nearby.getPaint();
            if (p == PaintType.EMPTY && nearby.isPassable())
                score += 5;
            else if (p.isEnemy())
                score += 4;
            else if (p.isAlly())
                score += 0;

            MapLocation nearbyLoc = nearby.getMapLocation();
            if (rc.canSenseRobotAtLocation(nearbyLoc)) {
                if (rc.senseRobotAtLocation(nearbyLoc).getTeam() == rc.getTeam()) {
                    score -= 3;
                }
            }
        }
        for (MapInfo nearby : rc.senseNearbyMapInfos(loc, 8)) {
            if (nearby.hasRuin() && !rc.canSenseRobotAtLocation(nearby.getMapLocation())) {
                score += 3;
                break;
            }
        }
        if (State.last16.contains(loc))
            score -= 3;

        for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (bot.getType().isTowerType() && loc.isWithinDistanceSquared(bot.getLocation(), 9)) {
                score -= 5;
                break;
            }
        }
        return score;
    }

    private static void lowPaintBehavior(RobotController rc) throws GameActionException {
        tryCompleteResourcePattern(rc);

        if (State.lastTower == null) {
            Direction dir = Pathfind.randomPaintedWalk(rc);
            if (dir != null && rc.canMove(dir))
                rc.move(dir);
            return;
        }
        MapLocation towerLoc = State.lastTower.getMapLocation();

        if (rc.canSenseRobotAtLocation(towerLoc)) {
            int towerPaint = rc.senseRobotAtLocation(towerLoc).paintAmount;
            if (rc.getPaint() < 10 && rc.canTransferPaint(towerLoc, -towerPaint)
                    && towerPaint > State.MIN_PAINT_GIVE) {
                rc.transferPaint(towerLoc, -towerPaint);
            }
        }
        int amtToTransfer = rc.getPaint() - rc.getType().paintCapacity;
        if (rc.canTransferPaint(towerLoc, amtToTransfer)) {
            rc.transferPaint(towerLoc, amtToTransfer);
        }

        Direction dir = Pathfind.pathfindPainted(rc, towerLoc);
        if (dir != null && rc.canMove(dir))
            rc.move(dir);
    }

    // Simpan paint tower terdekat
    private static void updateLastPaintTower(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        MapInfo best = null;
        for (MapInfo loc : rc.senseNearbyMapInfos()) {
            if (!loc.hasRuin())
                continue;
            MapLocation ml = loc.getMapLocation();
            if (!rc.canSenseRobotAtLocation(ml))
                continue;
            RobotInfo bot = rc.senseRobotAtLocation(ml);
            if (bot == null || !bot.getTeam().equals(rc.getTeam()))
                continue;
            if (bot.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                State.seenPaintTower = true;
                int d = ml.distanceSquaredTo(State.myLoc);
                if (d < minDist) {
                    minDist = d;
                    best = loc;
                }
            }
        }
        if (minDist != Integer.MAX_VALUE)
            State.lastTower = best;
        else if (State.lastTower != null
                && State.lastTower.getMapLocation().isWithinDistanceSquared(State.myLoc, 20)) {
            State.lastTower = null;
        }
    }

    private static MapLocation pickEdgeTarget(RobotController rc) throws GameActionException {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        MapLocation myLoc = rc.getLocation();

        MapLocation[] candidates = {
            new MapLocation(2, 2),
            new MapLocation(w - 3, 2),
            new MapLocation(2, h - 3),
            new MapLocation(w - 3, h - 3),
            new MapLocation(w / 2, 2),
            new MapLocation(w / 2, h - 3),
            new MapLocation(2, h / 2),
            new MapLocation(w - 3, h / 2),
        };

        int baseIdx = (rc.getID() / 3) % 8;

        MapLocation best = candidates[baseIdx];
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < 8; i++) {
            MapLocation cand = candidates[(baseIdx + i) % 8];
            int distScore = myLoc.distanceSquaredTo(cand);
            int emptyBonus = 0;
            for (MapInfo tile : rc.senseNearbyMapInfos(cand, 8)) {
                if (tile.isPassable() && tile.getPaint() == PaintType.EMPTY) emptyBonus += 3;
            }
            int score = distScore + emptyBonus * 10;
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    // Tentukan tipe tower
    private static UnitType guessTowerType(RobotController rc, MapLocation ruinLoc)
            throws GameActionException {
        if (rc.getNumberTowers() <= 2)
            return UnitType.LEVEL_ONE_MONEY_TOWER;

        int paintTowers = 0, moneyTowers = 0;
        RobotInfo[] myBots = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo bot : myBots) {
            if (!bot.getType().isTowerType())
                continue;
            UnitType base = bot.getType().getBaseType();
            if (base == UnitType.LEVEL_ONE_PAINT_TOWER)
                paintTowers++;
            else if (base == UnitType.LEVEL_ONE_MONEY_TOWER)
                moneyTowers++;
        }

        int currentPaint = rc.getPaint();
        int currentMoney = rc.getMoney();

        if (paintTowers == 0 && currentPaint < 500) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        if (currentPaint > 1500 || currentMoney < 7000) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        int total = rc.getNumberTowers();
        double paintRatio = (paintTowers + 1.0) / (total + 1.0);

        if (paintRatio < 0.2)
            return UnitType.LEVEL_ONE_PAINT_TOWER;

        int cx = rc.getMapWidth() / 2, cy = rc.getMapHeight() / 2;
        double distToCenter = Math.abs(cx - ruinLoc.x) + Math.abs(cy - ruinLoc.y);
        if (distToCenter < (rc.getMapWidth() + rc.getMapHeight()) / 4.0 && total >= 5) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    private static boolean[][] getPattern(UnitType type) throws GameActionException {
        return State.rc.getTowerPattern(type);
    }

    private static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        if (rc.canCompleteResourcePattern(State.myLoc)) {
            rc.completeResourcePattern(State.myLoc);
        }
    }

    private static MapLocation findUnpaintedQuadrantTarget(RobotController rc) throws GameActionException {
        int w = rc.getMapWidth(), h = rc.getMapHeight();
        int cx = w / 2, cy = h / 2;

        int[] emptyCount = new int[4];

        for (MapInfo tile : rc.senseNearbyMapInfos(-1)) {
            if (!tile.isPassable()) continue;
            MapLocation loc = tile.getMapLocation();
            int qx = loc.x < cx ? 0 : 1;
            int qy = loc.y < cy ? 0 : 1;
            int q = qy * 2 + qx;
            if (tile.getPaint() == PaintType.EMPTY) {
                emptyCount[q]++;
            }
        }

        int bestQ = 0;
        for (int i = 1; i < 4; i++) {
            if (emptyCount[i] > emptyCount[bestQ]) bestQ = i;
        }

        int tx, ty;
        switch (bestQ) {
            case 0: tx = State.rng.nextInt(cx);   ty = State.rng.nextInt(cy);   break;
            case 1: tx = cx + State.rng.nextInt(w - cx); ty = State.rng.nextInt(cy); break;
            case 2: tx = State.rng.nextInt(cx);   ty = cy + State.rng.nextInt(h - cy); break;
            default: tx = cx + State.rng.nextInt(w - cx); ty = cy + State.rng.nextInt(h - cy);
        }
        return new MapLocation(tx, ty);
    }
}
