package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupPolicyTest {
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long NOW_EPOCH = 1_800_000_000L;

    @Test
    void defaultHiveAndActiveOnlineHivesAreNeverEligible() {
        long oldEpoch = NOW_EPOCH - 100 * SECONDS_PER_DAY;
        assertFalse(TeamLifecycleService.CleanupPolicy.isOrphanedTeamEligible(
            HiveMindData.DEFAULT_HIVE_ID, true, oldEpoch, oldEpoch, true, false, NOW_EPOCH, 0
        ));
        assertFalse(TeamLifecycleService.CleanupPolicy.isInactiveEmptyTeamEligible(
            HiveMindData.DEFAULT_HIVE_ID, false, oldEpoch, true, true, false, NOW_EPOCH, 1
        ));
        assertFalse(TeamLifecycleService.CleanupPolicy.isOrphanedTeamEligible(
            "team:active", true, oldEpoch, oldEpoch, true, true, NOW_EPOCH, 0
        ));
    }

    @Test
    void orphanedTeamRespectsRetentionDays() {
        long fiveDaysAgo = NOW_EPOCH - 5 * SECONDS_PER_DAY;
        assertTrue(TeamLifecycleService.CleanupPolicy.isOrphanedTeamEligible(
            "team:deleted", true, fiveDaysAgo, fiveDaysAgo, true, false, NOW_EPOCH, 0
        ));
        assertFalse(TeamLifecycleService.CleanupPolicy.isOrphanedTeamEligible(
            "team:deleted", true, fiveDaysAgo, fiveDaysAgo, true, false, NOW_EPOCH, 7
        ));
        assertTrue(TeamLifecycleService.CleanupPolicy.isOrphanedTeamEligible(
            "team:deleted", true, fiveDaysAgo, fiveDaysAgo, true, false, NOW_EPOCH, 5
        ));
    }

    @Test
    void inactiveEmptyTeamRespectsRetentionDaysAndNonEmptyScoreboard() {
        long tenDaysAgo = NOW_EPOCH - 10 * SECONDS_PER_DAY;
        assertFalse(TeamLifecycleService.CleanupPolicy.isInactiveEmptyTeamEligible(
            "team:empty", false, tenDaysAgo, true, true, false, NOW_EPOCH, 0
        ));
        assertFalse(TeamLifecycleService.CleanupPolicy.isInactiveEmptyTeamEligible(
            "team:empty", false, tenDaysAgo, true, true, false, NOW_EPOCH, 30
        ));
        assertFalse(TeamLifecycleService.CleanupPolicy.isInactiveEmptyTeamEligible(
            "team:occupied", false, tenDaysAgo, true, false, false, NOW_EPOCH, 5
        ));
        assertTrue(TeamLifecycleService.CleanupPolicy.isInactiveEmptyTeamEligible(
            "team:empty", false, tenDaysAgo, true, true, false, NOW_EPOCH, 7
        ));
    }

    @Test
    void personalStateEligibilityProtectsOnlinePlayersAndNonEmptyWithinRetention() {
        long fiveDaysAgo = NOW_EPOCH - 5 * SECONDS_PER_DAY;
        long thirtyOneDaysAgo = NOW_EPOCH - 31 * SECONDS_PER_DAY;

        assertTrue(TeamLifecycleService.CleanupPolicy.isPersonalStateEligible(true, NOW_EPOCH, false, NOW_EPOCH, 30));
        assertFalse(TeamLifecycleService.CleanupPolicy.isPersonalStateEligible(true, NOW_EPOCH, true, NOW_EPOCH, 30));

        assertFalse(TeamLifecycleService.CleanupPolicy.isPersonalStateEligible(false, fiveDaysAgo, false, NOW_EPOCH, 0));
        assertFalse(TeamLifecycleService.CleanupPolicy.isPersonalStateEligible(false, fiveDaysAgo, false, NOW_EPOCH, 30));
        assertTrue(TeamLifecycleService.CleanupPolicy.isPersonalStateEligible(false, thirtyOneDaysAgo, false, NOW_EPOCH, 30));
    }
}
