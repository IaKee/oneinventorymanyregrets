package net.iakee.oneinventorymanyregrets.client;

import net.iakee.oneinventorymanyregrets.network.DamageIconType;
import net.iakee.oneinventorymanyregrets.network.OIMRAirFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRDamageFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRFoodFeedPayload;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHudFeedStateTest {
    @BeforeEach
    @AfterEach
    void resetFeedState() {
        ClientHudFeedState.reset();
    }

    @Test
    void invalidPayloadsAreIgnored() {
        ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("", DamageIconType.MELEE, 4.0F));
        ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("Alice", DamageIconType.MELEE, 0.0F));
        ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("Alice", DamageIconType.MELEE, -2.0F));
        ClientHudFeedState.pushFood(new OIMRFoodFeedPayload("Alice", ItemStack.EMPTY, 4));
        ClientHudFeedState.pushAir(new OIMRAirFeedPayload("", 2));
        ClientHudFeedState.pushAir(new OIMRAirFeedPayload("Alice", 0));
        ClientHudFeedState.pushAir(new OIMRAirFeedPayload("Alice", -1));

        assertTrue(ClientHudFeedState.snapshotDamageEntries(false).isEmpty());
        assertTrue(ClientHudFeedState.snapshotRightEntries(false).isEmpty());
    }

    @Test
    void pushDamageOrdersNewestFirstAndCapsOverflowEntriesForExit() {
        for (int i = 1; i <= 6; i++) {
            ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("Player" + i, DamageIconType.LAVA, (float) i));
        }

        List<ClientHudFeedState.DamageEntry> entries = ClientHudFeedState.snapshotDamageEntries(true);
        assertEquals(6, entries.size());
        assertEquals("Player6", entries.getFirst().playerName());
        assertEquals(0, entries.getFirst().targetSlot);
        assertEquals("Player1", entries.getLast().playerName());
        assertEquals(5, entries.getLast().targetSlot);
        assertEquals(
            ClientHudFeedState.FADE_IN_MS + ClientHudFeedState.HOLD_MS,
            entries.getLast().ageMs,
            "Overflow entry beyond MAX_VISIBLE_ENTRIES must immediately enter fade-out"
        );
    }

    @Test
    void rightFeedStacksAirEntriesCapsOverflowAndResetsCleanly() {
        for (int i = 1; i <= 6; i++) {
            ClientHudFeedState.pushAir(new OIMRAirFeedPayload("Diver" + i, i));
        }

        List<ClientHudFeedState.FeedEntry> right = ClientHudFeedState.snapshotRightEntries(true);
        assertEquals(6, right.size());
        ClientHudFeedState.AirEntry newest = assertInstanceOf(ClientHudFeedState.AirEntry.class, right.getFirst());
        assertEquals("Diver6", newest.playerName());
        assertEquals(6, newest.drainedBubbles());
        assertEquals(0, newest.targetSlot);

        ClientHudFeedState.AirEntry overflow = assertInstanceOf(ClientHudFeedState.AirEntry.class, right.getLast());
        assertEquals("Diver1", overflow.playerName());
        assertEquals(5, overflow.targetSlot);
        assertEquals(ClientHudFeedState.FADE_IN_MS + ClientHudFeedState.HOLD_MS, overflow.ageMs);

        ClientHudFeedState.reset();
        assertTrue(ClientHudFeedState.snapshotRightEntries(false).isEmpty());
        assertTrue(ClientHudFeedState.snapshotDamageEntries(false).isEmpty());
    }

    @Test
    void animationAlphaAndVerticalOffsetFollowFadeLifecycle() {
        ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("Alice", DamageIconType.FALL, 6.0F));
        ClientHudFeedState.DamageEntry entry = ClientHudFeedState.snapshotDamageEntries(true).getFirst();

        entry.ageMs = 0L;
        assertEquals(0.0F, entry.alpha(), 1.0E-5F);
        assertEquals(2.0F, entry.verticalOffsetPx(12.0F), 1.0E-5F);

        entry.ageMs = ClientHudFeedState.FADE_IN_MS;
        assertEquals(1.0F, entry.alpha(), 1.0E-5F);
        assertEquals(0.0F, entry.verticalOffsetPx(12.0F), 1.0E-5F);

        entry.ageMs = ClientHudFeedState.FADE_IN_MS + ClientHudFeedState.HOLD_MS + ClientHudFeedState.FADE_OUT_MS;
        assertEquals(0.0F, entry.alpha(), 1.0E-5F);
        assertEquals(-7.0F, entry.verticalOffsetPx(12.0F), 1.0E-5F);
    }
}
