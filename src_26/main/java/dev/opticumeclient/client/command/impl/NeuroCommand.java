package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.combat.neuro.GruRotationModel;
import dev.opticumeclient.client.combat.neuro.NeuroDatasetRecorder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .neuro <record|stop|train|stats|reset>} controls GRU neuro-rotation dataset collection & training.
 */
public final class NeuroCommand extends ClientCommand {
    public NeuroCommand() {
        super("neuro", "Датасет и обучение GRU нейро-ротаций (.neuro record|stop|train|stats|reset)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("record").executes(ctx -> {
            NeuroDatasetRecorder.getInstance().startRecording();
            ChatUtils.sendClientMessage("Запись датасета движений мыши §aвключена§7.");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("stop").executes(ctx -> {
            NeuroDatasetRecorder.getInstance().stopRecording();
            ChatUtils.sendClientMessage("Запись остановлена. Сэмплов в датасете: §e" + NeuroDatasetRecorder.getInstance().getSampleCount());
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("stats").executes(ctx -> {
            NeuroDatasetRecorder rec = NeuroDatasetRecorder.getInstance();
            ChatUtils.sendClientMessage("GRU Neuro: статус=" + (rec.isRecording() ? "§aREC" : "§7IDLE")
                    + "§7, сэмплов=§e" + rec.getSampleCount());
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("train").executes(ctx -> {
            var samples = NeuroDatasetRecorder.getInstance().getSamples();
            int used = GruRotationModel.getInstance().trainFromDataset(samples, 6, 0.015f);
            if (used <= 0) {
                ChatUtils.sendError("Нужно минимум 8 сэмплов (запиши бой через .neuro record).");
            } else if (GruRotationModel.getInstance().hasSaveFailed()) {
                ChatUtils.sendError("GRU модель обучена на §e" + used + " §cсэмплах (в памяти), но не удалось сохранить веса на диск!");
            } else {
                ChatUtils.sendClientMessage("GRU модель обучена на §e" + used + " §7сэмплах и сохранена в opticumeclient/neuro/.");
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("reset").executes(ctx -> {
            NeuroDatasetRecorder.getInstance().clear();
            GruRotationModel.getInstance().resetToDefaultWeights();
            ChatUtils.sendClientMessage("Датасет и веса GRU сброшены к базовым.");
            return SINGLE_SUCCESS;
        }));
    }
}
