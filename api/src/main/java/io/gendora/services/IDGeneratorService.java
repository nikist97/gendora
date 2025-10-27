package io.gendora.services;

import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class IDGeneratorService {
    private static final long EPOCH = 1759276800000L; // 2025-10-01 00:00:00 UTC
    
    // Bit layout: [Sign: 1 bit] [Timestamp (since epoch): 41 bits] [MachineID: 10 bits] [Sequence: 12 bits]
    private static final long TIMESTAMP_BITS = 41;
    private static final long MACHINE_ID_BITS = 10;
    private static final long SEQUENCE_BITS = 12;
    
    private static final long SEQUENCE_SHIFT = 0;
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = MACHINE_ID_SHIFT + MACHINE_ID_BITS;

    private static final long MAX_MACHINE_ID = (1 << MACHINE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE_ID = (1 << SEQUENCE_BITS) - 1;
    private static final long MAX_TIMESTAMP_DELTA = (1L << TIMESTAMP_BITS) - 1;

    @Autowired
    private SlotAllocatorService slotAllocator;

    private long lastTimestamp = -1L;
    private long sequenceID = 0;

    public synchronized long generateId() {
        long machineId = slotAllocator.getAllocatedSlot();
        if (machineId > MAX_MACHINE_ID) {
            throw new RuntimeException("Machine ID exceeds maximum value: " + machineId);
        }

        long timestamp = getCurrentTimestamp();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id for " + 
                (lastTimestamp - timestamp) + " milliseconds");
        }
        
        if (timestamp == lastTimestamp) {
            sequenceID++;
            if (sequenceID > MAX_SEQUENCE_ID) {
                // Sequence overflow, wait for next millisecond
                sequenceID = 0;
                timestamp = waitForNextMillisecond(lastTimestamp);
            }
        } else {
            sequenceID = 0;
        }
        
        lastTimestamp = timestamp;
        
        long timestampDelta = timestamp - EPOCH;
        if (timestampDelta > MAX_TIMESTAMP_DELTA) {
            throw new RuntimeException("Timestamp exceeds maximum value");
        }

        return (timestampDelta << TIMESTAMP_SHIFT) |
               (machineId << MACHINE_ID_SHIFT) |
                sequenceID;
    }

    public Map<String, Object> getMetadata(long id) {
        long sequenceID = (id >> SEQUENCE_SHIFT) & MAX_SEQUENCE_ID;
        long machineId = (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
        long timestampDelta = (id >> TIMESTAMP_SHIFT) & MAX_TIMESTAMP_DELTA;

        return ImmutableMap.of(
                "timestampDelta", Long.toString(timestampDelta),
                "epoch", Long.toString(EPOCH),
                "machineID", machineId,
                "sequenceID", sequenceID,
                "algorithm", "snowflake"
        );
    }

    private long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }
    
    private long waitForNextMillisecond(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
}
