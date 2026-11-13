package dev.optimusprime.client.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.player.Player; 
 

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
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
        File dir = new File(Minecraft.getInstance().gameDirectory, "config/optimusprime");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "friends.json");
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
            sendFeedback("§a[Opticume] §fИгрок §b" + name + " §fдобавлен в список друзей!");
        }
        return added;
    }

    public boolean removeFriend(String name) {
        if (name == null || name.isBlank()) return false;
        boolean removed = friends.remove(name.toLowerCase().trim());
        if (removed) {
            save();
            sendFeedback("§c[Opticume] §fИгрок §e" + name + " §fудален из списка друзей.");
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

    public void load() {
        if (configFile != null && configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
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
        try (FileWriter writer = new FileWriter(configFile)) {
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
