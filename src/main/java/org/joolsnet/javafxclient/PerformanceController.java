package org.joolsnet.javafxclient;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * Controller for the Performance Metrics window.
 * Displays memory usage, GC statistics, and system metrics.
 */
public class PerformanceController {
    
    @FXML
    private Label heapUsedLabel;
    
    @FXML
    private Label heapCommittedLabel;
    
    @FXML
    private Label heapMaxLabel;
    
    @FXML
    private Label heapUsageLabel;
    
    @FXML
    private Label nonHeapUsedLabel;
    
    @FXML
    private Label edenSpaceLabel;
    
    @FXML
    private Label survivorSpaceLabel;
    
    @FXML
    private Label oldGenLabel;
    
    @FXML
    private Label youngGcCountLabel;
    
    @FXML
    private Label oldGcCountLabel;
    
    @FXML
    private Label allocationRateLabel;
    
    @FXML
    private Label promotionRateLabel;
    
    @FXML
    private Label allocStatsLabel;
    
    @FXML
    private Label gcCountLabel;
    
    @FXML
    private Label gcTimeLabel;
    
    @FXML
    private Label gcRateLabel;
    
    @FXML
    private Label avgGcPauseLabel;
    
    @FXML
    private Label cpuCoresLabel;
    
    @FXML
    private Label threadCountLabel;
    
    @FXML
    private Label uptimeLabel;
    
    @FXML
    private Label lastUpdateLabel;
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<MemoryPoolMXBean> memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
    
    private ScheduledExecutorService scheduler;
    private RecordingStream recordingStream;
    private long lastGcCount = 0;
    private long lastGcTime = 0;
    private long lastUpdateTime = System.currentTimeMillis();
    
    // For rate calculations
    private long lastEdenUsed = 0;
    private long lastOldGenUsed = 0;
    private long lastRateUpdateTime = System.currentTimeMillis();
    
    // JFR allocation tracking
    private final Map<String, AtomicLong> allocationsByType = new ConcurrentHashMap<>();
    private volatile boolean jfrAvailable = false;
    
    @FXML
    public void initialize() {
        // Start JFR streaming
        startJFRRecording();
        
        // Update immediately
        updateMetrics();
        
        // Schedule updates every second
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceMetrics");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::updateMetrics);
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void startJFRRecording() {
        try {
            recordingStream = new RecordingStream();
            
            // Enable allocation events
            recordingStream.enable("jdk.ObjectAllocationInNewTLAB")
                .withStackTrace();
            recordingStream.enable("jdk.ObjectAllocationOutsideTLAB")
                .withStackTrace();
            
            // Process allocation events
            recordingStream.onEvent("jdk.ObjectAllocationInNewTLAB", this::handleAllocationEvent);
            recordingStream.onEvent("jdk.ObjectAllocationOutsideTLAB", this::handleAllocationEvent);
            
            // Start in background
            recordingStream.startAsync();
            
            jfrAvailable = true;
            System.out.println("JFR allocation tracking started");
        } catch (Exception e) {
            System.err.println("JFR not available: " + e.getMessage());
            jfrAvailable = false;
            Platform.runLater(() -> {
                if (allocStatsLabel != null) {
                    allocStatsLabel.setText("JFR not available (requires Java 14+)");
                }
            });
        }
    }
    
    private void handleAllocationEvent(RecordedEvent event) {
        try {
            // Get the allocated class name
            String className = event.getClass("objectClass").getName();
            long allocationSize = event.getLong("allocationSize");
            
            // Aggregate by type
            allocationsByType.computeIfAbsent(className, k -> new AtomicLong())
                .addAndGet(allocationSize);
        } catch (Exception e) {
            // Ignore malformed events
        }
    }
    
    private void updateMetrics() {
        updateMemoryMetrics();
        updateMemoryGenerationMetrics();
        updateGCMetrics();
        updateSystemMetrics();
        updateAllocationStats();
        
        // Update timestamp
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastUpdateLabel.setText("Last update: " + time);
    }
    
    private void updateMemoryMetrics() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        long heapUsed = heapUsage.getUsed();
        long heapCommitted = heapUsage.getCommitted();
        long heapMax = heapUsage.getMax();
        long nonHeapUsed = nonHeapUsage.getUsed();
        
        heapUsedLabel.setText(formatBytes(heapUsed));
        heapCommittedLabel.setText(formatBytes(heapCommitted));
        heapMaxLabel.setText(heapMax > 0 ? formatBytes(heapMax) : "unlimited");
        
        double usagePercent = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;
        heapUsageLabel.setText(String.format("%.1f%%", usagePercent));
        
        nonHeapUsedLabel.setText(formatBytes(nonHeapUsed));
    }
    
