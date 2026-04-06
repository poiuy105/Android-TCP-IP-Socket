# Tasks

- [x] Task 1: MIUI TV设备检测与适配基础
  - [x] SubTask 1.1: 创建MIUI TV设备检测工具类MiuiTvDetector.java
  - [x] SubTask 1.2: 实现MIUI版本检测方法（检测1.3.48版本）
  - [x] SubTask 1.3: 添加MIUI TV特性检测逻辑
  - [x] SubTask 1.4: 在Application类中初始化MIUI TV检测

- [x] Task 2: 增强MIUI TV保活机制
  - [x] SubTask 2.1: 在ServerService中添加MIUI TV保活策略
  - [x] SubTask 2.2: 实现MIUI白名单申请引导功能
  - [x] SubTask 2.3: 添加MIUI后台权限检测和提示
  - [x] SubTask 2.4: 实现MIUI特有的服务重启机制
  - [x] SubTask 2.5: 添加心跳检测和自动恢复功能

- [x] Task 3: 优化MIUI TV TTS功能
  - [x] SubTask 3.1: 创建TTS引擎管理类TtsEngineManager.java
  - [x] SubTask 3.2: 实现MIUI TV TTS引擎检测和选择逻辑
  - [x] SubTask 3.3: 添加TTS初始化失败的重试机制
  - [x] SubTask 3.4: 实现TTS状态监控和错误处理
  - [x] SubTask 3.5: 添加TTS测试功能到设置界面

- [x] Task 4: 修复MIUI TV悬浮窗显示问题
  - [x] SubTask 4.1: 优化悬浮窗权限申请流程（适配MIUI TV）
  - [x] SubTask 4.2: 调整悬浮窗WindowManager.LayoutParams参数
  - [x] SubTask 4.3: 实现悬浮窗层级管理（TYPE_APPLICATION_OVERLAY）
  - [x] SubTask 4.4: 添加悬浮窗位置自适应逻辑（右上角）
  - [x] SubTask 4.5: 实现悬浮窗防遮挡机制

- [x] Task 5: 添加MIUI TV用户引导界面
  - [x] SubTask 5.1: 创建MIUI TV设置引导Activity
  - [x] SubTask 5.2: 添加保活权限设置引导页面
  - [x] SubTask 5.3: 添加悬浮窗权限设置引导页面
  - [x] SubTask 5.4: 添加TTS设置引导页面

- [x] Task 6: 测试与验证
  - [x] SubTask 6.1: 在MIUI TV模拟器上测试保活功能
  - [x] SubTask 6.2: 在MIUI TV模拟器上测试TTS功能
  - [x] SubTask 6.3: 在MIUI TV模拟器上测试悬浮窗显示
  - [x] SubTask 6.4: 在真实MIUI TV设备上进行全面测试
  - [x] SubTask 6.5: 修复测试发现的问题

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 1]
- [Task 5] depends on [Task 2, Task 3, Task 4]
- [Task 6] depends on [Task 5]
