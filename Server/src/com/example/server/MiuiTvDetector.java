package com.example.server;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiuiTvDetector {
    private static final String TAG = "MiuiTvDetector";
    
    private static final String MIUI_VERSION_FILE = "/proc/version";
    private static final String MIUI_SYSTEM_PROP = "ro.miui.ui.version.name";
    private static final String MIUI_VERSION_CODE_PROP = "ro.miui.ui.version.code";
    private static final String MIUI_TV_VERSION_PROP = "ro.build.version.incremental";
    
    private static final String MIUI_TV_FEATURE = "com.xiaomi.tv";
    private static final String MIUI_TV_PACKAGE = "com.xiaomi.mitv.tvhome";
    
    private static Boolean cachedIsMiuiTv = null;
    private static String cachedMiuiVersion = null;
    private static Boolean cachedIsTargetVersion = null;
    
    private MiuiTvDetector() {
    }
    
    public static boolean isMiuiTv(Context context) {
        if (cachedIsMiuiTv != null) {
            return cachedIsMiuiTv;
        }
        
        cachedIsMiuiTv = detectMiuiTv(context);
        Log.d(TAG, "MIUI TV detection result: " + cachedIsMiuiTv);
        return cachedIsMiuiTv;
    }
    
    private static boolean detectMiuiTv(Context context) {
        if (!isMiuiDevice()) {
            Log.d(TAG, "Not a MIUI device");
            return false;
        }
        
        if (!isTvDevice(context)) {
            Log.d(TAG, "Not a TV device");
            return false;
        }
        
        if (hasMiuiTvFeature(context)) {
            Log.d(TAG, "Has MIUI TV feature");
            return true;
        }
        
        if (hasMiuiTvPackage(context)) {
            Log.d(TAG, "Has MIUI TV package");
            return true;
        }
        
        if (isMiuiTvByVersion()) {
            Log.d(TAG, "Detected as MIUI TV by version");
            return true;
        }
        
        return false;
    }
    
    public static boolean isMiuiDevice() {
        String miuiVersion = getSystemProperty(MIUI_SYSTEM_PROP);
        boolean isMiui = miuiVersion != null && !miuiVersion.isEmpty();
        Log.d(TAG, "MIUI device check: " + isMiui + " (version: " + miuiVersion + ")");
        return isMiui;
    }
    
    public static boolean isTvDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        
        boolean isTv = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
        boolean isLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean isAndroidTv = pm.hasSystemFeature("android.hardware.type.tv");
        
        boolean result = isTv || isLeanback || isAndroidTv;
        Log.d(TAG, "TV device check: " + result + " (television: " + isTv + 
              ", leanback: " + isLeanback + ", androidTv: " + isAndroidTv + ")");
        
        return result;
    }
    
    public static String getMiuiVersion() {
        if (cachedMiuiVersion != null) {
            return cachedMiuiVersion;
        }
        
        cachedMiuiVersion = getSystemProperty(MIUI_SYSTEM_PROP);
        
        if (cachedMiuiVersion == null || cachedMiuiVersion.isEmpty()) {
            cachedMiuiVersion = getMiuiVersionFromFile();
        }
        
        Log.d(TAG, "MIUI version: " + cachedMiuiVersion);
        return cachedMiuiVersion;
    }
    
    public static String getMiuiVersionCode() {
        return getSystemProperty(MIUI_VERSION_CODE_PROP);
    }
    
    public static String getMiuiTvVersion() {
        return getSystemProperty(MIUI_TV_VERSION_PROP);
    }
    
    public static boolean isTargetVersion(String targetVersion) {
        String currentVersion = getMiuiVersion();
        if (currentVersion == null) {
            return false;
        }
        return currentVersion.equalsIgnoreCase(targetVersion);
    }
    
    public static boolean isVersion1_3_48() {
        if (cachedIsTargetVersion != null) {
            return cachedIsTargetVersion;
        }
        
        String version = getMiuiVersion();
        String versionCode = getMiuiVersionCode();
        String tvVersion = getMiuiTvVersion();
        
        Log.d(TAG, "Checking version 1.3.48 - version: " + version + 
              ", versionCode: " + versionCode + ", tvVersion: " + tvVersion);
        
        cachedIsTargetVersion = checkVersion1_3_48(version, versionCode, tvVersion);
        return cachedIsTargetVersion;
    }
    
    private static boolean checkVersion1_3_48(String version, String versionCode, String tvVersion) {
        if (version != null && version.contains("1.3.48")) {
            return true;
        }
        
        if (versionCode != null && versionCode.contains("1.3.48")) {
            return true;
        }
        
        if (tvVersion != null && tvVersion.contains("1.3.48")) {
            return true;
        }
        
        if (tvVersion != null) {
            Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
            Matcher matcher = pattern.matcher(tvVersion);
            if (matcher.find()) {
                try {
                    int major = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    int patch = Integer.parseInt(matcher.group(3));
                    
                    return major == 1 && minor == 3 && patch == 48;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse version numbers", e);
                }
            }
        }
        
        return false;
    }
    
    public static boolean hasMiuiTvFeature(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean hasFeature = pm.hasSystemFeature(MIUI_TV_FEATURE);
        Log.d(TAG, "MIUI TV feature check: " + hasFeature);
        return hasFeature;
    }
    
    public static boolean hasMiuiTvPackage(Context context) {
        try {
            context.getPackageManager().getPackageInfo(MIUI_TV_PACKAGE, 0);
            Log.d(TAG, "MIUI TV package found: " + MIUI_TV_PACKAGE);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "MIUI TV package not found");
            return false;
        }
    }
    
    private static boolean isMiuiTvByVersion() {
        String manufacturer = Build.MANUFACTURER;
        String brand = Build.BRAND;
        String model = Build.MODEL;
        String device = Build.DEVICE;
        
        Log.d(TAG, "Device info - manufacturer: " + manufacturer + 
              ", brand: " + brand + ", model: " + model + ", device: " + device);
        
        boolean isXiaomi = manufacturer != null && 
                          (manufacturer.toLowerCase().contains("xiaomi") ||
                           manufacturer.toLowerCase().contains("mi"));
        
        boolean isTvModel = model != null && 
                           (model.toLowerCase().contains("tv") ||
                            model.toLowerCase().contains("mitv") ||
                            model.toLowerCase().contains("xiaomi tv"));
        
        boolean isTvDevice = device != null &&
                            (device.toLowerCase().contains("tv") ||
                             device.toLowerCase().contains("mitv"));
        
        return isXiaomi && (isTvModel || isTvDevice);
    }
    
    private static String getSystemProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = systemProperties.getMethod("get", String.class);
            String value = (String) getMethod.invoke(null, key);
            return value;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get system property: " + key, e);
            return null;
        }
    }
    
    private static String getMiuiVersionFromFile() {
        File versionFile = new File("/data/system/miui_version");
        if (versionFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(versionFile));
                String version = reader.readLine();
                reader.close();
                return version != null ? version.trim() : null;
            } catch (IOException e) {
                Log.w(TAG, "Failed to read MIUI version file", e);
            }
        }
        return null;
    }
    
    public static void clearCache() {
        cachedIsMiuiTv = null;
        cachedMiuiVersion = null;
        cachedIsTargetVersion = null;
        Log.d(TAG, "Cache cleared");
    }
    
    public static MiuiTvInfo getMiuiTvInfo(Context context) {
        MiuiTvInfo info = new MiuiTvInfo();
        info.isMiuiDevice = isMiuiDevice();
        info.isTvDevice = isTvDevice(context);
        info.isMiuiTv = isMiuiTv(context);
        info.miuiVersion = getMiuiVersion();
        info.miuiVersionCode = getMiuiVersionCode();
        info.miuiTvVersion = getMiuiTvVersion();
        info.isVersion1_3_48 = isVersion1_3_48();
        info.hasMiuiTvFeature = hasMiuiTvFeature(context);
        info.hasMiuiTvPackage = hasMiuiTvPackage(context);
        info.manufacturer = Build.MANUFACTURER;
        info.brand = Build.BRAND;
        info.model = Build.MODEL;
        info.device = Build.DEVICE;
        info.sdkVersion = Build.VERSION.SDK_INT;
        return info;
    }
    
    public static class MiuiTvInfo {
        public boolean isMiuiDevice;
        public boolean isTvDevice;
        public boolean isMiuiTv;
        public String miuiVersion;
        public String miuiVersionCode;
        public String miuiTvVersion;
        public boolean isVersion1_3_48;
        public boolean hasMiuiTvFeature;
        public boolean hasMiuiTvPackage;
        public String manufacturer;
        public String brand;
        public String model;
        public String device;
        public int sdkVersion;
        
        @Override
        public String toString() {
            return "MiuiTvInfo{" +
                   "isMiuiDevice=" + isMiuiDevice +
                   ", isTvDevice=" + isTvDevice +
                   ", isMiuiTv=" + isMiuiTv +
                   ", miuiVersion='" + miuiVersion + '\'' +
                   ", miuiVersionCode='" + miuiVersionCode + '\'' +
                   ", miuiTvVersion='" + miuiTvVersion + '\'' +
                   ", isVersion1_3_48=" + isVersion1_3_48 +
                   ", hasMiuiTvFeature=" + hasMiuiTvFeature +
                   ", hasMiuiTvPackage=" + hasMiuiTvPackage +
                   ", manufacturer='" + manufacturer + '\'' +
                   ", brand='" + brand + '\'' +
                   ", model='" + model + '\'' +
                   ", device='" + device + '\'' +
                   ", sdkVersion=" + sdkVersion +
                   '}';
        }
    }
}
