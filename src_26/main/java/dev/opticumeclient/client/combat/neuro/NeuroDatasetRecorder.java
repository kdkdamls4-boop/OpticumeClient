package dev.opticumeclient.client.combat.neuro;

import dev.opticumeclient.client.util.AtomicFileWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Records manual mouse combat aim trajectories vs nearest combat target into
 * <gameDir>/opticumeclient/neuro/dataset.csv for training GruRotationModel.
 */
public final class NeuroDatasetRecorder {
    public record Sample(float errYaw, float errPitch, float dist, float velHoriz, float hurtFrac,
                         float humanStepYaw, float humanStepPitch) {
    }

    private record PreObservation(int targetId, float errYaw, float errPitch, float dist, float velHoriz, float hurtFrac) {
    }

    private static final NeuroDatasetRecorder INSTANCE = new NeuroDatasetRecorder();
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticume-neuro-dataset-io");
        t.setDaemon(true);
        return t;
    });

    private boolean recording = false;
    private final List<Sample> buffer = new ArrayList<>();
    private float prevYaw = Float.NaN;
    private float prevPitch = Float.NaN;
    private PreObservation pendingObs = null;
    private int pendingSaveCount = 0;
    private File datasetFile;

    private NeuroDatasetRecorder() {
    }

    public static NeuroDatasetRecorder getInstance() {
        return INSTANCE;
    }

    private void ensureFile() {
        if (datasetFile != null) return;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File dir = new File(gameDir, "opticumeclient/neuro");
        if (!dir.exists()) dir.mkdirs();
        datasetFile = new File(dir, "dataset.csv");
        loadFromDisk();
    }

    public boolean isRecording() {
        return recording;
    }

    public synchronized void startRecording() {
        ensureFile();
        recording = true;
        prevYaw = Float.NaN;
        prevPitch = Float.NaN;
        pendingObs = null;
    }

    public synchronized void stopRecording() {
        recording = false;
        pendingObs = null;
        pendingSaveCount = 0;
        enqueueSnapshotSave();
    }

    public synchronized int getSampleCount() {
        ensureFile();
        return buffer.size();
    }

    public synchronized List<Sample> getSamples() {
        ensureFile();
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }

    public synchronized void clear() {
        ensureFile();
        buffer.clear();
        pendingObs = null;
        pendingSaveCount = 0;
        File target = datasetFile;
        SAVE_EXECUTOR.execute(() -> {
            if (target != null && target.exists()) {
                target.delete();
            }
        });
    }

    public synchronized void onTick() {
        if (!recording) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            prevYaw = Float.NaN;
            pendingObs = null;
            return;
        }
        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        LivingEntity nearest = findNearestTarget(mc);

        if (!Float.isNaN(prevYaw) && pendingObs != null) {
            float dYaw = Mth.wrapDegrees(curYaw - prevYaw);
            float dPitch = curPitch - prevPitch;
            if ((Math.abs(dYaw) >= 0.05f || Math.abs(dPitch) >= 0.05f)
                    && nearest != null && nearest.getId() == pendingObs.targetId()) {
                if (buffer.size() >= 8000) {
                    buffer.remove(0);
                }
                buffer.add(new Sample(
                        pendingObs.errYaw(), pendingObs.errPitch(),
                        pendingObs.dist(), pendingObs.velHoriz(), pendingObs.hurtFrac(),
                        dYaw, dPitch
                ));
                pendingSaveCount++;
                if (pendingSaveCount >= 40) {
                    pendingSaveCount = 0;
                    enqueueSnapshotSave();
                }
            }
        }

        prevYaw = curYaw;
        prevPitch = curPitch;

        if (nearest == null) {
            pendingObs = null;
            return;
        }

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 targetCenter = nearest.position().add(0.0, nearest.getBbHeight() * 0.6, 0.0);
        Vec3 diff = targetCenter.subtract(eyes);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));

        float errYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float errPitch = Mth.clamp(targetPitch - curPitch, -90.0f, 90.0f);
        float dist = (float) eyes.distanceTo(targetCenter);
        Vec3 vel = nearest.getDeltaMovement();
        float velH = (float) Math.hypot(vel.x, vel.z);
        float hurt = nearest.hurtTime > 0 ? Math.min(1.0f, nearest.hurtTime / 10.0f) : 0.0f;
        pendingObs = new PreObservation(nearest.getId(), errYaw, errPitch, dist, velH, hurt);
    }

    private static LivingEntity findNearestTarget(Minecraft mc) {
        LivingEntity best = null;
        double bestDistSq = 36.0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
            double d = mc.player.distanceToSqr(le);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = le;
            }
        }
        return best;
    }

    private void loadFromDisk() {
        if (datasetFile == null || !datasetFile.exists()) return;
        try (BufferedReader br = Files.newBufferedReader(datasetFile.toPath(), StandardCharsets.UTF_8)) {
            buffer.clear();
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split(",");
                if (p.length != 7) continue;
                try {
                    float v0 = Float.parseFloat(p[0]);
                    float v1 = Float.parseFloat(p[1]);
                    float v2 = Float.parseFloat(p[2]);
                    float v3 = Float.parseFloat(p[3]);
                    float v4 = Float.parseFloat(p[4]);
                    float v5 = Float.parseFloat(p[5]);
                    float v6 = Float.parseFloat(p[6]);
                    if (Float.isFinite(v0) && Float.isFinite(v1) && Float.isFinite(v2) && Float.isFinite(v3)
                            && Float.isFinite(v4) && Float.isFinite(v5) && Float.isFinite(v6)) {
                        buffer.add(new Sample(v0, v1, v2, v3, v4, v5, v6));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void enqueueSnapshotSave() {
        if (datasetFile == null) return;
        final File target = datasetFile;
        final List<Sample> snapshot = new ArrayList<>(buffer);
        SAVE_EXECUTOR.execute(() -> writeSnapshotToDisk(target, snapshot));
    }

    private static void writeSnapshotToDisk(File target, List<Sample> snapshot) {
        if (target == null) return;
        try {
            AtomicFileWriter.writeUtf8(target, bw -> {
                for (Sample s : snapshot) {
                    bw.write(String.format(Locale.US, "%.4f,%.4f,%.3f,%.4f,%.3f,%.4f,%.4f%n",
                            s.errYaw(), s.errPitch(), s.dist(), s.velHoriz(), s.hurtFrac(), s.humanStepYaw(), s.humanStepPitch()));
                }
            });
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to save neuro dataset: " + t.getMessage());
        }
    }
}
