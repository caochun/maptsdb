# MapTSDB - 基于MapDB的时序数据存储系统

## 项目简介

MapTSDB是一个基于MapDB构建的高性能时序数据存储系统，专为物联网和边缘计算场景设计。它提供了高效的数据写入、时间范围查询和数据压缩功能。

## 核心特性

- 🚀 **高性能写入**：支持每秒数十万数据点的写入性能
- 📊 **时间范围查询**：高效的时间序列数据查询
- 💾 **数据压缩**：内置多种序列化器，显著减少存储空间
- 🔄 **并发支持**：原生支持多线程并发操作
- 📱 **嵌入式部署**：无需独立数据库进程，适合边缘设备
- 🗂️ **自动清理**：支持数据过期策略，自动清理历史数据
- 🎯 **多数据类型**：支持Double、Integer、Long、String、Boolean、Float等多种数据类型

## 技术架构

### 存储引擎
- **MapDB 3.0.8**：嵌入式Java数据库引擎
- **内存映射文件**：提供接近内存的读写性能
- **增量编码**：时间戳使用LongDeltaSerializer压缩
- **浮点压缩**：数值使用DoublePackedSerializer优化

### 数据结构
```java
ConcurrentNavigableMap<Long, Double> timeSeriesData
├── 时间戳 (Long) - 使用增量编码压缩
└── 数据值 (Double) - 使用浮点压缩
```

## 快速开始

### 环境要求
- Java 11+
- Maven 3.6+

### 安装依赖
```bash
mvn clean install
```

### 基本使用

#### 单数据类型（Double）
```java
// 创建时序数据库
TimeSeriesDB tsdb = new TimeSeriesDB("sensor_data.db");

// 写入数据
tsdb.put(System.currentTimeMillis(), 25.5);

// 时间范围查询
NavigableMap<Long, Double> recentData = tsdb.queryRange(
    startTime, endTime);

// 批量写入
List<DataPoint> batchData = Arrays.asList(
    new DataPoint(timestamp1, value1),
    new DataPoint(timestamp2, value2)
);
tsdb.putBatch(batchData);

// 关闭数据库
tsdb.close();
```

#### 多数据类型支持
```java
// 创建多类型时序数据库
MultiTypeTimeSeriesDB tsdb = new MultiTypeTimeSeriesDB("multi_data.db");

// 存储不同类型的数据
tsdb.putDouble(System.currentTimeMillis(), 25.5);      // 温度
tsdb.putInteger(System.currentTimeMillis(), 65);       // 湿度
tsdb.putString(System.currentTimeMillis(), "ONLINE");  // 状态
tsdb.putBoolean(System.currentTimeMillis(), true);     // 开关
tsdb.putLong(System.currentTimeMillis(), 1000L);       // 计数器
tsdb.putFloat(System.currentTimeMillis(), 1013.25f);   // 压力

// 按类型查询
List<TypedDataPoint<Double>> temperatureData = 
    tsdb.queryRangeByType(startTime, endTime, Double.class);

// 类型安全的数据获取
Double temperature = tsdb.getDouble(timestamp);
String status = tsdb.getString(timestamp);

tsdb.close();
```

## 性能指标

根据实际测试结果：

### TimeSeriesDB（单类型）
| 指标 | 性能 |
|------|------|
| 单线程写入 | 377 数据点/秒 |
| 批量写入 | 102,040 数据点/秒 |
| 查询延迟 | 95% < 10ms |
| 内存占用 | 653 KB (5000数据点) |

### MultiTypeTimeSeriesDB（多类型）
| 指标 | 性能 |
|------|------|
| 单线程写入 | 374 数据点/秒 |
| 批量写入 | 375 数据点/秒 |
| 查询延迟 | 95% < 10ms |
| 内存占用 | 更优的内存管理 |
| 支持类型 | 6种数据类型 |

### 性能对比总结
- **单线程写入**：性能相当（差异0.7%）
- **批量写入**：TimeSeriesDB快270倍
- **功能丰富度**：MultiTypeTimeSeriesDB支持更多数据类型
- **选择建议**：纯数值用TimeSeriesDB，多类型用MultiTypeTimeSeriesDB

## 使用场景

