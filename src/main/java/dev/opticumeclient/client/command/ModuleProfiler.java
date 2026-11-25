package dev.opticumeclient.client.command;

import dev.opticumeclient.client.modules.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight per-module execution profiler measuring tick / render2D / render3D time in nanoseconds.
 */
public final class ModuleProfiler {
    public static final class Sample {
        public final String moduleName;
        public long tickNs;
        public long render2dNs;
        public long render3dNs;
        public long peakNs;
        public int frames;

        Sample(String moduleName) {
            this.moduleName = moduleName;
        }

        public double avgTotalMs() {
            int f = Math.max(1, frames);
            return (tickNs + render2dNs + render3dNs) / (f * 1_000_000.0);
        }

        public double peakMs() {
            return peakNs / 1_000_000.0;
        }
    }

    private static final ModuleProfiler INSTANCE = new ModuleProfiler();
    private final Map<String, Sample> samples = new LinkedHashMap<>();
    private boolean recording = true;

    private ModuleProfiler() {
    }

    public static ModuleProfiler getInstance() {
        return INSTANCE;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    public synchronized void recordTick(Module module, long elapsedNs) {
        if (!recording || module == null) return;
        Sample s = samples.computeIfAbsent(module.getName(), Sample::new);
        s.tickNs += elapsedNs;
        s.frames++;
        if (elapsedNs > s.peakNs) s.peakNs = elapsedNs;
    }

    public synchronized void recordRender2D(Module module, long elapsedNs) {
        if (!recording || module == null) return;
        Sample s = samples.computeIfAbsent(module.getName(), Sample::new);
        s.render2dNs += elapsedNs;
        if (elapsedNs > s.peakNs) s.peakNs = elapsedNs;
    }

    public synchronized void recordRender3D(Module module, long elapsedNs) {
        if (!recording || module == null) return;
        Sample s = samples.computeIfAbsent(module.getName(), Sample::new);
        s.render3dNs += elapsedNs;
        if (elapsedNs > s.peakNs) s.peakNs = elapsedNs;
    }

    public synchronized void reset() {
        samples.clear();
    }

    public synchronized List<Sample> getTopSamples(int limit) {
        List<Sample> list = new ArrayList<>(samples.values());
        list.sort(Comparator.comparingDouble(Sample::avgTotalMs).reversed());
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }
}
