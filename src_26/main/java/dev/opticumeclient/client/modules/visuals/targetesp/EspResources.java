package dev.opticumeclient.client.modules.visuals.targetesp;

import net.minecraft.resources.Identifier;

public final class EspResources {
    private static final String NAMESPACE = "opticumeclient";

    private EspResources() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Identifier font(String name) {
        String path = name.startsWith("fonts/") ? name : "fonts/" + name;
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static Identifier image(String name) {
        String path = "targetesp/" + (name.startsWith("images/") ? name : "images/" + name);
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static Identifier shader(String name) {
        String path = "core/targetesp/" + (name.startsWith("core/") ? name.substring(5) : name);
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
