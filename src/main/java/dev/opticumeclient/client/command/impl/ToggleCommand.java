package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .toggle <module>} / {@code .t <module>}
 */
public final class ToggleCommand extends ClientCommand {
    public ToggleCommand() {
        super("toggle", "Включить или выключить модуль (.toggle <модуль>)", "t");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(argument("module", StringArgumentType.greedyString()).executes(ctx -> {
            String query = StringArgumentType.getString(ctx, "module").trim();
            String clean = query.replace("_", "").replace(" ", "");
            Module target = null;
            for (Module m : ModuleManager.getInstance().getModules()) {
                if (m.getName().equalsIgnoreCase(query) || m.getName().replace(" ", "").equalsIgnoreCase(clean)) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                ChatUtils.sendError("Модуль \"" + query + "\" не найден.");
                return SINGLE_SUCCESS;
            }
            target.toggle();
            ConfigManager.getInstance().saveActiveConfigQuietly();
            ChatUtils.sendClientMessage("Модуль §b" + target.getName() + " §fтеперь "
                    + (target.isEnabled() ? "§aВКЛЮЧЁН" : "§cВЫКЛЮЧЕН") + "§f.");
            return SINGLE_SUCCESS;
        }));
    }
}
