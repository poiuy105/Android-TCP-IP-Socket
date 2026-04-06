# MIUI TV 系统兼容性优化规格

## Why
当前版本1.3.48的应用在MIUI TV系统环境下存在三个关键功能问题：应用保活机制失效、TTS功能无法正常工作、界面无法在右上角以最上层模式显示。这些问题严重影响应用在MIUI TV设备上的可用性和用户体验。

## What Changes
- 增强MIUI TV特有的保活机制，适配MIUI的后台进程管理策略
- 优化TTS引擎初始化流程，添加MIUI TV兼容性检测和错误处理
- 调整悬浮窗权限申请和显示逻辑，确保在MIUI TV上正确显示在右上角最上层
- 添加MIUI TV设备检测和适配逻辑

## Impact
- Affected specs: 后台服务保活、TTS语音输出、悬浮窗显示
- Affected code: ServerService.java, FloatingWindowService.java, BootReceiver.java, MainActivity.java

## ADDED Requirements

### Requirement: MIUI TV保活机制
系统应提供针对MIUI TV的后台保活机制，确保应用在MIUI TV 1.3.48版本环境中能够持续运行。

#### Scenario: MIUI TV后台保活
- **WHEN** 应用在MIUI TV设备上运行并进入后台
- **THEN** 应用应通过MIUI特有的保活策略保持活跃状态
- **AND** 应用应能够响应MIUI的后台进程清理机制
- **AND** 应用被意外终止后应能够自动重启

### Requirement: MIUI TV TTS兼容性
系统应提供针对MIUI TV的TTS引擎兼容性支持，确保语音播报功能正常工作。

#### Scenario: TTS初始化成功
- **WHEN** 应用在MIUI TV设备上启动
- **THEN** 系统应检测并初始化MIUI TV可用的TTS引擎
- **AND** 如果默认TTS引擎不可用，应尝试备用引擎
- **AND** 应提供详细的错误日志便于问题定位

#### Scenario: TTS语音播报
- **WHEN** 应用接收到需要播报的文本
- **THEN** 系统应成功调用TTS引擎进行语音播报
- **AND** 应处理播报失败的情况并提供重试机制

### Requirement: MIUI TV悬浮窗显示
系统应提供针对MIUI TV的悬浮窗显示支持，确保界面能够在右上角区域以最上层模式呈现。

#### Scenario: 悬浮窗权限获取
- **WHEN** 应用首次在MIUI TV设备上启动
- **THEN** 系统应检测悬浮窗权限状态
- **AND** 如果权限未授予，应引导用户到MIUI特有的权限设置页面
- **AND** 应提供清晰的权限申请说明

#### Scenario: 悬浮窗正确显示
- **WHEN** 应用需要显示悬浮窗
- **THEN** 悬浮窗应在屏幕右上角正确显示
- **AND** 悬浮窗应始终保持在最上层
- **AND** 悬浮窗不应被其他应用遮挡

## MODIFIED Requirements

### Requirement: 后台服务保活（已修改）
系统应提供多重保活机制，包括：
- 标准Android保活策略（START_STICKY、前台服务）
- MIUI TV特有保活策略（白名单申请、后台权限提示）
- 服务重启机制（onTaskRemoved、定时重启）
- 守护进程机制（可选）

### Requirement: TTS功能（已修改）
系统应提供完整的TTS功能支持，包括：
- 标准TTS引擎初始化
- MIUI TV TTS引擎兼容性检测
- 多语言支持（中文、英文）
- 错误处理和重试机制
- TTS状态监控

### Requirement: 悬浮窗显示（已修改）
系统应提供完善的悬浮窗显示功能，包括：
- 标准悬浮窗权限申请
- MIUI TV悬浮窗权限适配
- 悬浮窗位置和大小自适应
- 悬浮窗层级管理（最上层显示）
- 悬浮窗生命周期管理

## REMOVED Requirements
无移除的需求。
