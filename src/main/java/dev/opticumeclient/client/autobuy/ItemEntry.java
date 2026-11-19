package dev.opticumeclient.client.autobuy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemEntry {
    public String itemId;
    public String displayName;
    public String searchQuery;
    public String loreKeyword;
    public String potionEffect;
    public long maxUnitPrice;
    public long marketPrice;
    public long minUnitPrice;
    public int minDurabilityPercent;
    public boolean isArmor;
    public boolean matchAnySword;
    public boolean requireCustom;
    public boolean enabled = true;
    public String server = "HW";
    public List<Marker> markers = new ArrayList<>();

    public ItemEntry() {
    }

    public ItemEntry(String itemId, long maxUnitPrice) {
        this.itemId = itemId;
        this.maxUnitPrice = maxUnitPrice;
    }

    public ItemEntry(String itemId, String displayName, long maxUnitPrice) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.maxUnitPrice = maxUnitPrice;
    }

    public boolean matches(String otherItemId) {
        if (otherItemId == null) {
            return false;
        }
        return this.matchAnySword ? otherItemId.endsWith("_sword") : otherItemId.equals(this.itemId);
    }

    public boolean isArmor() {
        if (this.isArmor) {
            return true;
        }
        if (this.itemId != null) {
            String id = this.itemId.toLowerCase(Locale.ROOT);
            return id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings")
                    || id.endsWith("_boots") || id.contains("turtle_helmet") || id.contains("elytra");
        }
        return false;
    }

    public String label() {
        String name = this.displayName != null && !this.displayName.isBlank() ? this.displayName : this.itemId;
        if (this.loreKeyword != null && !this.loreKeyword.isBlank()) {
            name = name + " [" + this.loreKeyword + "]";
        }
        if (this.matchAnySword) {
            name = "Меч+" + name;
        }
        return name;
    }

    public String query() {
        if (this.searchQuery != null && !this.searchQuery.isBlank()) {
            return this.searchQuery;
        }
        return this.displayName != null && !this.displayName.isBlank() ? this.displayName : this.itemId;
    }

    public List<Marker> resolveMarkers() {
        if (this.markers != null && !this.markers.isEmpty()) {
            return this.markers;
        }

        String nameLow = this.displayName == null ? "" : this.displayName.trim().toLowerCase(Locale.ROOT);
        String queryLow = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);

        for (ItemEntry db : ItemDatabase.LIST) {
            if (db.markers == null || db.markers.isEmpty()) continue;
            if (this.server != null && db.server != null && !this.server.equalsIgnoreCase(db.server)) continue;

            String dbName = db.displayName == null ? "" : db.displayName.toLowerCase(Locale.ROOT);
            String dbQuery = db.searchQuery == null ? "" : db.searchQuery.toLowerCase(Locale.ROOT);
            boolean idOk = (db.matchAnySword || (db.itemId != null && db.itemId.equals(this.itemId)))
                    && (!db.matchAnySword || (this.itemId != null && this.itemId.endsWith("_sword")));
            if (idOk) {
                if (!nameLow.isEmpty() && nameLow.equals(dbName)) {
                    return db.markers;
                }
                if (!queryLow.isEmpty() && queryLow.equals(dbQuery)) {
                    return db.markers;
                }
            }
        }
        return this.markers;
    }
}
