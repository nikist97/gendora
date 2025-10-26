package io.gendora.services;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
    properties = {
        "spring.application.name=test-app",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SlotAllocatorServiceTest {

    @Autowired
    private RedisCommands<String, String> redis;

    @Autowired
    private SlotAllocatorService slotAllocatorService;

    @TestConfiguration
    static class MockRedisConfiguration {

        @Bean
        @Primary
        public StatefulRedisConnection<String, String> redisConnection() {
            @SuppressWarnings("unchecked")
            StatefulRedisConnection<String, String> redisConnection = mock(StatefulRedisConnection.class);

            @SuppressWarnings("unchecked")
            RedisCommands<String, String> redis = mock(RedisCommands.class);

            when(redis.setnx(anyString(), anyString())).thenReturn(false, false, true);
            when(redis.expire(anyString(), anyLong())).thenReturn(true);

            when(redisConnection.sync()).thenReturn(redis);

            return redisConnection;
        }
    }

    @Test
    void shouldAllocateFirstAvailableSlot() {
        // Given
        when(redis.setnx(anyString(), anyString())).thenReturn(true);
        when(redis.expire(anyString(), anyLong())).thenReturn(true);
        
        // When
        int slot = slotAllocatorService.getAllocatedSlot();
        
        // Then
        assertEquals(2, slot);
        verify(redis, times(1)).setnx(eq("test-app:slot:0"), anyString());
        verify(redis, times(1)).setnx(eq("test-app:slot:1"), anyString());
        verify(redis, times(1)).setnx(eq("test-app:slot:2"), anyString());
        verify(redis, atLeastOnce()).expire(eq("test-app:slot:2"), anyLong());
        verifyNoMoreInteractions(redis);
    }

    @Test
    void shouldReturnInitializedSlotOnMultipleCalls() {
        // Given
        when(redis.setnx(anyString(), anyString())).thenReturn(true);
        when(redis.expire(anyString(), anyLong())).thenReturn(true);

        // When
        int slot1 = slotAllocatorService.getAllocatedSlot();
        int slot2 = slotAllocatorService.getAllocatedSlot();
        int slot3 = slotAllocatorService.getAllocatedSlot();

        // Then
        assertEquals(2, slot1);
        assertEquals(2, slot2);
        assertEquals(2, slot3);

        verify(redis, times(1)).setnx(eq("test-app:slot:0"), anyString());
        verify(redis, times(1)).setnx(eq("test-app:slot:1"), anyString());
        verify(redis, times(1)).setnx(eq("test-app:slot:2"), anyString());
        verify(redis, atLeastOnce()).expire(eq("test-app:slot:2"), anyLong());
        verifyNoMoreInteractions(redis);
    }

    @Test
    void shouldReleaseSlotOnCleanup() {
        // Given
        when(redis.setnx(anyString(), anyString())).thenReturn(true);
        when(redis.expire(anyString(), anyLong())).thenReturn(true);
        slotAllocatorService.getAllocatedSlot();
        
        // Capture the instanceID that was stored
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redis, times(3)).setnx(anyString(), captor.capture());
        String instanceID = captor.getValue();
        
        when(redis.get(anyString())).thenReturn(instanceID);

        // When
        slotAllocatorService.cleanup();

        // Then
        verify(redis, atLeastOnce()).expire(eq("test-app:slot:2"), anyLong());
        verify(redis, times(1)).get("test-app:slot:2");
        verify(redis, times(1)).del("test-app:slot:2");
        verifyNoMoreInteractions(redis);
    }
}
