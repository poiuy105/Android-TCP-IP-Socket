# Android TV 兼容性优化计划

## 项目概述

针对当前Android TCP/IP Socket服务器应用在电视设备上的兼容性问题，进行全面优化，确保应用能在Android TV设备上稳定运行，实现自启动、后台常驻、TTS语音输出和悬浮窗显示等完整功能。

## 当前项目状态分析

### 已有功能
- TCP服务器监听端口1234
- TTS文本转语音功能
- 悬浮窗显示功能
- 开机自启动功能（基础实现）

### 已有组件
- MainActivity：主界面
- ServerService：后台服务
- FloatingWindowService：悬浮窗服务
- BootReceiver：开机广播接收器
- TTSState：TTS状态枚举

### 当前配置
- minSdkVersion: 8
- targetSdkVersion: 22 (需要更新到34)
- versionCode: 1
- versionName: "1.0"

## 优化计划详细步骤

### 1. 电视自启动功能优化

#### 1.1 AndroidManifest.xml配置优化
- 添加电视设备特性声明
- 优化启动广播接收器配置
- 添加必要的权限声明

**具体操作：**
```xml
<!-- 添加电视特性声明 -->
<uses-feature android:name="android.hardware.type.tv" android:required="false" />
<uses-feature android:name="android.software.leanback" android:required="false" />

<!-- 添加电视启动相关权限 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.QUICKBOOT_POWERON" />

<!-- 优化BootReceiver配置 -->
<receiver
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true"
    android:directBootAware="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        <action android:name="android.intent.action.REBOOT" />
        <action android:name="android.intent.action.USER_PRESENT" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.ACTION_SHUTDOWN" />
    </intent-filter>
</receiver>
```

#### 1.2 BootReceiver.java优化
- 添加电视设备检测逻辑
- 优化启动延迟处理（电视设备启动较慢）
- 添加启动失败重试机制

**具体操作：**
- 检测设备是否为电视设备
- 添加启动延迟（电视设备启动后需要更多时间初始化）
- 实现启动失败后的重试逻辑
- 添加日志记录便于调试

#### 1.3 MainActivity添加电视启动Activity
- 添加LEANBACK_LAUNCHER intent-filter
- 确保应用在电视桌面显示

**具体操作：**
```xml
<activity android:name=".MainActivity"
    android:label="@string/app_name"
    android:banner="@drawable/ic_launcher_hdpi">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

### 2. 后台进程保活与通知常驻优化

#### 2.1 ServerService保活优化
- 提升服务优先级
- 实现双进程守护机制
- 添加服务重启机制

**具体操作：**
- 在onStartCommand中返回START_STICKY
- 实现onTaskRemoved处理服务被杀死的情况
- 添加守护服务（GuardService）
- 实现服务重启广播接收器

#### 2.2 通知常驻优化
- 优化通知渠道配置
- 确保通知不可清除
- 添加电视设备特有的通知样式

**具体操作：**
```java
// 创建高优先级通知渠道
NotificationChannel channel = new NotificationChannel(
    NOTIFICATION_CHANNEL_ID,
    "Post Tell Me Service",
    NotificationManager.IMPORTANCE_HIGH  // 提升优先级
);
channel.setDescription("Background service for Post Tell Me");
channel.setShowBadge(true);
channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
```

#### 2.3 内存管理优化
- 处理电视系统特有的内存压力
- 实现低内存时的优雅降级
- 添加内存监控机制

**具体操作：**
- 实现onTrimMemory回调
- 在低内存时释放非必要资源
- 添加内存压力监控日志

### 3. TTS功能适配

#### 3.1 TTS引擎检测与初始化
- 检测电视设备可用的TTS引擎
- 提供TTS引擎选择功能
- 处理TTS引擎不可用的情况

**具体操作：**
- 检查系统是否安装TTS引擎
- 列出可用的TTS引擎供用户选择
- 提供TTS引擎下载引导

#### 3.2 TTS权限处理
- 确保TTS权限正确声明
- 处理权限被拒绝的情况

**具体操作：**
- 添加必要的TTS权限声明
- 实现权限检查和请求逻辑

#### 3.3 TTS兼容性优化
- 适配不同品牌电视的TTS实现
- 添加TTS错误处理和重试机制
- 提供TTS测试功能

**具体操作：**
- 测试主流电视品牌的TTS兼容性
- 添加TTS初始化失败的处理逻辑
- 在设置中添加TTS测试按钮

### 4. 悬浮窗权限与显示优化

#### 4.1 悬浮窗权限申请优化
- 检测电视设备的悬浮窗权限机制
- 提供权限申请引导
- 处理权限被拒绝的情况

**具体操作：**
- 检查Settings.canDrawOverlays()
- 提供电视设备特有的权限申请流程
- 添加权限被拒绝时的提示

#### 4.2 悬浮窗UI适配
- 调整悬浮窗大小和位置
- 适配电视遥控器操作
- 优化电视屏幕显示效果

**具体操作：**
```java
// 电视设备悬浮窗参数调整
WindowManager.LayoutParams params = new WindowManager.LayoutParams(
    getWindowWidth() * 2 / 3,  // 电视设备使用更大宽度
    WindowManager.LayoutParams.WRAP_CONTENT,
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
        : WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
);

