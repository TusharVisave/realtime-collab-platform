package com.collab.platform;

import com.collab.platform.dto.CollabAction;
import com.collab.platform.dto.CollabMessage;
import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.model.ItemStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoomIsolationIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName("Room isolation test: two users in Room A, one in Room B — confirm Room A's broadcast never reaches Room B")
    void testRoomIsolation() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws-collab-raw";
        String roomA = "room-alpha";
        String roomB = "room-bravo";

        BlockingQueue<CollabMessage> queueA1 = new LinkedBlockingQueue<>();
        BlockingQueue<CollabMessage> queueA2 = new LinkedBlockingQueue<>();
        BlockingQueue<CollabMessage> queueB1 = new LinkedBlockingQueue<>();

        // Connect User A1 (Room A)
        StompSession sessionA1 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessionA1.subscribe("/topic/rooms/" + roomA, new TestFrameHandler(queueA1));

        // Connect User A2 (Room A)
        StompSession sessionA2 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessionA2.subscribe("/topic/rooms/" + roomA, new TestFrameHandler(queueA2));

        // Connect User B1 (Room B)
        StompSession sessionB1 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessionB1.subscribe("/topic/rooms/" + roomB, new TestFrameHandler(queueB1));

        Thread.sleep(300);

        // User A1 creates an item in Room A
        CreateItemRequest itemRoomA = CreateItemRequest.builder()
                .roomId(roomA)
                .title("Secret Feature for Room Alpha")
                .description("Top secret isolated item")
                .status(ItemStatus.TODO)
                .color("#EF4444")
                .sender("Alice-Alpha")
                .build();

        sessionA1.send("/app/room/" + roomA + "/create", itemRoomA);

        // Verify A1 and A2 received it
        CollabMessage msgA1 = queueA1.poll(5, TimeUnit.SECONDS);
        assertThat(msgA1).isNotNull();
        assertThat(msgA1.getRoomId()).isEqualTo(roomA);

        CollabMessage msgA2 = queueA2.poll(5, TimeUnit.SECONDS);
        assertThat(msgA2).isNotNull();
        assertThat(msgA2.getRoomId()).isEqualTo(roomA);

        // Verify User B1 received NOTHING from Room A (queue remains null)
        CollabMessage msgB1 = queueB1.poll(1500, TimeUnit.MILLISECONDS);
        assertThat(msgB1).as("Room B subscriber should NEVER receive Room A broadcasts").isNull();

        sessionA1.disconnect();
        sessionA2.disconnect();
        sessionB1.disconnect();
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
