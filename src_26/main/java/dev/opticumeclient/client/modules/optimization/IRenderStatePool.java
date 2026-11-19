package dev.opticumeclient.client.modules.optimization;

/**
 * Per-entity / per-block-entity cached render-state interface implemented via {@code RenderStatePoolMixin}.
 */
public interface IRenderStatePool {
    Object opticume$pooledState(Object owner);

    void opticume$poolState(Object owner, Object state);
}
