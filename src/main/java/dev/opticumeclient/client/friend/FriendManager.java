package dev.opticumeclient.client.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.player.Player; 
 

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class FriendManager {
    private static final FriendManager INSTANCE = new FriendManager();
    private final Set<String> friends = new HashSet<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File configFile;

    private FriendManager() {
        initFile();
        load();
    }

    public static FriendManager getInstance() {
        return INSTANCE;
    }

    private void initFile() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File dir = new File(gameDir, "opticumeclient");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "friends.json");
        if (!configFile.exists()) {
            File legacyFile = new File(gameDir, "config/optimusprime/friends.json");
            if (legacyFile.exists()) {
                try {
                    java.nio.file.Files.copy(legacyFile.toPath(), configFile.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isFriend(String name) {
        if (name == null) return false;
        return friends.contains(name.toLowerCase().trim());
    }

    public boolean isFriend(Entity entity) {
        if (entity instanceof Player player) {
            return isFriend(player.getName().getString());
        }
        return false;
    }

    public boolean addFriend(String name) {
        if (name == null || name.isBlank()) return false;
        boolean added = friends.add(name.toLowerCase().trim());
        if (added) {
            save();
            sendFeedback("§a[OpticumeClient] §fAdded §b" + name + " §fto friends list.");
        }
        return added;
    }

    public boolean removeFriend(String name) {
        if (name == null || name.isBlank()) return false;
        boolean removed = friends.remove(name.toLowerCase().trim());
        if (removed) {
            save();
            sendFeedback("§c[OpticumeClient] §fRemoved §e" + name + " §ffrom friends list.");
        }
        return removed;
    }

    public void toggleFriend(String name) {
        if (isFriend(name)) {
            removeFriend(name);
        } else {
            addFriend(name);
        }
    }

    public Set<String> getFriends() {
        return friends;
    }

    public int clearFriends() {
        int count = friends.size();
        if (count > 0) {
            friends.clear();
            save();
        }
        return count;
    }

    public void load() {
        if (configFile != null && configFile.exists()) {
            try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                Set<String> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    friends.clear();
                    for (String s : loaded) {
                        friends.add(s.toLowerCase().trim());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        if (configFile == null) initFile();
        try (Writer writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(friends, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendFeedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
}
