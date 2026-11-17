package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public final class BufferPool implements AutoCloseable {
    private static final int DEFAULT_CAPACITY = 786432;
    private static final BufferPool INSTANCE = new BufferPool(DEFAULT_CAPACITY);

    private final ByteBufferBuilder byteBufferBuilder;

    public BufferPool(int capacity) {
        this.byteBufferBuilder = new ByteBufferBuilder(capacity);
    }

    public static BufferPool getInstance() {
        return INSTANCE;
    }

    public BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(this.byteBufferBuilder, topology, format);
    }

    public void clear() {
        this.byteBufferBuilder.clear();
    }

    @Override
    public void close() {
        this.byteBufferBuilder.close();
    }
}
