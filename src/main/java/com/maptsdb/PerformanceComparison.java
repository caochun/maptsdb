package com.maptsdb;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

/**
 * TimeSeriesDB vs MultiTypeTimeSeriesDB 性能对比测试
 */
public class PerformanceComparison {
    
    private static final int TEST_DATA_POINTS = 10000;
    private static final int CONCURRENT_THREADS = 5;
    private static final int BATCH_SIZE = 100;
    
    public static void main(String[] args) {
        System.out.println("=== MapDB时序数据库性能对比测试 ===\n");
        
        // 测试1：单线程写入性能对比
        compareSingleThreadWrite();
        
        // 测试2：多线程并发写入性能对比
        compareConcurrentWrite();
        
        // 测试3：批量写入性能对比
        compareBatchWrite();
        
        // 测试4：查询性能对比
        compareQueryPerformance();
        
        // 测试5：内存使用对比
        compareMemoryUsage();
    }
    
    /**
     * 单线程写入性能对比
     */
    private static void compareSingleThreadWrite() {
        System.out.println("=== 单线程写入性能对比 ===");
        
        // 测试TimeSeriesDB
        long startTime = System.currentTimeMillis();
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb.db");
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            tsdb.put(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        long tsdbTime = System.currentTimeMillis() - startTime;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB
        startTime = System.currentTimeMillis();
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb.db");
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            mtsdb.putDouble(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        long mtsdbTime = System.currentTimeMillis() - startTime;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d ms (%d 数据点/秒)%n", 
            tsdbTime, TEST_DATA_POINTS * 1000 / tsdbTime);
        System.out.printf("MultiTypeTSDB:   %d ms (%d 数据点/秒)%n", 
            mtsdbTime, TEST_DATA_POINTS * 1000 / mtsdbTime);
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbTime - tsdbTime) / tsdbTime * 100);
        System.out.println();
    }
    
    /**
     * 多线程并发写入性能对比
     */
    private static void compareConcurrentWrite() {
        System.out.println("=== 多线程并发写入性能对比 ===");
        
        // 测试TimeSeriesDB
        long startTime = System.currentTimeMillis();
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_concurrent.db");
        
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        final CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        
        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            final int threadId = t;
            final TimeSeriesDB finalTsdb = tsdb;
            executor.submit(() -> {
                for (int i = 0; i < TEST_DATA_POINTS / CONCURRENT_THREADS; i++) {
                    finalTsdb.put(System.currentTimeMillis() + threadId * 1000 + i, 
                        ThreadLocalRandom.current().nextDouble(0, 100));
                }
                latch.countDown();
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long tsdbTime = System.currentTimeMillis() - startTime;
        tsdb.close();
        executor.shutdown();
        
        // 测试MultiTypeTimeSeriesDB
        startTime = System.currentTimeMillis();
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_concurrent.db");
        
        executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        final CountDownLatch latch2 = new CountDownLatch(CONCURRENT_THREADS);
        
        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            final int threadId = t;
            final MultiTypeTimeSeriesDB finalMtsdb = mtsdb;
            executor.submit(() -> {
                for (int i = 0; i < TEST_DATA_POINTS / CONCURRENT_THREADS; i++) {
                    finalMtsdb.putDouble(System.currentTimeMillis() + threadId * 1000 + i, 
                        ThreadLocalRandom.current().nextDouble(0, 100));
                }
                latch2.countDown();
            });
        }
        
        try {
            latch2.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long mtsdbTime = System.currentTimeMillis() - startTime;
        mtsdb.close();
        executor.shutdown();
        
        System.out.printf("TimeSeriesDB:     %d ms (%d 数据点/秒)%n", 
            tsdbTime, TEST_DATA_POINTS * 1000 / tsdbTime);
        System.out.printf("MultiTypeTSDB:   %d ms (%d 数据点/秒)%n", 
            mtsdbTime, TEST_DATA_POINTS * 1000 / mtsdbTime);
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbTime - tsdbTime) / tsdbTime * 100);
        System.out.println();
    }
    
    /**
     * 批量写入性能对比
     */
    private static void compareBatchWrite() {
        System.out.println("=== 批量写入性能对比 ===");
        
        // 预先创建相同的数据集
        long baseTime = System.currentTimeMillis();
        List<TimeSeriesDB.DataPoint> tsdbBatchData = new ArrayList<>();
        List<MultiTypeTimeSeriesDB.DataPoint> mtsdbBatchData = new ArrayList<>();
        
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            long timestamp = baseTime + i;
            double value = ThreadLocalRandom.current().nextDouble(0, 100);
            
            tsdbBatchData.add(new TimeSeriesDB.DataPoint(timestamp, value));
            mtsdbBatchData.add(new MultiTypeTimeSeriesDB.DataPoint(timestamp, value));
        }
        
        // 测试TimeSeriesDB写入时间
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_batch.db");
        long startTime = System.currentTimeMillis();
        tsdb.putBatch(tsdbBatchData);
        long tsdbWriteTime = System.currentTimeMillis() - startTime;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB写入时间
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_batch.db");
        startTime = System.currentTimeMillis();
        mtsdb.putBatch(mtsdbBatchData);
        long mtsdbWriteTime = System.currentTimeMillis() - startTime;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d ms (%d 数据点/秒)%n", 
            tsdbWriteTime, TEST_DATA_POINTS * 1000 / Math.max(tsdbWriteTime, 1));
        System.out.printf("MultiTypeTSDB:   %d ms (%d 数据点/秒)%n", 
            mtsdbWriteTime, TEST_DATA_POINTS * 1000 / Math.max(mtsdbWriteTime, 1));
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbWriteTime - tsdbWriteTime) / Math.max(tsdbWriteTime, 1) * 100);
        System.out.println();
    }
    
    /**
     * 查询性能对比
     */
    private static void compareQueryPerformance() {
        System.out.println("=== 查询性能对比 ===");
        
        // 准备测试数据
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_query.db");
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_query.db");
        
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            double value = ThreadLocalRandom.current().nextDouble(0, 100);
            tsdb.put(baseTime + i, value);
            mtsdb.putDouble(baseTime + i, value);
        }
        
        // 测试TimeSeriesDB查询
        long startTime = System.currentTimeMillis();
        long queryStart = baseTime + TEST_DATA_POINTS / 4;
        long queryEnd = baseTime + TEST_DATA_POINTS * 3 / 4;
        
        var tsdbResult = tsdb.queryRange(queryStart, queryEnd);
        long tsdbQueryTime = System.currentTimeMillis() - startTime;
        
        // 测试MultiTypeTimeSeriesDB查询
        startTime = System.currentTimeMillis();
        var mtsdbResult = mtsdb.queryRange(queryStart, queryEnd);
        long mtsdbQueryTime = System.currentTimeMillis() - startTime;
        
        // 先输出结果再关闭数据库
        System.out.printf("TimeSeriesDB:     %d ms (查询到 %d 个数据点)%n", 
            tsdbQueryTime, tsdbResult != null ? tsdbResult.size() : 0);
        System.out.printf("MultiTypeTSDB:   %d ms (查询到 %d 个数据点)%n", 
            mtsdbQueryTime, mtsdbResult != null ? mtsdbResult.size() : 0);
        System.out.printf("性能差异: %.1f%%%n", 
            (double)(mtsdbQueryTime - tsdbQueryTime) / Math.max(tsdbQueryTime, 1) * 100);
        System.out.println();
        
        tsdb.close();
        mtsdb.close();
    }
    
    /**
     * 内存使用对比
     */
    private static void compareMemoryUsage() {
        System.out.println("=== 内存使用对比 ===");
        
        // 获取初始内存
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 测试TimeSeriesDB内存使用
        TimeSeriesDB tsdb = new TimeSeriesDB("perf_test_tsdb_memory.db");
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            tsdb.put(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        runtime.gc();
        long tsdbMemory = runtime.totalMemory() - runtime.freeMemory() - initialMemory;
        tsdb.close();
        
        // 测试MultiTypeTimeSeriesDB内存使用
        runtime.gc();
        long initialMemory2 = runtime.totalMemory() - runtime.freeMemory();
        
        MultiTypeTimeSeriesDB mtsdb = new MultiTypeTimeSeriesDB("perf_test_mtsdb_memory.db");
        for (int i = 0; i < TEST_DATA_POINTS; i++) {
            mtsdb.putDouble(System.currentTimeMillis() + i, ThreadLocalRandom.current().nextDouble(0, 100));
        }
        
        runtime.gc();
        long mtsdbMemory = runtime.totalMemory() - runtime.freeMemory() - initialMemory2;
        mtsdb.close();
        
        System.out.printf("TimeSeriesDB:     %d KB%n", tsdbMemory / 1024);
        System.out.printf("MultiTypeTSDB:   %d KB%n", mtsdbMemory / 1024);
        System.out.printf("内存差异: %.1f%%%n", 
            (double)(mtsdbMemory - tsdbMemory) / tsdbMemory * 100);
        System.out.println();
    }
}