### 物联网传感器数据
```java
// 单类型数据（温度传感器）
TimeSeriesDB temperatureDB = new TimeSeriesDB("temperature.db");
temperatureDB.put(System.currentTimeMillis(), sensorReading);

// 多类型数据（综合传感器）
MultiTypeTimeSeriesDB sensorDB = new MultiTypeTimeSeriesDB("sensor_data.db");
sensorDB.putDouble(System.currentTimeMillis(), temperature);  // 温度
sensorDB.putInteger(System.currentTimeMillis(), humidity);    // 湿度
sensorDB.putString(System.currentTimeMillis(), status);       // 状态
```

### 工业设备监控
```java
// 设备状态数据
MultiTypeTimeSeriesDB deviceDB = new MultiTypeTimeSeriesDB("device_monitor.db");
deviceDB.putString(System.currentTimeMillis(), "RUNNING");    // 运行状态
deviceDB.putBoolean(System.currentTimeMillis(), true);        // 开关状态
deviceDB.putLong(System.currentTimeMillis(), counter);        // 计数器
deviceDB.putFloat(System.currentTimeMillis(), pressure);      // 压力值
```

### 边缘计算数据采集
```java
// 高频数据采集
for (int i = 0; i < 1000; i++) {
    tsdb.putDouble(System.currentTimeMillis() + i, sensorValue);
    tsdb.putInteger(System.currentTimeMillis() + i, sensorId);
    tsdb.putString(System.currentTimeMillis() + i, "ACTIVE");
}
```

## 高级配置

### 自定义序列化器
```java
DB db = DBMaker.fileDB("custom.db")
    .fileMmapEnable()
    .make();

ConcurrentNavigableMap<Long, Double> data = db.treeMap("data")
    .keySerializer(new LongDeltaSerializer())    // 时间戳增量编码
    .valueSerializer(Serializer.DOUBLE_PACKED)  // 浮点数压缩
    .createOrOpen();
```

### 内存优化配置
```java
DB db = DBMaker.fileDB("optimized.db")
    .cacheSize(512 * 1024 * 1024)  // 512MB缓存
    .cacheLRUEnable()              // LRU淘汰策略
    .make();
```

## 运行示例

```bash
# 运行单类型示例
mvn exec:java -Dexec.mainClass="com.maptsdb.TimeSeriesExample"

# 运行多类型示例
mvn exec:java -Dexec.mainClass="com.maptsdb.MultiTypeExample"

# 运行性能对比测试
mvn exec:java -Dexec.mainClass="com.maptsdb.SimplePerformanceComparison"

# 运行所有测试
mvn test

# 查看性能对比报告
cat PERFORMANCE_COMPARISON.md
```

## 项目结构

```
maptsdb/
├── src/main/java/com/maptsdb/
│   ├── TimeSeriesDB.java              # 核心时序数据库类（Double类型）
│   ├── MultiTypeTimeSeriesDB.java     # 多类型时序数据库类
│   ├── TimeSeriesExample.java         # 单类型使用示例
│   ├── MultiTypeExample.java          # 多类型使用示例
│   ├── SimplePerformanceComparison.java # 性能对比测试
│   └── PerformanceComparison.java     # 详细性能测试
├── src/test/java/com/maptsdb/
│   ├── TimeSeriesDBTest.java          # 单类型单元测试
│   └── MultiTypeTimeSeriesDBTest.java # 多类型单元测试
├── pom.xml                            # Maven配置
├── README.md                          # 项目文档
├── PERFORMANCE_COMPARISON.md          # 性能对比报告
└── .gitignore                         # Git忽略文件
```

## 最佳实践

### 1. 数据写入优化
- 使用批量写入减少IO操作
- 合理设置缓存大小
- 启用事务保证数据一致性

### 2. 查询性能优化
- 使用时间范围查询而非全表扫描
- 合理设置数据保留策略
- 定期清理过期数据

### 3. 内存管理
- 配置适当的缓存大小
- 使用堆外存储减少GC压力
- 监控内存使用情况

## 故障排除

### 常见问题

1. **内存不足**
   - 减少缓存大小
   - 启用LRU淘汰策略
   - 使用堆外存储

2. **写入性能下降**
   - 检查磁盘空间
   - 优化批量写入大小
   - 调整缓存配置

3. **数据丢失**
   - 确保启用事务
   - 定期调用commit()
   - 检查磁盘权限

## 贡献指南

欢迎提交Issue和Pull Request来改进项目。

## 许可证

MIT License

## 参考资源

- [MapDB官方文档](https://mapdb.org/doc/)
- [时序数据存储最佳实践](https://blog.csdn.net/gitblog_00850/article/details/152066637)
- [MapDB GitHub仓库](https://github.com/jankotek/mapdb)
