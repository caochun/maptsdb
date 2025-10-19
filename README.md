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
- 🛡️ **健壮性**：完善的参数验证和异常处理
- 📝 **专业文档**：完整的JavaDoc注释和代码文档

## 技术架构

### 存储引擎
- **MapDB 3.1.0**：嵌入式Java数据库引擎（已升级）
- **内存映射文件**：提供接近内存的读写性能
- **标准序列化**：使用Serializer.LONG和Serializer.DOUBLE
- **性能优化**：启用内存映射、事务支持和并发优化
- **代码质量**：专业的代码风格和完善的文档

### 数据结构
```java
ConcurrentNavigableMap<Long, Double> timeSeriesData
├── 时间戳 (Long) - 使用标准序列化
└── 数据值 (Double) - 使用标准序列化
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
ObjectTimeSeriesDb tsdb = new ObjectTimeSeriesDb("multi_data.db");

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

根据优化后的实际测试结果（10,000个数据点）：

### TimeSeriesDB（单类型）
| 指标 | 性能 |
|------|------|
| 单线程写入 | 1,661 数据点/秒 |
| 多线程写入 | 515 数据点/秒 |
| 批量写入 | 312,500 数据点/秒 |
| 查询延迟 | < 1ms |
| 内存占用 | 54 KB |

### ObjectTimeSeriesDb（多类型）
| 指标 | 性能 |
|------|------|
| 单线程写入 | 1,205 数据点/秒 |
| 多线程写入 | 389 数据点/秒 |
| 批量写入 | 85,470 数据点/秒 |
| 查询延迟 | < 1ms |
| 内存占用 | 57 KB |
| 支持类型 | 6种数据类型 |

### 性能对比总结
- **单线程写入**：TimeSeriesDB快37.8%
- **多线程写入**：TimeSeriesDB快32.3%
- **批量写入**：TimeSeriesDB快265.6%
- **查询性能**：两者相当
- **内存使用**：两者相当
- **选择建议**：纯数值用TimeSeriesDB，多类型用ObjectTimeSeriesDb

## 使用场景

### 物联网传感器数据
```java
// 单类型数据（温度传感器）
TimeSeriesDB temperatureDB = new TimeSeriesDB("temperature.db");
temperatureDB.put(System.currentTimeMillis(), sensorReading);

// 多类型数据（综合传感器）
ObjectTimeSeriesDb sensorDB = new ObjectTimeSeriesDb("sensor_data.db");
sensorDB.putDouble(System.currentTimeMillis(), temperature);  // 温度
sensorDB.putInteger(System.currentTimeMillis(), humidity);    // 湿度
sensorDB.putString(System.currentTimeMillis(), status);       // 状态
```

### 工业设备监控
```java
// 设备状态数据
ObjectTimeSeriesDb deviceDB = new ObjectTimeSeriesDb("device_monitor.db");
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

### 性能优化配置（推荐）
```java
DB db = DBMaker.fileDB("optimized.db")
    .fileMmapEnable()           // 启用内存映射文件
    .fileMmapPreclearDisable()  // 禁用预清理以提高性能
    .cleanerHackEnable()        // 启用清理器以防止内存泄漏
    .transactionEnable()        // 启用事务支持
    .closeOnJvmShutdown()      // JVM关闭时自动关闭
    .concurrencyScale(16)      // 设置并发级别
    .make();
```

### 自定义序列化器
```java
DB db = DBMaker.fileDB("custom.db")
    .fileMmapEnable()
    .make();

ConcurrentNavigableMap<Long, Double> data = db.treeMap("data")
    .keySerializer(Serializer.LONG)    // 时间戳序列化
    .valueSerializer(Serializer.DOUBLE) // 浮点数序列化
    .createOrOpen();
```

## 运行测试

```bash
# 运行所有测试
mvn test

# 编译项目
mvn compile

# 打包项目
mvn package
```

## 最新更新

### v1.0.0 (2025-10-19)

**🚀 重大更新：**
- ✅ **MapDB升级**：从3.0.8升级到3.1.0，获得更好的性能和稳定性
- ✅ **代码整理**：全面的代码重构和文档完善
- ✅ **健壮性增强**：添加完善的参数验证和异常处理
- ✅ **文档完善**：完整的JavaDoc注释和代码文档

**🔧 技术改进：**
- 📝 **代码质量**：统一的代码风格和命名规范
- 🛡️ **错误处理**：全面的输入验证和异常处理机制
- 📚 **文档系统**：专业的JavaDoc注释和代码文档
- 🎯 **性能优化**：保持原有高性能的同时提升代码质量

**📊 测试覆盖：**
- ✅ 17个单元测试全部通过
- ✅ 功能完整性验证
- ✅ 性能基准测试通过

## 项目结构

```
maptsdb/
├── src/main/java/com/maptsdb/
│   ├── TimeSeriesDB.java              # 核心时序数据库类（Double类型）
│   └── ObjectTimeSeriesDb.java        # 多类型时序数据库类
├── src/test/java/com/maptsdb/
│   ├── TimeSeriesDBTest.java          # 单类型单元测试
│   └── ObjectTimeSeriesDbTest.java    # 多类型单元测试
├── pom.xml                            # Maven配置
├── README.md                          # 项目文档
└── .gitignore                         # Git忽略文件
```

## 最佳实践

### 1. 数据写入优化
- 使用批量写入减少IO操作
- 合理设置缓存大小
- 启用事务保证数据一致性
- 使用类型安全的put方法

### 2. 查询性能优化
- 使用时间范围查询而非全表扫描
- 合理设置数据保留策略
- 定期清理过期数据
- 利用类型过滤查询提高效率

### 3. 内存管理
- 配置适当的缓存大小
- 使用堆外存储减少GC压力
- 监控内存使用情况

### 4. 代码质量
- 使用完整的JavaDoc注释
- 遵循统一的代码风格
- 添加适当的参数验证
- 处理异常情况

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
