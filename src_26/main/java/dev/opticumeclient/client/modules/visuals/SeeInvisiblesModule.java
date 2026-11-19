package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Ported from Desire SeeInvisiblesModule:
 * Makes invisible players translucent at configurable alpha instead of hidden.
 */
public class SeeInvisiblesModule extends Module {
    private static SeeInvisiblesModule instance;

    private final NumberSetting alpha = new NumberSetting("Alpha", "Прозрачность невидимых игроков", 0.30, 0.05, 1.0, 0.05);

    public SeeInvisiblesModule() {
        super("SeeInvisibles", "Показывает невидимых игроков полупрозрачными", Category.VISUALS);
        instance = this;
        addSetting(alpha);
    }

    public static SeeInvisiblesModule getInstance() {
        return instance;
    }

    public boolean shouldShow(LivingEntityRenderState state) {
        if (!isEnabled() || state == null || !state.isInvisibleToPlayer) return false;
        return state instanceof AvatarRenderState || Math.abs(state.boundingBoxWidth - 0.6f) < 0.02f;
    }

    public int applyAlphaTint(int color) {
        int a = Math.max(0, Math.min(255, Math.round(alpha.getFloatValue() * 255.0f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public float getAlpha() {
        return alpha.getFloatValue();
    }
}
