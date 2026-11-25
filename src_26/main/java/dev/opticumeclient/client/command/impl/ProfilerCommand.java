package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.ModuleProfiler;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;
import java.util.Locale;

/**
 * {@code .profiler [reset|on|off]} prints top modules by tick + render time in chat.
 */
public final class ProfilerCommand extends ClientCommand {
    public ProfilerCommand() {
        super("profiler", "Статистика производительности модулей (.profiler [reset|on|off])");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> {
            List<ModuleProfiler.Sample> top = ModuleProfiler.getInstance().getTopSamples(8);
            if (top.isEmpty()) {
                ChatUtils.sendClientMessage("Данных профайлера пока нет.");
            } else {
                ChatUtils.sendClientMessage("Топ модулей по нагрузке (avg/frame, peak):");
                for (ModuleProfiler.Sample s : top) {
                    ChatUtils.sendClientMessage(String.format(Locale.US,
                            " §b%s§7: avg §e%.3f ms§7, peak §c%.3f ms", s.moduleName, s.avgTotalMs(), s.peakMs()));
                }
            }
            return SINGLE_SUCCESS;
        });

        builder.then(literal("reset").executes(ctx -> {
            ModuleProfiler.getInstance().reset();
            ChatUtils.sendClientMessage("Счётчики профайлера сброшены.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("on").executes(ctx -> {
            ModuleProfiler.getInstance().setRecording(true);
            ChatUtils.sendClientMessage("Профайлер модулей включён.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(ctx -> {
            ModuleProfiler.getInstance().setRecording(false);
            ChatUtils.sendClientMessage("Профайлер модулей приостановлен.");
            return SINGLE_SUCCESS;
        }));
    }
}
