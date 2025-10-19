package com.maptsdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import java.util.ArrayList;
import java.util.NavigableMap;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeSeriesDB单元测试
 */
public class TimeSeriesDBTest {
    
    private TimeSeriesDB tsdb;
    private String testDbPath = "test_timeseries.db";
    
    @BeforeEach
    void setUp() {
        // 清理可能存在的测试数据库文件
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        
        tsdb = new TimeSeriesDB(testDbPath);
    }
    
    @AfterEach
    void tearDown() {
        if (tsdb != null) {
            tsdb.close();
        }
        
        // 清理测试数据库文件
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    @DisplayName("测试基本数据写入和读取")
    void testBasicWriteAndRead() {
        long timestamp = System.currentTimeMillis();
        double value = 25.5;
        
        // 写入数据
        tsdb.put(timestamp, value);
        
        // 读取数据
        Double retrievedValue = tsdb.get(timestamp);
        
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue, 0.001);
    }
    
    @Test
    @DisplayName("测试时间范围查询")
    void testRangeQuery() {
        long baseTime = System.currentTimeMillis();
        
        // 写入测试数据
        for (int i = 0; i < 10; i++) {
            tsdb.put(baseTime + i * 1000, 20.0 + i);
        }
        
        // 查询时间范围
        long startTime = baseTime + 2000;
        long endTime = baseTime + 8000;
        NavigableMap<Long, Double> result = tsdb.queryRange(startTime, endTime);
        
        assertEquals(7, result.size()); // 应该包含7个数据点
        assertTrue(result.containsKey(baseTime + 2000));
        assertTrue(result.containsKey(baseTime + 8000));
        assertFalse(result.containsKey(baseTime + 1000)); // 不在范围内
    }
    
    @Test
    @DisplayName("测试批量数据写入")
    void testBatchWrite() {
        List<TimeSeriesDB.DataPoint> batchData = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        // 准备批量数据
        for (int i = 0; i < 100; i++) {
            batchData.add(new TimeSeriesDB.DataPoint(baseTime + i, 10.0 + i));
        }
        
        // 批量写入
        tsdb.putBatch(batchData);
        
        // 验证数据
        for (int i = 0; i < 100; i++) {
            Double value = tsdb.get(baseTime + i);
            assertNotNull(value);
            assertEquals(10.0 + i, value, 0.001);
        }
    }
    
    @Test
    @DisplayName("测试获取最新数据点")
    void testGetLatest() {
        long baseTime = System.currentTimeMillis();
        
        // 写入10个数据点
        for (int i = 0; i < 10; i++) {
            tsdb.put(baseTime + i, 100.0 + i);
        }
        
        // 获取最新5个数据点
        List<TimeSeriesDB.DataPoint> latest = tsdb.getLatest(5);
        
        assertEquals(5, latest.size());
        assertEquals(baseTime + 9, latest.get(0).getTimestamp()); // 最新的时间戳
        assertEquals(109.0, latest.get(0).getValue(), 0.001);
    }
    
    @Test
    @DisplayName("测试数据库统计信息")
    void testDatabaseStats() {
        // 写入一些测试数据
        for (int i = 0; i < 50; i++) {
            tsdb.put(System.currentTimeMillis() + i, Math.random() * 100);
        }
        
        TimeSeriesDB.DBStats stats = tsdb.getStats();
        
        assertNotNull(stats);
        assertTrue(stats.getDataPointCount() >= 50);
        assertTrue(stats.getLastUpdateTime() > 0);
    }
    
    @Test
    @DisplayName("测试数据持久化")
    void testDataPersistence() {
        long timestamp = System.currentTimeMillis();
        double value = 42.0;
        
        // 写入数据
        tsdb.put(timestamp, value);
        tsdb.close();
        
        // 重新打开数据库
        TimeSeriesDB newTsdb = new TimeSeriesDB(testDbPath);
        
        // 验证数据仍然存在
        Double retrievedValue = newTsdb.get(timestamp);
        assertNotNull(retrievedValue);
        assertEquals(value, retrievedValue, 0.001);
        
        newTsdb.close();
    }
    
    @Test
    @DisplayName("测试并发写入")
    void testConcurrentWrite() throws InterruptedException {
        int threadCount = 5;
        int dataPointsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        // 创建多个线程并发写入
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < dataPointsPerThread; i++) {
                    long timestamp = System.currentTimeMillis() + threadId * 1000 + i;
                    double value = threadId * 100.0 + i;
                    tsdb.put(timestamp, value);
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证数据总数（允许少量数据丢失，因为时间戳可能冲突）
        TimeSeriesDB.DBStats stats = tsdb.getStats();
        assertTrue(stats.getDataPointCount() >= threadCount * dataPointsPerThread * 0.9);
    }
}
