package net.citizensnpcs.api.npc;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.api.util.SpigotUtil;
import net.citizensnpcs.api.util.Storage;

public class SimpleNPCDataStore implements NPCDataStore {
    private final Storage root;

    public SimpleNPCDataStore(Storage saves) {
        root = saves;
    }

    @Override
    public void clearData(NPC npc) {
        root.getKey("npc").removeKey(Integer.toString(npc.getId()));
    }

    @Override
    public void clearTraitData(Iterable<String> clear) {
        for (DataKey npcKey : root.getKey("npc").getSubKeys()) {
            Set<String> traitNames = Splitter.on(',').splitToStream(npcKey.getString("traitnames"))
                    .collect(Collectors.toSet());
            DataKey traitsKey = npcKey.getRelative("traits");
            for (String traitName : clear) {
                traitName = traitName.toLowerCase(Locale.ROOT);
                traitsKey.removeKey(traitName);
                traitNames.remove(traitName);
            }
            npcKey.setString("traitnames", Joiner.on(',').join(traitNames));
        }
    }

    @Override
    public int createUniqueNPCId(NPCRegistry registry) {
        DataKey rootKey = root.getKey("");
        int maxId = rootKey.getInt("last-created-npc-id", -1);
        if (maxId == -1 || registry.getById(maxId + 1) != null) {
            int highestId = Integer.MIN_VALUE;
            for (NPC npc : registry) {
                if (npc.getId() > highestId) {
                    highestId = npc.getId();
                }
            }
            maxId = highestId == Integer.MIN_VALUE ? 0 : highestId + 1;
        } else {
            maxId++;
        }
        rootKey.setInt("last-created-npc-id", maxId);
        return maxId;
    }

    @Override
    public void loadInto(NPCRegistry registry) {
        for (DataKey key : root.getKey("npc").getSubKeys()) {
            int id = Integer.parseInt(key.name());
            if (!key.keyExists("name")) {
                Messaging.logTr(LOAD_NAME_NOT_FOUND, id);
                continue;
            }
            String rawType = key.getRelative("traits").getString("type", "PLAYER");
            EntityType type = matchEntityType(rawType);
            if (type == null) {
                Messaging.logTr(LOAD_UNKNOWN_NPC_TYPE, rawType);
                continue;
            }
            NPC npc = registry.createNPC(type,
                    !key.getString("uuid").isEmpty() ? UUID.fromString(key.getString("uuid")) : UUID.randomUUID(), id,
                    key.getString("name"));
            npc.load(key);
        }
    }

    @Override
    public void reloadFromSource() {
        root.load();
    }

    @Override
    public void saveToDisk() {
        root.saveAsync();
    }

    @Override
    public void saveToDiskImmediate() {
        root.save();
    }

    @Override
    public void store(NPC npc) {
        npc.save(root.getKey("npc." + npc.getId()));
    }

    @Override
    public void storeAll(NPCRegistry registry) {
        for (NPC npc : registry) {
            store(npc);
        }
    }

    public static NPCDataStore create(Storage saves) {
        return new SimpleNPCDataStore(saves);
    }

    private static EntityType matchEntityType(String toMatch) {
        if (toMatch == null || toMatch.isEmpty()) {
            return null;
        }
        try {
            NamespacedKey key = SpigotUtil.getKey(toMatch);
            if (key != null) {
                EntityType byRegistry = Registry.ENTITY_TYPE.get(key);
                if (byRegistry != null) {
                    return byRegistry;
                }
            }
        } catch (Throwable ignored) {
        }
        String normalized = toMatch;
        int colonIdx = normalized.indexOf(':');
        if (colonIdx >= 0) {
            normalized = normalized.substring(colonIdx + 1);
        }
        EntityType type = null;
        try {
            type = EntityType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            try {
                type = EntityType.fromName(normalized);
            } catch (Throwable ignored) {
            }
        }
        if (type != null) {
            return type;
        }
        return matchEnum(EntityType.values(), normalized);
    }

    private static <T extends Enum<?>> T matchEnum(T[] values, String toMatch) {
        T type = null;
        for (T check : values) {
            String name = check.name();
            if (name.matches(toMatch) || name.equalsIgnoreCase(toMatch)
                    || name.replace("_", "").equalsIgnoreCase(toMatch)
                    || name.replace('_', '-').equalsIgnoreCase(toMatch)
                    || name.replace('_', ' ').equalsIgnoreCase(toMatch) || name.startsWith(toMatch)) {
                type = check;
                break;
            }
        }
        return type;
    }

    private static final String LOAD_NAME_NOT_FOUND = "citizens.notifications.npc-name-not-found";
    private static final String LOAD_UNKNOWN_NPC_TYPE = "citizens.notifications.unknown-npc-type";
}
