package io.gendora.services;

import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SlotAllocatorService {
    
    private static final Logger logger = LoggerFactory.getLogger(SlotAllocatorService.class);
    private static final int MAX_SLOTS = 1024; // 0-1023
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration SLOT_TTL = Duration.ofMinutes(5);
    
    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired
    private RedisCommands<String, String> redis;

    private ScheduledExecutorService heartbeatExecutor;

    private String instanceID;
    private volatile Integer allocatedSlot;

    public int getAllocatedSlot() {
        if (allocatedSlot == null) {
            throw new IllegalStateException("No slot allocated");
        }
        return allocatedSlot;
    }

    @PostConstruct
    public void init() {
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        
        try {
            this.instanceID =  InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();

            allocateSlot();
            startHeartbeat();
            logger.info("Successfully allocated slot {} for instance {}", allocatedSlot, instanceID);
        } catch (Exception e) {
            logger.error("Failed to allocate slot", e);
            throw new RuntimeException("Slot allocation failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (allocatedSlot != null) {
            releaseSlot();
        }

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Heartbeat executor did not terminate within 5 seconds, forcing shutdown");
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for heartbeat executor to shutdown", e);
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void allocateSlot() {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            String slotKey = getSlotKey(slot);
            
            Boolean claimed = redis.setnx(slotKey, instanceID);
            if (Boolean.TRUE.equals(claimed)) {
                redis.expire(slotKey, SLOT_TTL.toSeconds());
                this.allocatedSlot = slot;
                logger.info("Successfully claimed slot {}", slot);
                return;
            }
        }
        
        throw new RuntimeException("No available slots found (all 1024 slots are occupied)");
    }
    
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::renewSlot,
                0,
                HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
        logger.info("Started heartbeat for slot {}", allocatedSlot);
    }
    
    private void renewSlot() {
        if (allocatedSlot != null) {
            String slotKey = getSlotKey(allocatedSlot);
            try {
                redis.expire(slotKey, SLOT_TTL.toSeconds());
            } catch (Exception e) {
                logger.error("Failed to renew heartbeat for slot {}", allocatedSlot, e);
                allocatedSlot = null;
            }
        }
    }
    
    private void releaseSlot() {
        String slotKey = getSlotKey(allocatedSlot);
        try {
            String currentOwner = redis.get(slotKey);
            if (instanceID.equals(currentOwner)) {
                redis.del(slotKey);
                logger.info("Released slot {}", allocatedSlot);
            } else {
                logger.warn("Slot {} is no longer owned by this container", allocatedSlot);
            }
        } catch (Exception e) {
            logger.error("Failed to release slot {}", allocatedSlot, e);
        }
    }

    private String getSlotKey(int slotIndex) {
        return  applicationName + ":slot:" + slotIndex;
    }
}
