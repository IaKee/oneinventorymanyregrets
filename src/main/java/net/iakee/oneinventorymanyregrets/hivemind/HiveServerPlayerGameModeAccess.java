package net.iakee.oneinventorymanyregrets.hivemind;

/** Access to the server's authoritative block-breaking state. */
public interface HiveServerPlayerGameModeAccess {
    boolean oimr$isDestroyingOrDelayedBlock();

    void oimr$setDestroyingBlockForTest(boolean destroying);
}
