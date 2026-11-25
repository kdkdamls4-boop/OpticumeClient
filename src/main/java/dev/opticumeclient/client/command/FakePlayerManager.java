package dev.opticumeclient.client.command;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

/**
 * Client-side fake player manager for safely testing combat, aura, crystal, TargetESP and KillEffect.
 */
public final class FakePlayerManager {
    private static final FakePlayerManager INSTANCE = new FakePlayerManager();
    private static final int FAKE_ENTITY_ID = -133701;

    private RemotePlayer fakePlayer;
    private ClientLevel spawnedLevel;

    private FakePlayerManager() {
    }

    public static FakePlayerManager getInstance() {
        return INSTANCE;
    }

    public boolean isSpawned() {
        Minecraft mc = Minecraft.getInstance();
        return fakePlayer != null
                && mc != null
                && mc.level != null
                && spawnedLevel == mc.level
                && mc.level.getEntity(fakePlayer.getId()) == fakePlayer;
    }

    public RemotePlayer getFakePlayer() {
        return isSpawned() ? fakePlayer : null;
    }

    public boolean spawn(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        remove();

        String cleanName = (name == null || name.isBlank()) ? "OpticumeDummy" : name.trim();
        if (cleanName.length() > 16) {
            cleanName = cleanName.substring(0, 16);
        }
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(cleanName);
        RemotePlayer clone = new RemotePlayer(mc.level, new GameProfile(uuid, cleanName));
        clone.setId(FAKE_ENTITY_ID);
        clone.copyPosition(mc.player);
        clone.yHeadRot = mc.player.yHeadRot;
        clone.yBodyRot = mc.player.yBodyRot;
        clone.setHealth(mc.player.getHealth());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        }

        mc.level.addEntity(clone);
        this.fakePlayer = clone;
        this.spawnedLevel = mc.level;
        return true;
    }

    public boolean remove() {
        Minecraft mc = Minecraft.getInstance();
        if (fakePlayer == null) {
            spawnedLevel = null;
            return false;
        }
        if (mc != null && mc.level != null && spawnedLevel == mc.level) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
        spawnedLevel = null;
        return true;
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || spawnedLevel != mc.level) {
            fakePlayer = null;
            spawnedLevel = null;
        }
    }
}
