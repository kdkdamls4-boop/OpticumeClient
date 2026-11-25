package dev.opticumeclient.client.combat.neuro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.util.AtomicFileWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

/**
 * Pure-Java 1-layer GRU neural rotation predictor (8 inputs -> 16 hidden -> 2 angular step outputs)
 * producing human-like ballistic acceleration, subtle overshoot damping, and trainable weights from .neuro datasets.
 */
public final class GruRotationModel {
    public static final int INPUT_DIM = 8;
    public static final int HIDDEN_DIM = 16;
    public static final int OUTPUT_DIM = 2;

    private static final GruRotationModel INSTANCE = new GruRotationModel();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // GRU gates (z = update, r = reset, n = candidate)
    private final float[][] wz = new float[HIDDEN_DIM][INPUT_DIM];
    private final float[][] uz = new float[HIDDEN_DIM][HIDDEN_DIM];
    private final float[] bz = new float[HIDDEN_DIM];

    private final float[][] wr = new float[HIDDEN_DIM][INPUT_DIM];
    private final float[][] ur = new float[HIDDEN_DIM][HIDDEN_DIM];
    private final float[] br = new float[HIDDEN_DIM];

    private final float[][] wn = new float[HIDDEN_DIM][INPUT_DIM];
    private final float[][] un = new float[HIDDEN_DIM][HIDDEN_DIM];
    private final float[] bn = new float[HIDDEN_DIM];

    // Linear readout head from hidden state + direct residual skip weights
    private final float[][] wOut = new float[OUTPUT_DIM][HIDDEN_DIM];
    private final float[] bOut = new float[OUTPUT_DIM];
    private float yawGain = 0.68f;
    private float pitchGain = 0.56f;
    private volatile boolean lastSaveFailed = false;

    private final float[] h = new float[HIDDEN_DIM];
    private float prevStepYaw = 0.0f;
    private float prevStepPitch = 0.0f;
    private int lastTargetId = Integer.MIN_VALUE;
    private File weightsFile;
    private boolean initialized = false;

    private GruRotationModel() {
    }