    private void updateMemoryGenerationMetrics() {
        long edenUsed = 0;
        long edenMax = 0;
        long survivorUsed = 0;
        long survivorMax = 0;
        long oldGenUsed = 0;
        long oldGenMax = 0;
        long youngGcCount = 0;
        long oldGcCount = 0;
        
        // Collect memory pool statistics
        for (MemoryPoolMXBean pool : memoryPoolBeans) {
            String name = pool.getName();
            MemoryUsage usage = pool.getUsage();
            
            if (usage != null) {
                // Different GC implementations use different pool names
                if (name.contains("Eden")) {
                    edenUsed = usage.getUsed();
                    edenMax = usage.getMax();
                } else if (name.contains("Survivor")) {
                    survivorUsed += usage.getUsed(); // May have multiple survivor spaces
                    if (usage.getMax() > 0) {
                        survivorMax += usage.getMax();
                    }
                } else if (name.contains("Old") || name.contains("Tenured")) {
                    oldGenUsed = usage.getUsed();
                    oldGenMax = usage.getMax();
                }
            }
        }
        
        // Collect GC counts by type
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long count = gcBean.getCollectionCount();
            
            if (count > 0) {
                // Young generation collectors
                if (gcName.contains("Copy") || gcName.contains("ParNew") || 
                    gcName.contains("PS Scavenge") || gcName.contains("G1 Young") ||
                    gcName.contains("Shenandoah") || gcName.contains("ZGC")) {
                    youngGcCount += count;
                }
                // Old generation collectors
                else if (gcName.contains("MarkSweep") || gcName.contains("PS MarkSweep") ||
                         gcName.contains("ConcurrentMarkSweep") || gcName.contains("G1 Old") ||
                         gcName.contains("G1 Mixed")) {
                    oldGcCount += count;
                }
            }
        }
        
        // Update labels
        if (edenMax > 0) {
            double edenPct = (edenUsed * 100.0) / edenMax;
            edenSpaceLabel.setText(String.format("%s / %s (%.1f%%)", 
                formatBytes(edenUsed), formatBytes(edenMax), edenPct));
        } else {
            edenSpaceLabel.setText(formatBytes(edenUsed));
        }
        
        if (survivorMax > 0) {
            double survivorPct = (survivorUsed * 100.0) / survivorMax;
            survivorSpaceLabel.setText(String.format("%s / %s (%.1f%%)", 
                formatBytes(survivorUsed), formatBytes(survivorMax), survivorPct));
        } else {
            survivorSpaceLabel.setText(formatBytes(survivorUsed));
        }
        
        if (oldGenMax > 0) {
            double oldGenPct = (oldGenUsed * 100.0) / oldGenMax;
            oldGenLabel.setText(String.format("%s / %s (%.1f%%)", 
                formatBytes(oldGenUsed), formatBytes(oldGenMax), oldGenPct));
        } else {
            oldGenLabel.setText(formatBytes(oldGenUsed));
        }
        
        youngGcCountLabel.setText(String.valueOf(youngGcCount));
        oldGcCountLabel.setText(String.valueOf(oldGcCount));
        
        // Calculate allocation and promotion rates
        long now = System.currentTimeMillis();
        long timeDelta = now - lastRateUpdateTime;
        
