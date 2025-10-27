package io.gendora.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class IDGenerationServiceTest {

    @MockitoBean
    private SlotAllocatorService slotAllocatorService;

    @Autowired
    private IDGeneratorService idGeneratorService;

    @Test
    void shouldGenerateIdSuccessfully() {
        // Given
        when(slotAllocatorService.getAllocatedSlot()).thenReturn(0);

        // When
        long id = idGeneratorService.generateId();

        // Then
        assertTrue(id > 0);
        verify(slotAllocatorService, times(1)).getAllocatedSlot();
    }

    @Test
    void shouldReturnDifferentIdsForDifferentSlots() {
        // Given
        when(slotAllocatorService.getAllocatedSlot()).thenReturn(0, 1);

        // When
        long id1 = idGeneratorService.generateId();
        long id2 = idGeneratorService.generateId();

        // Then
        assertTrue(id1 > 0 && id2 > 0 && id1 != id2);
        verify(slotAllocatorService, times(2)).getAllocatedSlot();
    }

    @Test
    void shouldReturnDifferentIdsForDifferentSequences() {
        // Given
        when(slotAllocatorService.getAllocatedSlot()).thenReturn(0);

        // when
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1024; i++) {
            long id = idGeneratorService.generateId();
            ids.add(id);
        }

        // Then
        assertTrue(ids.size() == 1024 && ids.stream().allMatch(id -> id > 0));
        verify(slotAllocatorService, times(1024)).getAllocatedSlot();
    }

    @Test
    void shouldGetMetadataSuccessfully() {
        // Given
        when(slotAllocatorService.getAllocatedSlot()).thenReturn(0);
        long id = idGeneratorService.generateId();

        // When
        Map<String, Object> metadata = idGeneratorService.getMetadata(id);

        // Then
        assertTrue(metadata.containsKey("timestampDelta"));
        assertEquals(1759276800000L, metadata.get("epoch"));
        assertEquals(0L, metadata.get("machineID"));
        assertEquals(0L, metadata.get("sequenceID"));
        assertEquals("snowflake", metadata.get("algorithm"));

        verify(slotAllocatorService, times(1)).getAllocatedSlot();
    }

}
