package com.collab.platform;

import com.collab.platform.dto.CollabAction;
import com.collab.platform.dto.CollabMessage;
import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.dto.RoomSyncPayload;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.service.BoardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReconnectSyncIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private BoardService boardService;

    private WebSocketStompClient stompClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName("Reconnect test: disconnect client, mutate state in background, reconnect client, verify catch-up sync receives persisted state")
    void testDisconnectMutateReconnectSync() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws-collab-raw";
        String roomId = "reconnect-room-" + System.currentTimeMillis();

        // 1. Initial item created
        boardService.createItem(CreateItemRequest.builder()
                .roomId(roomId)
                .title("Initial Task Before Disconnect")
                .description("Persisted in DB")
                .status(ItemStatus.TODO)
                .sender("System")
                .build());

        // 2. Client 1 connects for the first time
        BlockingQueue<CollabMessage> client1Queue = new LinkedBlockingQueue<>();
        StompSession session1 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        session1.subscribe("/topic/rooms/" + roomId, new TestFrameHandler(client1Queue));

        session1.send("/app/room/" + roomId + "/join", Map.of("username", "Dave", "color", "#3B82F6"));
        CollabMessage initialSync = client1Queue.poll(5, TimeUnit.SECONDS);
        assertThat(initialSync).isNotNull();

        // 3. Client 1 DISCONNECTS (network drop)
        session1.disconnect();
        Thread.sleep(500);

        // 4. While Client 1 is offline, other collaborators mutate the room state in DB
        BoardItem offlineItem1 = boardService.createItem(CreateItemRequest.builder()
                .roomId(roomId)
                .title("Offline Task A: Sharded Partitioning")
                .description("Created while Dave was disconnected")
                .status(ItemStatus.IN_PROGRESS)
                .sender("Eve")
                .build());

        BoardItem offlineItem2 = boardService.createItem(CreateItemRequest.builder()
                .roomId(roomId)
                .title("Offline Task B: Redis Cluster Relay")
                .description("Another task created during outage")
                .status(ItemStatus.DONE)
                .sender("Frank")
                .build());

        // 5. Client 1 RECONNECTS (new session)
        BlockingQueue<CollabMessage> reconnectQueue = new LinkedBlockingQueue<>();
        StompSession reconnectedSession = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        reconnectedSession.subscribe("/topic/rooms/" + roomId, new TestFrameHandler(reconnectQueue));

        // Client 1 sends Join / Sync request to catch up
        reconnectedSession.send("/app/room/" + roomId + "/join", Map.of("username", "Dave-Reconnected", "color", "#3B82F6"));

        // Wait for ROOM_SYNC message
        CollabMessage syncMsg = null;
        for (int i = 0; i < 5; i++) {
            CollabMessage candidate = reconnectQueue.poll(3, TimeUnit.SECONDS);
            if (candidate != null && candidate.getAction() == CollabAction.ROOM_SYNC) {
                syncMsg = candidate;
                break;
            }
        }

        assertThat(syncMsg).as("Reconnecting client must receive ROOM_SYNC message").isNotNull();
        assertThat(syncMsg.getPayload()).isNotNull();

        // Convert payload back to RoomSyncPayload
        RoomSyncPayload payload = objectMapper.convertValue(syncMsg.getPayload(), RoomSyncPayload.class);
        assertThat(payload.getItems()).hasSize(3);

        List<String> titles = payload.getItems().stream().map(BoardItem::getTitle).toList();
        assertThat(titles).contains(
                "Initial Task Before Disconnect",
                "Offline Task A: Sharded Partitioning",
                "Offline Task B: Redis Cluster Relay"
        );

        reconnectedSession.disconnect();
    }

    private static class TestFrameHandler implements StompFrameHandler {
        private final BlockingQueue<CollabMessage> queue;

        public TestFrameHandler(BlockingQueue<CollabMessage> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return CollabMessage.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.offer((CollabMessage) payload);
        }
    }
}
