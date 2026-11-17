package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;

import java.nio.ByteBuffer;

public final class GpuStaging {
    private static final long VERTEX_ALIGNMENT = 4L;

    private GpuStaging() {
    }

    public static GpuBufferSlice uploadVertices(ByteBuffer data, int stride) {
        GpuDevice device = RenderSystem.getDevice();
        return device.createCommandEncoder().transientMemory()
                .uploadStaging(data, VERTEX_ALIGNMENT, GpuBuffer.USAGE_VERTEX, data.remaining(), stride);
    }

    public static GpuBufferSlice uploadUniform(ByteBuffer data) {
        GpuDevice device = RenderSystem.getDevice();
        long alignment = device.getDeviceInfo().limits().minUniformOffsetAlignment();
        return device.createCommandEncoder().transientMemory()
                .uploadStaging(data, alignment, GpuBuffer.USAGE_UNIFORM);
    }
}