        if (timeDelta > 0 && lastRateUpdateTime > 0) {
            // Allocation rate: how fast Eden is filling (MB/sec)
            if (edenUsed >= lastEdenUsed) {
                long edenDelta = edenUsed - lastEdenUsed;
                double allocationRate = (edenDelta * 1000.0) / (timeDelta * 1024.0 * 1024.0);
                allocationRateLabel.setText(String.format("%.2f MB/sec", allocationRate));
            }
            
            // Promotion rate: how fast Old Gen is growing (MB/sec)
            if (oldGenUsed >= lastOldGenUsed) {
                long oldGenDelta = oldGenUsed - lastOldGenUsed;
                double promotionRate = (oldGenDelta * 1000.0) / (timeDelta * 1024.0 * 1024.0);
                promotionRateLabel.setText(String.format("%.2f MB/sec", promotionRate));
            }
        }
        
        // Store for next iteration
        lastEdenUsed = edenUsed;
        lastOldGenUsed = oldGenUsed;
        lastRateUpdateTime = now;
    }
    
    private void updateAllocationStats() {
        if (!jfrAvailable) {
            return;
        }
        
        // Get top 10 allocated types by total bytes
        Map<String, Long> snapshot = new HashMap<>();
        allocationsByType.forEach((k, v) -> snapshot.put(k, v.get()));
        
        if (snapshot.isEmpty()) {
            allocStatsLabel.setText("No allocations recorded yet...");
            return;
        }
        
        String stats = snapshot.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(e -> String.format("%-40s %s", 
                simplifyClassName(e.getKey()), 
                formatBytes(e.getValue())))
            .collect(Collectors.joining("\n"));
        
        allocStatsLabel.setText(stats);
    }
    
    private String simplifyClassName(String fullClassName) {
        // Remove package prefix for readability
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fullClassName.length() - 1) {
            return fullClassName.substring(lastDot + 1);
        }
        return fullClassName;
    }
    
    private void updateGCMetrics() {
        long totalGcCount = 0;
        long totalGcTime = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            
            if (count > 0) {
                totalGcCount += count;
            }
            if (time > 0) {
                totalGcTime += time;
            }
        }
        
        gcCountLabel.setText(String.valueOf(totalGcCount));
        gcTimeLabel.setText(formatDuration(totalGcTime));
        
        // Calculate GC rate (collections per second)
        long now = System.currentTimeMillis();
        long timeDelta = now - lastUpdateTime;
        long countDelta = totalGcCount - lastGcCount;
        
        if (timeDelta > 0 && lastUpdateTime > 0) {
            double gcPerSecond = (countDelta * 1000.0) / timeDelta;
            gcRateLabel.setText(String.format("%.2f /sec", gcPerSecond));
        } else {
            gcRateLabel.setText("0.00 /sec");
        }
        
        // Calculate average GC pause
        if (totalGcCount > 0) {
            double avgPause = totalGcTime / (double) totalGcCount;
            avgGcPauseLabel.setText(String.format("%.2f ms", avgPause));
        } else {
            avgGcPauseLabel.setText("0.00 ms");
        }
        
        lastGcCount = totalGcCount;
        lastGcTime = totalGcTime;
        lastUpdateTime = now;
    }
    
    private void updateSystemMetrics() {
        int cores = Runtime.getRuntime().availableProcessors();
        int threadCount = threadBean.getThreadCount();
        long uptime = System.currentTimeMillis() - startTime;
        
        cpuCoresLabel.setText(String.valueOf(cores));
        threadCountLabel.setText(String.valueOf(threadCount));
        uptimeLabel.setText(formatDuration(uptime));
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        } else if (millis < 60000) {
            return String.format("%.2f sec", millis / 1000.0);
        } else if (millis < 3600000) {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            long hours = millis / 3600000;
            long minutes = (millis % 3600000) / 60000;
            return String.format("%d hr %d min", hours, minutes);
        }
    }
    
    /**
     * Cleanup when the window is closed
     */
    public void cleanup() {
        // Stop JFR recording
        if (recordingStream != null) {
            try {
                recordingStream.close();
                System.out.println("JFR recording stopped");
            } catch (Exception e) {
                System.err.println("Error stopping JFR: " + e.getMessage());
            }
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