    public static GruRotationModel getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        initPretrainedWeights();
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File dir = new File(gameDir, "opticumeclient/neuro");
        if (!dir.exists()) dir.mkdirs();
        weightsFile = new File(dir, "gru_weights.json");
        loadWeights();
    }

    public synchronized void resetState() {
        for (int i = 0; i < HIDDEN_DIM; i++) h[i] = 0.0f;
        prevStepYaw = 0.0f;
        prevStepPitch = 0.0f;
        lastTargetId = Integer.MIN_VALUE;
    }

    /**
     * Predicts organic angular step [stepYawDeg, stepPitchDeg] for the current tick.
     */
    public synchronized float[] step(int targetId,
                                     float errYawDeg,
                                     float errPitchDeg,
                                     float distance,
                                     float targetVelHoriz,
                                     float targetHurtFrac,
                                     float maxYawSpeed,
                                     float maxPitchSpeed) {
        ensureInitialized();
        if (targetId != lastTargetId) {
            for (int i = 0; i < HIDDEN_DIM; i++) h[i] *= 0.25f;
            lastTargetId = targetId;
        }

        float[] x = buildInputVector(errYawDeg, errPitchDeg, distance, targetVelHoriz,
                prevStepYaw, prevStepPitch, targetHurtFrac);
        float[] hNext = forwardCell(x, h);
        System.arraycopy(hNext, 0, h, 0, HIDDEN_DIM);

        float out0 = bOut[0];
        float out1 = bOut[1];
        for (int j = 0; j < HIDDEN_DIM; j++) {
            out0 += wOut[0][j] * h[j];
            out1 += wOut[1][j] * h[j];
        }

        // Combine learned non-linear GRU modulation with proportional target-center convergence
        float yawNorm = Mth.clamp(errYawDeg / 45.0f, -1.0f, 1.0f);
        float pitchNorm = Mth.clamp(errPitchDeg / 35.0f, -1.0f, 1.0f);
        float modYaw = 0.65f + 0.35f * (float) Math.tanh(out0);
        float modPitch = 0.65f + 0.35f * (float) Math.tanh(out1);

        float rawStepYaw = yawNorm * 45.0f * yawGain * modYaw + prevStepYaw * 0.14f;
        float rawStepPitch = pitchNorm * 35.0f * pitchGain * modPitch + prevStepPitch * 0.12f;

        // Soft deadzone damping right on hitbox center for realistic micro-settle
        if (Math.abs(errYawDeg) < 1.2f) rawStepYaw *= 0.45f;
        if (Math.abs(errPitchDeg) < 1.0f) rawStepPitch *= 0.45f;

        float stepYaw = Mth.clamp(rawStepYaw, -maxYawSpeed, maxYawSpeed);
        float stepPitch = Mth.clamp(rawStepPitch, -maxPitchSpeed, maxPitchSpeed);
        prevStepYaw = stepYaw;
        prevStepPitch = stepPitch;
        return new float[]{stepYaw, stepPitch};
    }

    public synchronized int trainFromDataset(List<NeuroDatasetRecorder.Sample> samples, int epochs, float lr) {
        ensureInitialized();
        if (samples == null || samples.size() < 8) return 0;

        float[] localH = new float[HIDDEN_DIM];
        for (int ep = 0; ep < epochs; ep++) {
            for (int i = 0; i < HIDDEN_DIM; i++) localH[i] = 0.0f;
            float trainPrevYaw = 0.0f;
            float trainPrevPitch = 0.0f;
            float prevErrYaw = Float.NaN;
            float prevDist = Float.NaN;
            for (NeuroDatasetRecorder.Sample s : samples) {
                if (!Float.isNaN(prevErrYaw)) {
                    float errJump = Math.abs(Mth.wrapDegrees(s.errYaw() - prevErrYaw));
                    float distJump = Math.abs(s.dist() - prevDist);
                    if (errJump > 40.0f || distJump > 1.8f) {
                        for (int i = 0; i < HIDDEN_DIM; i++) localH[i] = 0.0f;
                        trainPrevYaw = 0.0f;
                        trainPrevPitch = 0.0f;
                    }
                }
                float[] x = buildInputVector(s.errYaw(), s.errPitch(), s.dist(), s.velHoriz(),
                        trainPrevYaw, trainPrevPitch, s.hurtFrac());
                localH = forwardCell(x, localH);

                // Supervise readout head against the exact inference modulation equation:
                // rawStep = (norm * range * gain * (0.65 + 0.35 * tanh(out)) + prevStep * decay) * deadzoneScale
                float baseYaw = Mth.clamp(s.errYaw() / 45.0f, -1.0f, 1.0f) * 45.0f * yawGain;
                float basePitch = Mth.clamp(s.errPitch() / 35.0f, -1.0f, 1.0f) * 35.0f * pitchGain;
                float dzYaw = Math.abs(s.errYaw()) < 1.2f ? 0.45f : 1.0f;
                float dzPitch = Math.abs(s.errPitch()) < 1.0f ? 0.45f : 1.0f;

                float pred0 = bOut[0];
                float pred1 = bOut[1];
                for (int j = 0; j < HIDDEN_DIM; j++) {
                    pred0 += wOut[0][j] * localH[j];
                    pred1 += wOut[1][j] * localH[j];
                }
                float err0 = 0.0f;
                if (Math.abs(baseYaw * dzYaw) > 1.0e-3f) {
                    float desiredModY = ((s.humanStepYaw() / dzYaw) - trainPrevYaw * 0.14f) / baseYaw;
                    float targetTanhY = Mth.clamp((desiredModY - 0.65f) / 0.35f, -1.0f, 1.0f);
                    err0 = (float) Math.tanh(pred0) - targetTanhY;
                }
                float err1 = 0.0f;
                if (Math.abs(basePitch * dzPitch) > 1.0e-3f) {
                    float desiredModP = ((s.humanStepPitch() / dzPitch) - trainPrevPitch * 0.12f) / basePitch;
                    float targetTanhP = Mth.clamp((desiredModP - 0.65f) / 0.35f, -1.0f, 1.0f);
                    err1 = (float) Math.tanh(pred1) - targetTanhP;
                }

                bOut[0] -= lr * err0;
                bOut[1] -= lr * err1;
                for (int j = 0; j < HIDDEN_DIM; j++) {
                    wOut[0][j] = Mth.clamp(wOut[0][j] - lr * err0 * localH[j], -1.5f, 1.5f);
                    wOut[1][j] = Mth.clamp(wOut[1][j] - lr * err1 * localH[j], -1.5f, 1.5f);
                }

                trainPrevYaw = s.humanStepYaw();
                trainPrevPitch = s.humanStepPitch();
                prevErrYaw = s.errYaw();
                prevDist = s.dist();
            }
        }
        saveWeights();
        return samples.size();
    }

    private static float[] buildInputVector(float errYaw, float errPitch, float dist, float velHoriz,
                                            float prevYawStep, float prevPitchStep, float hurtFrac) {
        return new float[]{
                Mth.clamp(errYaw / 45.0f, -1.5f, 1.5f),
                Mth.clamp(errPitch / 35.0f, -1.5f, 1.5f),
                Mth.clamp(dist / 6.0f, 0.0f, 1.5f),
                Mth.clamp(velHoriz * 2.5f, 0.0f, 1.5f),
                Mth.clamp(prevYawStep / 30.0f, -1.5f, 1.5f),
                Mth.clamp(prevPitchStep / 30.0f, -1.5f, 1.5f),
                Mth.clamp(hurtFrac, 0.0f, 1.0f),
                1.0f
        };
    }

    private float[] forwardCell(float[] x, float[] prevH) {
        float[] z = new float[HIDDEN_DIM];
        float[] r = new float[HIDDEN_DIM];
        float[] n = new float[HIDDEN_DIM];
        float[] nextH = new float[HIDDEN_DIM];

        for (int i = 0; i < HIDDEN_DIM; i++) {
            float accZ = bz[i];
            float accR = br[i];
            for (int k = 0; k < INPUT_DIM; k++) {
                accZ += wz[i][k] * x[k];
                accR += wr[i][k] * x[k];
            }
            for (int j = 0; j < HIDDEN_DIM; j++) {
                accZ += uz[i][j] * prevH[j];
                accR += ur[i][j] * prevH[j];
            }
            z[i] = sigmoid(accZ);
            r[i] = sigmoid(accR);
        }

        for (int i = 0; i < HIDDEN_DIM; i++) {
            float accN = bn[i];
            for (int k = 0; k < INPUT_DIM; k++) {
                accN += wn[i][k] * x[k];
            }
            for (int j = 0; j < HIDDEN_DIM; j++) {
                accN += un[i][j] * (r[j] * prevH[j]);
            }
            n[i] = (float) Math.tanh(accN);
            nextH[i] = (1.0f - z[i]) * prevH[i] + z[i] * n[i];
        }
        return nextH;
    }

    private static float sigmoid(float x) {
        return 1.0f / (1.0f + (float) Math.exp(-Mth.clamp(x, -12.0f, 12.0f)));
    }

    public synchronized void resetToDefaultWeights() {
        initPretrainedWeights();
        saveWeights();
    }

    private void initPretrainedWeights() {
        Random rng = new Random(20260326L);
        for (int i = 0; i < HIDDEN_DIM; i++) {
            bz[i] = -0.25f + (rng.nextFloat() - 0.5f) * 0.2f;
            br[i] = 0.1f + (rng.nextFloat() - 0.5f) * 0.2f;
            bn[i] = (rng.nextFloat() - 0.5f) * 0.12f;
            for (int k = 0; k < INPUT_DIM; k++) {
                wz[i][k] = (rng.nextFloat() - 0.5f) * 0.35f;
                wr[i][k] = (rng.nextFloat() - 0.5f) * 0.35f;
                wn[i][k] = (rng.nextFloat() - 0.5f) * 0.42f;
            }
            for (int j = 0; j < HIDDEN_DIM; j++) {
                uz[i][j] = (rng.nextFloat() - 0.5f) * 0.18f;
                ur[i][j] = (rng.nextFloat() - 0.5f) * 0.18f;
                un[i][j] = (rng.nextFloat() - 0.5f) * 0.22f;
            }
            // Structured features for organic error->acceleration coupling
            wn[i][0] += (i % 2 == 0 ? 0.55f : -0.45f);
            wn[i][1] += (i % 3 == 0 ? 0.45f : -0.35f);
            wOut[0][i] = (i % 2 == 0 ? 0.18f : -0.10f);
            wOut[1][i] = (i % 3 == 0 ? 0.16f : -0.09f);
        }
        bOut[0] = 0.22f;
        bOut[1] = 0.18f;
        yawGain = 0.68f;
        pitchGain = 0.56f;
    }

    public boolean hasSaveFailed() {
        return lastSaveFailed;
    }

    private void loadWeights() {
        if (weightsFile == null || !weightsFile.exists()) return;
        try (Reader r = Files.newBufferedReader(weightsFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return;
            JsonObject o = el.getAsJsonObject();

            float tmpYawGain = yawGain;
            float tmpPitchGain = pitchGain;
            float[] tmpBOut = bOut.clone();
            float[][] tmpWOut = new float[OUTPUT_DIM][HIDDEN_DIM];
            for (int rIdx = 0; rIdx < OUTPUT_DIM; rIdx++) {
                System.arraycopy(wOut[rIdx], 0, tmpWOut[rIdx], 0, HIDDEN_DIM);
            }

            if (o.has("yawGain")) {
                float v = o.get("yawGain").getAsFloat();
                if (!Float.isFinite(v)) return;
                tmpYawGain = v;
            }
            if (o.has("pitchGain")) {
                float v = o.get("pitchGain").getAsFloat();
                if (!Float.isFinite(v)) return;
                tmpPitchGain = v;
            }
            if (o.has("bOut")) {
                if (!o.get("bOut").isJsonArray()) return;
                JsonArray b = o.getAsJsonArray("bOut");
                for (int i = 0; i < Math.min(OUTPUT_DIM, b.size()); i++) {
                    float v = b.get(i).getAsFloat();
                    if (!Float.isFinite(v)) return;
                    tmpBOut[i] = v;
                }
            }
            if (o.has("wOut")) {
                if (!o.get("wOut").isJsonArray()) return;
                JsonArray rows = o.getAsJsonArray("wOut");
                for (int rIdx = 0; rIdx < Math.min(OUTPUT_DIM, rows.size()); rIdx++) {
                    if (!rows.get(rIdx).isJsonArray()) return;
                    JsonArray row = rows.get(rIdx).getAsJsonArray();
                    for (int cIdx = 0; cIdx < Math.min(HIDDEN_DIM, row.size()); cIdx++) {
                        float v = row.get(cIdx).getAsFloat();
                        if (!Float.isFinite(v)) return;
                        tmpWOut[rIdx][cIdx] = v;
                    }
                }
            }

            yawGain = tmpYawGain;
            pitchGain = tmpPitchGain;
            System.arraycopy(tmpBOut, 0, bOut, 0, OUTPUT_DIM);
            for (int rIdx = 0; rIdx < OUTPUT_DIM; rIdx++) {
                System.arraycopy(tmpWOut[rIdx], 0, wOut[rIdx], 0, HIDDEN_DIM);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean saveWeights() {
        if (weightsFile == null) {
            lastSaveFailed = true;
            return false;
        }
        try {
            JsonObject o = new JsonObject();
            o.addProperty("yawGain", yawGain);
            o.addProperty("pitchGain", pitchGain);
            JsonArray b = new JsonArray();
            for (float v : bOut) b.add(v);
            o.add("bOut", b);
            JsonArray rows = new JsonArray();
            for (int rIdx = 0; rIdx < OUTPUT_DIM; rIdx++) {
                JsonArray row = new JsonArray();
                for (int cIdx = 0; cIdx < HIDDEN_DIM; cIdx++) row.add(wOut[rIdx][cIdx]);
                rows.add(row);
            }
            o.add("wOut", rows);
            AtomicFileWriter.writeUtf8(weightsFile, w -> GSON.toJson(o, w));
            lastSaveFailed = false;
            return true;
        } catch (Throwable t) {
            lastSaveFailed = true;
            System.err.println("[OpticumeClient] Failed to save GRU weights: " + t.getMessage());
            return false;
        }
    }
}
