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
public class WebSocketIntegrationTest {

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
    @DisplayName("Integration test: Client 1 sends an item creation message, and Client 2 receives it over STOMP")
    void testWebSocketBroadcastBetweenClients() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws-collab-raw";
        String roomId = "integration-room-1";

        BlockingQueue<CollabMessage> client1Queue = new LinkedBlockingQueue<>();
        BlockingQueue<CollabMessage> client2Queue = new LinkedBlockingQueue<>();

        // Connect Client 1
        StompSession session1 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        assertThat(session1.isConnected()).isTrue();

        // Connect Client 2
        StompSession session2 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        assertThat(session2.isConnected()).isTrue();

        // Client 1 subscribes
        session1.subscribe("/topic/rooms/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CollabMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                client1Queue.offer((CollabMessage) payload);
            }
        });

        // Client 2 subscribes
        session2.subscribe("/topic/rooms/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CollabMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                client2Queue.offer((CollabMessage) payload);
            }
        });

        Thread.sleep(300); // allow subscriptions to register

        // Client 1 sends create item message
        CreateItemRequest createReq = CreateItemRequest.builder()
                .roomId(roomId)
                .title("Distributed Consensus Spike")
                .description("Verify Raft vs Paxos trade-offs")
                .status(ItemStatus.TODO)
                .color("#3B82F6")
                .sender("Alice")
                .build();

        session1.send("/app/room/" + roomId + "/create", createReq);

        // Verify Client 2 receives the broadcast
        CollabMessage receivedByClient2 = client2Queue.poll(5, TimeUnit.SECONDS);
        assertThat(receivedByClient2).isNotNull();
        assertThat(receivedByClient2.getAction()).isEqualTo(CollabAction.ITEM_CREATED);
        assertThat(receivedByClient2.getRoomId()).isEqualTo(roomId);
        assertThat(receivedByClient2.getSender()).isEqualTo("Alice");

        // Verify Client 1 also receives the broadcast
        CollabMessage receivedByClient1 = client1Queue.poll(5, TimeUnit.SECONDS);
        assertThat(receivedByClient1).isNotNull();
        assertThat(receivedByClient1.getAction()).isEqualTo(CollabAction.ITEM_CREATED);

        session1.disconnect();
        session2.disconnect();
    }
}
