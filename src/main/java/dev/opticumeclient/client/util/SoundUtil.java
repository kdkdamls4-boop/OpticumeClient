package dev.opticumeclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class SoundUtil {
    private static final String NS = "opticumeclient";

    private static final Identifier ENABLE_SMOOTH_ID = Identifier.fromNamespaceAndPath(NS, "smooth_on");
    private static final Identifier DISABLE_SMOOTH_ID = Identifier.fromNamespaceAndPath(NS, "smooth_off");
    public static final SoundEvent ENABLE_SMOOTH_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_SMOOTH_ID);
    public static final SoundEvent DISABLE_SMOOTH_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_SMOOTH_ID);

    private static final Identifier ENABLE_CEL_ID = Identifier.fromNamespaceAndPath(NS, "celestial_on");
    private static final Identifier DISABLE_CEL_ID = Identifier.fromNamespaceAndPath(NS, "celestial_off");
    public static final SoundEvent ENABLE_CEL_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_CEL_ID);
    public static final SoundEvent DISABLE_CEL_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_CEL_ID);

    private static final Identifier ENABLE_NU_ID = Identifier.fromNamespaceAndPath(NS, "nursultan_on");
    private static final Identifier DISABLE_NU_ID = Identifier.fromNamespaceAndPath(NS, "nursultan_off");
    public static final SoundEvent ENABLE_NU_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_NU_ID);
    public static final SoundEvent DISABLE_NU_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_NU_ID);

    private static final Identifier ENABLE_AK_ID = Identifier.fromNamespaceAndPath(NS, "akrien_on");
    private static final Identifier DISABLE_AK_ID = Identifier.fromNamespaceAndPath(NS, "akrien_off");
    public static final SoundEvent ENABLE_AK_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_AK_ID);
    public static final SoundEvent DISABLE_AK_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_AK_ID);

    private static final Identifier ENABLE_TECH_ID = Identifier.fromNamespaceAndPath(NS, "tech_on");
    private static final Identifier DISABLE_TECH_ID = Identifier.fromNamespaceAndPath(NS, "tech_off");
    public static final SoundEvent ENABLE_TECH_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_TECH_ID);
    public static final SoundEvent DISABLE_TECH_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_TECH_ID);

    private static final Identifier ENABLE_BLOP_ID = Identifier.fromNamespaceAndPath(NS, "blop_on");
    private static final Identifier DISABLE_BLOP_ID = Identifier.fromNamespaceAndPath(NS, "blop_off");
    public static final SoundEvent ENABLE_BLOP_EVENT = SoundEvent.createVariableRangeEvent(ENABLE_BLOP_ID);
    public static final SoundEvent DISABLE_BLOP_EVENT = SoundEvent.createVariableRangeEvent(DISABLE_BLOP_ID);

    private static boolean registered = false;

    private SoundUtil() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        reg(ENABLE_SMOOTH_ID, ENABLE_SMOOTH_EVENT);
        reg(DISABLE_SMOOTH_ID, DISABLE_SMOOTH_EVENT);
        reg(ENABLE_CEL_ID, ENABLE_CEL_EVENT);
        reg(DISABLE_CEL_ID, DISABLE_CEL_EVENT);
        reg(ENABLE_NU_ID, ENABLE_NU_EVENT);
        reg(DISABLE_NU_ID, DISABLE_NU_EVENT);
        reg(ENABLE_AK_ID, ENABLE_AK_EVENT);
        reg(DISABLE_AK_ID, DISABLE_AK_EVENT);
        reg(ENABLE_TECH_ID, ENABLE_TECH_EVENT);
        reg(DISABLE_TECH_ID, DISABLE_TECH_EVENT);
        reg(ENABLE_BLOP_ID, ENABLE_BLOP_EVENT);
        reg(DISABLE_BLOP_ID, DISABLE_BLOP_EVENT);
    }

    private static void reg(Identifier id, SoundEvent event) {
        if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
        }
    }

    public static void playUiSound(SoundEvent sound, float volume) {
        if (sound == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        float clamped = Math.max(0.01f, Math.min(1.0f, volume));
        try {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, clamped));
        } catch (Throwable ignored) {
        }
    }
}
