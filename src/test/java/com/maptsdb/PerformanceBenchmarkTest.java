package com.maptsdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 性能基准测试
 * 详细测试各种操作的性能表现
 */
public class PerformanceBenchmarkTest {
    
    @TempDir
    Path tempDir;
    
    private TimeSeriesDatabaseBuilder.TimeSeriesDatabase db;
    private String dbPath;
    
    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("performance_test.db").toString();
    }
    
    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }
    
    @Test
    void testSingleWritePerformance() {
        System.out.println("\n=== 单条写入性能测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("numeric_data")
                .addObjectSource("object_data")
                .build();
        
        int testCount = 10000;
        long baseTime = System.currentTimeMillis();
        
        // 预热JVM
        System.out.println("预热JVM...");
        for (int i = 0; i < 100; i++) {
            db.putDouble("numeric_data", baseTime + i, Math.random() * 100);
            db.putDoubleToObject("object_data", baseTime + i, Math.random() * 100);
        }
        
        // 测试数值类型单条写入性能
        System.out.println("测试数值类型单条写入...");
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            db.putDouble("numeric_data", baseTime + i, Math.random() * 100);
        }
        long numericTime = System.currentTimeMillis() - startTime;
        
        // 测试对象类型单条写入性能
        System.out.println("测试对象类型单条写入...");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            db.putDoubleToObject("object_data", baseTime + i, Math.random() * 100);
        }
        long objectTime = System.currentTimeMillis() - startTime;
        
        // 计算性能指标
        double numericRate = (double) testCount / numericTime * 1000;
        double objectRate = (double) testCount / objectTime * 1000;
        double performanceRatio = (double) objectTime / numericTime;
        
        System.out.println("\n=== 单条写入性能结果 ===");
        System.out.println("测试数据量: " + testCount + " 条");
        System.out.println("数值类型写入时间: " + numericTime + " ms");
        System.out.println("对象类型写入时间: " + objectTime + " ms");
        System.out.println();
        System.out.println("数值类型写入速率: " + String.format("%.0f", numericRate) + " 条/秒");
        System.out.println("对象类型写入速率: " + String.format("%.0f", objectRate) + " 条/秒");
        System.out.println("性能差异: " + String.format("%.2f", performanceRatio) + " 倍 (对象/数值)");
        
        // 性能分析
        System.out.println("\n=== 性能分析 ===");
        if (numericRate > 10000) {
            System.out.println("✅ 数值类型性能优秀 (>10,000 条/秒)");
        } else if (numericRate > 1000) {
            System.out.println("⚠️  数值类型性能一般 (1,000-10,000 条/秒)");
        } else {
            System.out.println("❌ 数值类型性能较差 (<1,000 条/秒)");
        }
        
        if (objectRate > 5000) {
            System.out.println("✅ 对象类型性能优秀 (>5,000 条/秒)");
        } else if (objectRate > 500) {
            System.out.println("⚠️  对象类型性能一般 (500-5,000 条/秒)");
        } else {
            System.out.println("❌ 对象类型性能较差 (<500 条/秒)");
        }
        
        if (performanceRatio < 2.0) {
            System.out.println("✅ 性能差异合理 (<2倍)");
        } else if (performanceRatio < 5.0) {
            System.out.println("⚠️  性能差异较大 (2-5倍)");
        } else {
            System.out.println("❌ 性能差异过大 (>5倍)");
        }
    }
    
    @Test
    void testBatchWritePerformance() {
        System.out.println("\n=== 批量写入性能测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("numeric_data")
                .addObjectSource("object_data")
                .build();
        
        int[] batchSizes = {100, 500, 1000, 5000, 10000};
        
        for (int batchSize : batchSizes) {
            System.out.println("\n--- 批量大小: " + batchSize + " 条 ---");
            
            long baseTime = System.currentTimeMillis();
            
            // 准备数据
            List<DataPoint<Double>> numericData = new ArrayList<>();
            List<DataPoint<Object>> objectData = new ArrayList<>();
            
            for (int i = 0; i < batchSize; i++) {
                double value = Math.random() * 100;
                numericData.add(new DataPoint<>(baseTime + i, value));
                objectData.add(new DataPoint<>(baseTime + i, value));
            }
            
            // 测试数值类型批量写入
            long startTime = System.currentTimeMillis();
            db.putDoubleBatch("numeric_data", numericData);
            long numericTime = System.currentTimeMillis() - startTime;
            
            // 测试对象类型批量写入
            startTime = System.currentTimeMillis();
            db.putObjectBatch("object_data", objectData);
            long objectTime = System.currentTimeMillis() - startTime;
            
            // 计算性能指标
            double numericRate = (double) batchSize / numericTime * 1000;
            double objectRate = (double) batchSize / objectTime * 1000;
            double performanceRatio = (double) objectTime / numericTime;
            
            System.out.println("数值类型批量写入: " + numericTime + " ms (" + 
                String.format("%.0f", numericRate) + " 条/秒)");
            System.out.println("对象类型批量写入: " + objectTime + " ms (" + 
                String.format("%.0f", objectRate) + " 条/秒)");
            System.out.println("性能差异: " + String.format("%.2f", performanceRatio) + " 倍");
        }
    }
    
    @Test
    void testConcurrentWritePerformance() {
        System.out.println("\n=== 并发写入性能测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("concurrent_data")
                .withConcurrencyScale(16)
                .build();
        
        int threadCount = 8;
        int operationsPerThread = 1000;
        int totalOperations = threadCount * operationsPerThread;
        
        System.out.println("并发线程数: " + threadCount);
        System.out.println("每线程操作数: " + operationsPerThread);
        System.out.println("总操作数: " + totalOperations);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        // 启动并发写入任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 使用系统时间 + 线程ID + 操作序号确保唯一性
                        long timestamp = System.currentTimeMillis() + threadId * 1000000L + j;
                        db.putDouble("concurrent_data", timestamp, Math.random() * 100);
                    }
                    // 每个线程完成后提交事务
                    db.commit();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            // 等待所有任务完成
            latch.await(60, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;
            
            // 计算性能指标
            double totalRate = (double) totalOperations / totalTime * 1000;
            double avgRatePerThread = totalRate / threadCount;
            
            System.out.println("\n=== 并发写入性能结果 ===");
            System.out.println("总执行时间: " + totalTime + " ms");
            System.out.println("总写入速率: " + String.format("%.0f", totalRate) + " 条/秒");
            System.out.println("平均每线程速率: " + String.format("%.0f", avgRatePerThread) + " 条/秒");
            
            // 验证数据完整性
            var stats = db.getStatistics();
            long actualDataPoints = stats.get("concurrent_data (DOUBLE)");
            System.out.println("实际数据点数: " + actualDataPoints);
            
            if (actualDataPoints == totalOperations) {
                System.out.println("✅ 数据完整性验证通过");
            } else {
                System.out.println("❌ 数据完整性验证失败");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("❌ 并发测试被中断");
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    void testReadPerformance() {
        System.out.println("\n=== 读取性能测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("numeric_data")
                .addObjectSource("object_data")
                .build();
        
        int dataCount = 10000;
        long baseTime = System.currentTimeMillis();
        
        // 准备测试数据
        System.out.println("准备测试数据...");
        for (int i = 0; i < dataCount; i++) {
            double value = Math.random() * 100;
            db.putDouble("numeric_data", baseTime + i, value);
            db.putDoubleToObject("object_data", baseTime + i, value);
        }
        
        // 测试数值类型读取性能
        System.out.println("测试数值类型读取...");
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < dataCount; i++) {
            db.getDouble("numeric_data", baseTime + i);
        }
        long numericReadTime = System.currentTimeMillis() - startTime;
        
        // 测试对象类型读取性能
        System.out.println("测试对象类型读取...");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < dataCount; i++) {
            db.getDoubleFromObject("object_data", baseTime + i);
        }
        long objectReadTime = System.currentTimeMillis() - startTime;
        
        // 计算性能指标
        double numericReadRate = (double) dataCount / numericReadTime * 1000;
        double objectReadRate = (double) dataCount / objectReadTime * 1000;
        double readPerformanceRatio = (double) objectReadTime / numericReadTime;
        
        System.out.println("\n=== 读取性能结果 ===");
        System.out.println("测试数据量: " + dataCount + " 条");
        System.out.println("数值类型读取时间: " + numericReadTime + " ms");
        System.out.println("对象类型读取时间: " + objectReadTime + " ms");
        System.out.println();
        System.out.println("数值类型读取速率: " + String.format("%.0f", numericReadRate) + " 条/秒");
        System.out.println("对象类型读取速率: " + String.format("%.0f", objectReadRate) + " 条/秒");
        System.out.println("读取性能差异: " + String.format("%.2f", readPerformanceRatio) + " 倍");
    }
    
    @Test
    void testMemoryUsage() {
        System.out.println("\n=== 内存使用测试 ===");
        
        String numericDbPath = tempDir.resolve("numeric_memory.db").toString();
        String objectDbPath = tempDir.resolve("object_memory.db").toString();
        
        // 创建数值类型数据库
        TimeSeriesDatabaseBuilder.TimeSeriesDatabase numericDb = TimeSeriesDatabaseBuilder.builder()
                .path(numericDbPath)
                .addDoubleSource("data")
                .build();
        
        // 创建对象类型数据库
        TimeSeriesDatabaseBuilder.TimeSeriesDatabase objectDb = TimeSeriesDatabaseBuilder.builder()
                .path(objectDbPath)
                .addObjectSource("data")
                .build();
        
        int dataCount = 10000;
        long baseTime = System.currentTimeMillis();
        
        // 写入相同数据
        for (int i = 0; i < dataCount; i++) {
            double value = Math.random() * 100;
            numericDb.putDouble("data", baseTime + i, value);
            objectDb.putDoubleToObject("data", baseTime + i, value);
        }
        
        // 获取文件大小
        java.io.File numericFile = new java.io.File(numericDbPath);
        java.io.File objectFile = new java.io.File(objectDbPath);
        
        long numericSize = numericFile.length();
        long objectSize = objectFile.length();
        double sizeRatio = (double) objectSize / numericSize;
        
        System.out.println("数据量: " + dataCount + " 条");
        System.out.println("数值类型文件大小: " + formatBytes(numericSize));
        System.out.println("对象类型文件大小: " + formatBytes(objectSize));
        System.out.println("大小差异: " + String.format("%.2f", sizeRatio) + " 倍");
        System.out.println("存储效率: 数值类型比对象类型节省 " + 
            String.format("%.1f", (1 - 1.0/sizeRatio) * 100) + "% 空间");
        
        numericDb.close();
        objectDb.close();
    }
    
    @Test
    void testPerformanceComparison() {
        System.out.println("\n=== 综合性能对比测试 ===");
        
        db = TimeSeriesDatabaseBuilder.builder()
                .path(dbPath)
                .addDoubleSource("numeric_data")
                .addObjectSource("object_data")
                .build();
        
        int testCount = 5000;
        long baseTime = System.currentTimeMillis();
        
        // 准备数据
        List<DataPoint<Double>> numericData = new ArrayList<>();
        List<DataPoint<Object>> objectData = new ArrayList<>();
        
        for (int i = 0; i < testCount; i++) {
            double value = Math.random() * 100;
            numericData.add(new DataPoint<>(baseTime + i, value));
            objectData.add(new DataPoint<>(baseTime + i, value));
        }
        
        System.out.println("测试数据量: " + testCount + " 条");
        
        // 1. 单条写入性能
        long singleNumericStart = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            db.putDouble("numeric_data", baseTime + i, Math.random() * 100);
        }
        long singleNumericTime = System.currentTimeMillis() - singleNumericStart;
        
        long singleObjectStart = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            db.putDoubleToObject("object_data", baseTime + i, Math.random() * 100);
        }
        long singleObjectTime = System.currentTimeMillis() - singleObjectStart;
        
        // 2. 批量写入性能
        long batchNumericStart = System.currentTimeMillis();
        db.putDoubleBatch("numeric_data", numericData);
        long batchNumericTime = System.currentTimeMillis() - batchNumericStart;
        
        long batchObjectStart = System.currentTimeMillis();
        db.putObjectBatch("object_data", objectData);
        long batchObjectTime = System.currentTimeMillis() - batchObjectStart;
        
        // 计算性能指标
        double singleNumericRate = 1000.0 / singleNumericTime * 1000;
        double singleObjectRate = 1000.0 / singleObjectTime * 1000;
        double batchNumericRate = (double) testCount / batchNumericTime * 1000;
        double batchObjectRate = (double) testCount / batchObjectTime * 1000;
        
        double singleImprovement = singleObjectRate / singleNumericRate;
        double batchImprovement = batchObjectRate / batchNumericRate;
        double singleVsBatchNumeric = batchNumericRate / singleNumericRate;
        double singleVsBatchObject = batchObjectRate / singleObjectRate;
        
        System.out.println("\n=== 性能对比结果 ===");
        System.out.println("单条写入:");
        System.out.println("  数值类型: " + String.format("%.0f", singleNumericRate) + " 条/秒");
        System.out.println("  对象类型: " + String.format("%.0f", singleObjectRate) + " 条/秒");
        System.out.println("  性能差异: " + String.format("%.2f", singleImprovement) + " 倍");
        
        System.out.println("\n批量写入:");
        System.out.println("  数值类型: " + String.format("%.0f", batchNumericRate) + " 条/秒");
        System.out.println("  对象类型: " + String.format("%.0f", batchObjectRate) + " 条/秒");
        System.out.println("  性能差异: " + String.format("%.2f", batchImprovement) + " 倍");
        
        System.out.println("\n批量 vs 单条提升:");
        System.out.println("  数值类型: " + String.format("%.2f", singleVsBatchNumeric) + " 倍");
        System.out.println("  对象类型: " + String.format("%.2f", singleVsBatchObject) + " 倍");
        
        // 性能建议
        System.out.println("\n=== 性能建议 ===");
        if (singleNumericRate > 10000) {
            System.out.println("✅ 数值类型单条写入性能优秀");
        } else {
            System.out.println("⚠️  数值类型单条写入性能需要优化");
        }
        
        if (batchNumericRate > 100000) {
            System.out.println("✅ 数值类型批量写入性能优秀");
        } else {
            System.out.println("⚠️  数值类型批量写入性能需要优化");
        }
        
        if (singleVsBatchNumeric > 10) {
            System.out.println("✅ 批量写入显著提升性能，推荐使用");
        } else {
            System.out.println("⚠️  批量写入提升有限，需要进一步优化");
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
