package Nate;

import battlecode.common.*;

/**
 * Greedy Algorithm Bot for Battlecode 2025.
 *
 * Strategy overview:
 * Early game (rounds 1–800):
 * - Soldiers explore and build towers at ruins (2 money : 1 paint ratio).
 * - Towers spawn soldiers to find more ruins.
 * Mid-late game (rounds 800–2000):
 * - Shift to mass territory painting with splashers and moppers.
 * - Prioritize Splashers.
 * - Soldiers protect towers and attack enemy bots/towers.
 * All towers Level 1 only — no upgrades.
 * Never repaint ally-painted tiles — always seek unmarked/enemy territory.
 */
public class RobotPlayer {

    static int turnCount = 0;

    // =========================================================================
    // MAIN ENTRY
    // =========================================================================

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        Soldier.run(rc);
                        break;
                    case MOPPER:
                        Mopper.run(rc);
                        break;
                    case SPLASHER:
                        Splasher.run(rc);
                        break;
                    default:
                        Tower.run(rc);
                        break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
