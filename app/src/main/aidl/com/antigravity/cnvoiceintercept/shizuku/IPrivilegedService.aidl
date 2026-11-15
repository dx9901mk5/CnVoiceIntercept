package com.antigravity.cnvoiceintercept.shizuku;

import com.antigravity.cnvoiceintercept.shizuku.ITaskCallback;

interface IPrivilegedService {
    void destroy();
    boolean forceStopPackage(String packageName);
    boolean pressBack();
    boolean startActivityAsShell(String packageName, String className);
    boolean launchPackageAsShell(String packageName);
    boolean grantNotificationListener(String componentName);
    boolean grantAllRequiredPermissions(String appPackage, String notifListenerComponent);
    boolean startTaskWatch(in List<String> targetPackages, ITaskCallback callback);
    void stopTaskWatch();
}