// 调整位置到右上角
params.gravity = Gravity.TOP | Gravity.END;
params.x = 32;  // 电视设备增加边距
params.y = 32;
```

#### 4.3 分辨率适配
- 支持不同电视分辨率
- 处理超宽屏和4K显示
- 提供UI缩放选项

**具体操作：**
- 检测电视分辨率和屏幕密度
- 根据分辨率动态调整悬浮窗大小
- 提供4K分辨率优化

### 5. 代码优化与兼容性测试

#### 5.1 AndroidManifest.xml全面优化
- 更新targetSdkVersion到34
- 添加电视设备特性声明
- 优化权限配置

**具体操作：**
```xml
<uses-sdk
    android:minSdkVersion="21"
    android:targetSdkVersion="34" />

<!-- 电视设备特性 -->
<uses-feature android:name="android.hardware.type.tv" android:required="false" />
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<!-- 权限优化 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

#### 5.2 build.gradle配置优化
- 更新编译SDK版本
- 更新依赖库版本
- 添加电视设备构建配置

**具体操作：**
```gradle
android {
    compileSdkVersion 34
    buildToolsVersion "34.0.0"

    defaultConfig {
        applicationId "com.example.server"
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 2
        versionName "1.1"
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.leanback:leanback:1.0.0'
    implementation 'androidx.preference:preference:1.2.1'
}
```

#### 5.3 兼容性测试计划
- 测试主流电视品牌设备
- 测试Android TV模拟器
- 测试不同Android版本

**测试设备列表：**
- Sony Android TV
- Samsung Smart TV (Tizen/Android)
- LG WebOS TV (如支持Android应用)
- 小米电视
- 海信电视
- TCL电视
- Android TV模拟器 (API 21-34)

### 6. 版本控制与构建部署

#### 6.1 版本号更新
- 更新versionCode为2
- 更新versionName为"1.1"

**具体操作：**
- 修改Server/build.gradle中的版本信息
- 修改AndroidManifest.xml中的版本信息

#### 6.2 GitHub提交
- 创建功能分支
- 提交所有优化代码
- 创建Pull Request

**具体操作：**
```bash
git checkout -b feature/android-tv-optimization
git add .
git commit -m "feat: Android TV compatibility optimization

- Add Android TV boot auto-start feature
- Optimize background service keep-alive mechanism
- Enhance TTS compatibility for TV devices
- Improve floating window display on TV screens
- Update targetSdkVersion to 34
- Bump version to 1.1

Closes #issue_number"
git push origin feature/android-tv-optimization
```

#### 6.3 GitHub Actions CI/CD优化
- 更新构建脚本支持电视设备
- 优化APK构建流程
- 添加电视设备APK签名配置

**具体操作：**
- 更新.github/workflows/build.yml
- 添加电视设备特性构建配置
- 优化APK输出路径

#### 6.4 APK构建与发布
- 构建电视设备专用APK
- 生成签名APK
- 创建GitHub Release

**具体操作：**
- 使用Gradle构建APK
- 配置签名密钥
- 上传APK到GitHub Release

## 实施顺序

### 阶段一：基础配置优化（优先级：高）
1. 更新build.gradle配置
2. 更新AndroidManifest.xml配置
3. 添加电视设备特性声明

### 阶段二：核心功能优化（优先级：高）
1. 优化BootReceiver自启动功能
2. 优化ServerService保活机制
3. 优化TTS功能适配

### 阶段三：UI与显示优化（优先级：中）
1. 优化悬浮窗显示
2. 适配电视分辨率
3. 优化通知显示

### 阶段四：测试与部署（优先级：高）
1. 进行兼容性测试
2. 修复测试发现的问题
3. 提交代码到GitHub
4. 触发CI/CD构建
5. 发布APK

## 预期成果

完成所有优化后，应用将具备以下能力：

1. **电视自启动**：应用能在电视设备重启后自动启动并恢复运行状态
2. **后台常驻**：应用能在电视系统后台稳定运行，不被系统自动终止
3. **TTS语音输出**：应用能在电视设备上正常使用TTS功能进行语音播报
4. **悬浮窗显示**：应用能在电视屏幕上正确显示悬浮内容
5. **兼容性**：应用能在主流品牌电视设备上稳定运行

## 风险与注意事项

### 风险点
1. 不同品牌电视的Android实现差异较大，可能需要针对性适配
2. 电视设备的内存管理策略可能比手机更严格
3. 部分电视品牌可能限制后台应用运行
4. TTS引擎在不同电视品牌上的可用性和性能差异

### 缓解措施
1. 提供详细的日志记录，便于问题定位
2. 实现优雅降级机制，确保核心功能可用
3. 提供用户配置选项，允许调整保活策略
4. 在文档中说明不同电视品牌的兼容性情况

## 后续维护计划

1. 收集用户反馈，持续优化兼容性
2. 定期测试新发布的电视设备
3. 跟进Android TV系统更新，及时适配新特性
4. 维护兼容性文档，记录各品牌电视的适配情况
