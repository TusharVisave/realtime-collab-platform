package com.collab.platform.service;

import com.collab.platform.dto.PresenceUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PresenceService {

    // sessionId -> PresenceSessionInfo
    private final Map<String, SessionPresence> sessions = new ConcurrentHashMap<>();
    
    // roomId -> Set of sessionIds
    private final Map<String, Set<String>> roomSessions = new ConcurrentHashMap<>();

    private record SessionPresence(String sessionId, String roomId, PresenceUser user) {}

    private static final String[] PALETTE = {
        "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#06B6D4", "#F97316"
    };

    public synchronized PresenceUser registerUser(String sessionId, String roomId, String username, String requestedColor) {
        String color = (requestedColor != null && !requestedColor.isBlank())
                ? requestedColor
                : PALETTE[Math.abs((username + sessionId).hashCode()) % PALETTE.length];

        PresenceUser user = PresenceUser.builder()
                .sessionId(sessionId)
                .username((username != null && !username.isBlank()) ? username : "User-" + sessionId.substring(0, Math.min(sessionId.length(), 4)))
                .color(color)
                .joinedAt(System.currentTimeMillis())
                .currentEditingItemId(null)
                .build();

        // If session was in another room, remove first
        SessionPresence oldPresence = sessions.remove(sessionId);
        if (oldPresence != null) {
            Set<String> oldRoom = roomSessions.get(oldPresence.roomId());
            if (oldRoom != null) {
                oldRoom.remove(sessionId);
                if (oldRoom.isEmpty()) {
                    roomSessions.remove(oldPresence.roomId());
                }
            }
        }

        sessions.put(sessionId, new SessionPresence(sessionId, roomId, user));
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        log.info("Registered user {} in room {} (session: {})", user.getUsername(), roomId, sessionId);
        return user;
    }

    public synchronized void updateEditingItem(String sessionId, String itemId) {
        SessionPresence presence = sessions.get(sessionId);
        if (presence != null) {
            presence.user().setCurrentEditingItemId(itemId);
        }
    }

    public synchronized Optional<SessionPresenceResult> removeSession(String sessionId) {
        SessionPresence removed = sessions.remove(sessionId);
        if (removed == null) {
            return Optional.empty();
        }

        Set<String> set = roomSessions.get(removed.roomId());
        if (set != null) {
            set.remove(sessionId);
            if (set.isEmpty()) {
                roomSessions.remove(removed.roomId());
            }
        }

        log.info("Removed user {} from room {} on disconnect (session: {})",
                removed.user().getUsername(), removed.roomId(), sessionId);
        return Optional.of(new SessionPresenceResult(removed.roomId(), removed.user()));
    }

    public List<PresenceUser> getUsersInRoom(String roomId) {
        Set<String> sessionIds = roomSessions.get(roomId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PresenceUser> list = new ArrayList<>();
        for (String sId : sessionIds) {
            SessionPresence sp = sessions.get(sId);
            if (sp != null) {
                list.add(sp.user());
            }
        }
        return list;
    }

    public Optional<String> getRoomForSession(String sessionId) {
        SessionPresence sp = sessions.get(sessionId);
        return (sp != null) ? Optional.of(sp.roomId()) : Optional.empty();
    }

    public record SessionPresenceResult(String roomId, PresenceUser user) {}
}
