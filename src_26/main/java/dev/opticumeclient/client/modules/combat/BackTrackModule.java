package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Delays incoming entity movement updates for the active combat target up to delayMs,
 * rendering a 3D box at the target's real server position and flushing safely on distance/timeout.
 */
public class BackTrackModule extends Module {
    private static BackTrackModule INSTANCE;

    private record QueuedPacket(Packet<?> packet, PacketListener listener, long releaseAtMs) {}

    private final NumberSetting delayMs = new NumberSetting("DelayMs", "Задержка позиции цели (мс)", 120.0, 20.0, 450.0, 10.0);
    private final NumberSetting maxRange = new NumberSetting("MaxRange", "Максимальная дистанция работы", 6.0, 3.0, 9.0, 0.25);
    private final BooleanSetting onlyDuringCombat = new BooleanSetting("OnlyCombat", "Работать только при активной цели ауры/ударе", true);
    private final BooleanSetting renderRealPos = new BooleanSetting("RenderRealBox", "Показывать реальный хитбокс сервера", true);

    private final Deque<QueuedPacket> queue = new ArrayDeque<>();
    private final ThreadLocal<Boolean> replaying = ThreadLocal.withInitial(() -> false);

    private volatile int trackedEntityId = -1;
    private volatile Vec3 realServerPos = null;
    private final VecDeltaCodec realPosCodec = new VecDeltaCodec();
    private volatile long lastCombatMs = 0L;

    public BackTrackModule() {
        super("BackTrack", "Буферизация перемещений цели с отображением реального серверного хитбокса", Category.COMBAT);
        INSTANCE = this;
        addSetting(delayMs);
        addSetting(maxRange);
        addSetting(onlyDuringCombat);
        addSetting(renderRealPos);
    }

    public static BackTrackModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return delayMs.getIntValue() + "ms";
    }

    @Override
    public void onDisable() {
        flushAll();
        trackedEntityId = -1;
        realServerPos = null;
    }

    public void onAttackTarget(Entity target) {
        if (!isEnabled() || !(target instanceof LivingEntity)) return;
        if (trackedEntityId != target.getId()) {
            flushAll();
            trackedEntityId = target.getId();
            realServerPos = target.position();
            realPosCodec.setBase(realServerPos);
        }
        lastCombatMs = System.currentTimeMillis();
    }

    /**
     * Called from ClientConnectionMixin on S2C packet receipt.
     * Returns true if the packet was buffered and should be cancelled for now.
     */
    public boolean onReceivePacket(Packet<?> packet, PacketListener listener) {
        if (!isEnabled() || replaying.get() || mc.player == null || mc.level == null) {
            return false;
        }

        // Always flush on self teleport to avoid desync
        if (packet instanceof ClientboundPlayerPositionPacket) {
            mc.execute(this::flushAll);
            return false;
        }

        // Sync active target from OpticumeAura if present
        OpticumeAuraModule aura = OpticumeAuraModule.getInstance();
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) {
            Entity t = aura.getTarget();
            if (trackedEntityId != t.getId()) {
                mc.execute(this::flushAll);
                trackedEntityId = t.getId();
                realServerPos = t.position();
                realPosCodec.setBase(realServerPos);
            }
            lastCombatMs = System.currentTimeMillis();
        }

        if (trackedEntityId == -1) return false;
        if (onlyDuringCombat.isEnabled() && System.currentTimeMillis() - lastCombatMs > 3000L) {
            return false;
        }

        if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket) {
            if (syncPacket.id() != trackedEntityId) return false;
            Vec3 newPos = syncPacket.position().endPosition();
            realServerPos = newPos;
            synchronized (realPosCodec) {
                realPosCodec.setBase(newPos);
            }
            if (mc.player.position().distanceTo(newPos) > maxRange.getValue()) {
                mc.execute(this::flushAll);
                return false;
            }
            boolean overflow;
            synchronized (queue) {
                overflow = queue.size() >= 64;
                if (!overflow) {
                    queue.addLast(new QueuedPacket(packet, listener, System.currentTimeMillis() + delayMs.getIntValue()));
                }
            }
            if (overflow) {
                // Release retained older packets in receive order before letting this newer update pass
                flushAll();
                return false;
            }
            return true;
        }

        if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            Entity entity = movePacket.getEntity(mc.level);
            if (entity == null || entity.getId() != trackedEntityId) return false;

            Vec3 base = realServerPos != null ? realServerPos : entity.position();
            if (movePacket.hasPosition()) {
                synchronized (realPosCodec) {
                    realPosCodec.setBase(base);
                    base = movePacket.getPositionDelta().decode(realPosCodec).endPosition();
                    realPosCodec.setBase(base);
                }
            }
            realServerPos = base;
            if (mc.player.position().distanceTo(base) > maxRange.getValue()) {
                mc.execute(this::flushAll);
                return false;
            }
            boolean overflow;
            synchronized (queue) {
                overflow = queue.size() >= 64;
                if (!overflow) {
                    queue.addLast(new QueuedPacket(packet, listener, System.currentTimeMillis() + delayMs.getIntValue()));
                }
            }
            if (overflow) {
                flushAll();
                return false;
            }
            return true;
        }

        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            flushAll();
            trackedEntityId = -1;
            realServerPos = null;
            return;
        }

        Entity tracked = trackedEntityId != -1 ? mc.level.getEntity(trackedEntityId) : null;
        if (tracked == null || !tracked.isAlive()
                || mc.player.distanceTo(tracked) > maxRange.getValue() + 2.0
                || (onlyDuringCombat.isEnabled() && System.currentTimeMillis() - lastCombatMs > 3000L)) {
            flushAll();
            trackedEntityId = -1;
            realServerPos = null;
            return;
        }

        long now = System.currentTimeMillis();
        while (true) {
            QueuedPacket next;
            synchronized (queue) {
                QueuedPacket head = queue.peekFirst();
                if (head == null || head.releaseAtMs() > now) break;
                next = queue.pollFirst();
            }
            dispatchPacket(next);
        }
    }

    public void flushAll() {
        while (true) {
            QueuedPacket next;
            synchronized (queue) {
                next = queue.pollFirst();
            }
            if (next == null) break;
            dispatchPacket(next);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends PacketListener> void dispatchPacket(QueuedPacket qp) {
        if (qp == null || qp.packet() == null || qp.listener() == null) return;
        replaying.set(true);
        try {
            ((Packet<T>) qp.packet()).handle((T) qp.listener());
        } catch (Throwable ignored) {
        } finally {
            replaying.set(false);
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!renderRealPos.isEnabled() || realServerPos == null || trackedEntityId == -1 || mc.level == null) return;
        Entity tracked = mc.level.getEntity(trackedEntityId);
        if (tracked == null || !tracked.isAlive()) return;

        double hw = tracked.getBbWidth() * 0.5;
        double h = tracked.getBbHeight();
        AABB realBox = new AABB(
                realServerPos.x - hw, realServerPos.y, realServerPos.z - hw,
                realServerPos.x + hw, realServerPos.y + h, realServerPos.z + hw
        );
        int color = ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(0), 210);
        Render3DHelper.drawBoxColor(realBox, color, 1.6f, true);
    }
}
