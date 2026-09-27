package net.fihnet.client.service;

import net.fihnet.client.net.FihNetServerConfig;
import net.fihnet.client.net.HttpJson;
import net.fihnet.client.util.JsonObject;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Real FihNet backend client: friends, presence, chat and credits.
 *
 * <p>Talks to the FihNet server over HTTP. A background thread long-polls the
 * event stream so incoming chat and presence changes arrive without polling spam.
 */
public final class RemoteFihNetService implements FihNetService {
    private static final Logger LOG = LogManager.getLogger("FihNet/Service");
    private static final int PRESENCE_INTERVAL_TICKS = 100;

    private final Supplier<String> tokenSupplier;
    private final CopyOnWriteArrayList<String> inbox = new CopyOnWriteArrayList<>();

    private volatile long lastSeq;
    private volatile boolean running;
    private volatile String lastIncoming = "";
    private int tickCounter;

    public RemoteFihNetService(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    /** Recent messages, oldest first. Used by the UI. */
    public List<String> inbox() {
        return List.copyOf(inbox);
    }

    public String lastIncoming() {
        return lastIncoming;
    }

    private String base() {
        return FihNetServerConfig.serverUrl();
    }

    private String token() {
        String value = tokenSupplier.get();
        return value == null ? "" : value;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread pump = new Thread(this::pumpLoop, "FihNet-Events");
        pump.setDaemon(true);
        pump.start();
        LOG.info("FihNet event stream started against {}", base());
    }

    public void stop() {
        running = false;
    }

    private void pumpLoop() {
        while (running) {
            String auth = token();
            if (auth.isEmpty()) {
                sleep(2000);
                continue;
            }
            try {
                HttpJson.Response response = HttpJson.get(base() + "/api/events?since=" + lastSeq, auth, Duration.ofSeconds(30));
                if (response.ok()) {
                    applyEvents(response.json());
                } else if (response.status() == 401) {
                    sleep(5000);
                } else {
                    sleep(2000);
                }
            } catch (Exception e) {
                LOG.debug("Event poll failed: {}", e.getMessage());
                sleep(3000);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    private void applyEvents(JsonObject root) {
        long next = root.getLong("next");
        if (next > lastSeq) {
            lastSeq = next;
        }
        Object events = root.get("events");
        if (!(events instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            if (!(entry instanceof java.util.Map<?, ?> event)) {
                continue;
            }
            String type = rawString(event.get("type"));
            if ("chat".equals(type)) {
                String line = rawString(event.get("from")) + ": " + rawString(event.get("message"));
                lastIncoming = line;
                inbox.add(line);
                while (inbox.size() > 200) {
                    inbox.remove(0);
                }
                LOG.info("[FihNet chat] {}", line);
            }
        }
    }

    private static String rawString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public CompletableFuture<List<FriendPresence>> friends() {
        return CompletableFuture.supplyAsync(() -> {
            String auth = token();
            if (auth.isEmpty()) {
                return List.<FriendPresence>of();
            }
            try {
                HttpJson.Response response = HttpJson.get(base() + "/api/friends", auth);
                if (!response.ok()) {
                    LOG.warn("Friend list request returned {}", response.status());
                    return List.<FriendPresence>of();
                }
                Object raw = response.json().get("friends");
                if (!(raw instanceof List<?> list)) {
                    return List.<FriendPresence>of();
                }
                List<FriendPresence> result = new ArrayList<>();
                for (Object entry : list) {
                    if (entry instanceof java.util.Map<?, ?> map) {
                        FriendPresence presence = toPresence(map);
                        if (presence != null) {
                            result.add(presence);
                        }
                    }
                }
                return result;
            } catch (Exception e) {
                LOG.warn("Could not load friends: {}", e.getMessage());
                return List.<FriendPresence>of();
            }
        });
    }

    private static FriendPresence toPresence(java.util.Map<?, ?> map) {
        try {
            String idRaw = rawString(map.get("id"));
            String username = rawString(map.get("username"));
            if (username.isEmpty()) {
                return null;
            }
            UUID id = idRaw.isEmpty() ? UUID.randomUUID() : UUID.fromString(idRaw);
            boolean online = Boolean.TRUE.equals(map.get("online"));
            boolean inGame = Boolean.TRUE.equals(map.get("inGame"));
            boolean modded = Boolean.TRUE.equals(map.get("modded"));
            String address = rawString(map.get("serverAddress"));
            String serverName = rawString(map.get("serverName"));
            int players = map.get("playerCount") instanceof Number number ? number.intValue() : 0;
            return new FriendPresence(id, username, online, inGame,
                    address.isEmpty() ? null : address, serverName, players, modded);
        } catch (Exception e) {
            LOG.warn("Skipping malformed friend entry: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public CompletableFuture<Void> sendChatMessage(String friendName, String message) {
        return CompletableFuture.runAsync(() -> {
            String auth = token();
            if (auth.isEmpty()) {
                throw new IllegalStateException("Not signed in to FihNet");
            }
            JsonObject body = new JsonObject();
            body.set("to", friendName);
            body.set("message", message);
            try {
                HttpJson.Response response = HttpJson.post(base() + "/api/chat", body.toString(), auth);
                if (!response.ok()) {
                    throw new IllegalStateException("Server returned " + response.status());
                }
                inbox.add("me to " + friendName + ": " + message);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> joinWorld(FriendPresence friend) {
        return CompletableFuture.runAsync(() -> {
            if (friend == null) {
                throw new IllegalArgumentException("No friend selected");
            }
            String address = friend.getServerAddress();
            if (address == null || address.isEmpty()) {
                throw new IllegalStateException(friend.getUsername() + " is not sharing a world address");
            }
            LOG.info("Join requested for {} at {} - tunnel relay arrives in the hosting step", friend.getUsername(), address);
        });
    }

    @Override
    public CompletableFuture<Integer> credits() {
        return CompletableFuture.supplyAsync(() -> {
            String auth = token();
            if (auth.isEmpty()) {
                return 0;
            }
            try {
                HttpJson.Response response = HttpJson.get(base() + "/api/credits", auth);
                if (response.ok()) {
                    return response.json().getInt("credits");
                }
            } catch (Exception e) {
                LOG.debug("Credits request failed: {}", e.getMessage());
            }
            return 0;
        });
    }

    /**
     * Called every client tick; reports presence roughly every 5 seconds.
     * Server address and player count detection arrive with the hosting step.
     */
    public void tickPresence(Minecraft client) {
        if (client == null || ++tickCounter < PRESENCE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        String auth = token();
        if (auth.isEmpty()) {
            return;
        }
        final boolean inGame = client.player != null;
        CompletableFuture.runAsync(() -> {
            JsonObject body = new JsonObject();
            body.set("inGame", inGame);
            body.set("serverAddress", "");
            body.set("serverName", inGame ? "In game" : "");
            body.set("playerCount", 0);
            try {
                HttpJson.post(base() + "/api/presence", body.toString(), auth);
            } catch (Exception e) {
                LOG.debug("Presence update failed: {}", e.getMessage());
            }
        });
    }
}
