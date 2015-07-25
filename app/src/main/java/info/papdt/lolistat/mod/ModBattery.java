package info.papdt.lolistat.mod;

import static info.papdt.lolistat.BuildConfig.DEBUG;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModBattery {
    private static final String TAG = ModBattery.class.getSimpleName() + ":";

    private static final String[] SONY_BATTERY_ID_NAMES = {"status", "battery_icon", "stamina_icon", "battery_percent_bg"};

    private static View batteryView;
    private static LinkedHashSet<Object> batteryImages = new LinkedHashSet<>();

    public static void doHook(ClassLoader loader) {
        try {
            Class<?> BatteryView = XposedHelpers.findClass("com.android.systemui.BatteryMeterView", loader);
            XposedHelpers.findAndHookMethod(BatteryView, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    View batteryView = (View) param.thisObject;
                    int parentId = ((View) batteryView.getParent()).getId();
                    Resources res = batteryView.getResources();
                    if (parentId != res.getIdentifier("signal_battery_cluster", "id", "com.android.systemui"))
                        return;

                    int visibility = (Integer) XposedHelpers.callMethod(param.thisObject, "getVisibility");
                    if (visibility == View.VISIBLE)
                        ModBattery.batteryView = (View) param.thisObject;
                }
            });
        } catch (XposedHelpers.ClassNotFoundError ignored) {
        }

        hookSonyBattery(loader);
    }

    public static void updateBattery() {
        if (ModSystemUI.shouldBlackenIcons() && batteryView != null) {

            int iconColor = Color.BLACK;

            try {
                final int[] colors = (int[]) XposedHelpers.getObjectField(batteryView, "mColors");
                colors[colors.length - 1] = iconColor;
                XposedHelpers.setObjectField(batteryView, "mColors", colors);
            } catch (NoSuchFieldError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            try {
                final Paint framePaint = (Paint) XposedHelpers.getObjectField(batteryView, "mFramePaint");
                framePaint.setColor(iconColor);
                framePaint.setAlpha(100);
            } catch (NoSuchFieldError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            try {
                final Paint boltPaint = (Paint) XposedHelpers.getObjectField(batteryView, "mBoltPaint");
                boltPaint.setColor(iconColor);
                boltPaint.setAlpha(100);
            } catch (NoSuchFieldError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            try {
                final Paint textPaint = (Paint) XposedHelpers.getObjectField(batteryView, "mTextPaint");
                textPaint.setColor(iconColor);
                textPaint.setAlpha(100);
            } catch (NoSuchFieldError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            try {
                XposedHelpers.setIntField(batteryView, "mChargeColor", iconColor);
            } catch (NoSuchFieldError e) {
			/* Beanstalk, not sure why the ROM changed this */
                try {
                    XposedHelpers.setIntField(batteryView, "mBatteryColor", iconColor);
                } catch (NoSuchFieldError ignored) {
                }
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            batteryView.invalidate();
        }
        updateSonyBattery();
    }

    private static void hookSonyBattery(ClassLoader loader) {
        try {
            final Class<?> batteryController = XposedHelpers.findClass("com.android.systemui.statusbar.policy.BatteryController", loader);
            XposedHelpers.findAndHookMethod(batteryController, "addView", View.class, View.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    View battery = (View) param.args[1];
                    Resources resources = battery.getContext().getResources();
                    for (String name : SONY_BATTERY_ID_NAMES) {
                        try {
                            ImageView icon = (ImageView) battery.findViewById(resources.getIdentifier(name, "id", "com.android.systemui"));
                            ModSystemUI.addSysIconView(icon);
                        } catch (Exception e) {
                            if (DEBUG)
                                XposedBridge.log(TAG + e);
                        }
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError e) {
            if (DEBUG)
                XposedBridge.log(TAG + e);
        }

        try {
            final Class<?> batteryImage = XposedHelpers.findClass("com.sonymobile.systemui.statusbar.BatteryImage", loader);
            try {
                XposedHelpers.findAndHookMethod(batteryImage, "setTextColor", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        if (ModSystemUI.shouldBlackenIcons())
                            param.args[0] = Color.BLACK;
/*                        if (DEBUG)
                            XposedBridge.log("method called: com.sonymobile.systemui.statusbar.BatteryImage.setTextColor");*/
                    }
                });
            } catch (NoSuchMethodError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }

            try {
                XposedHelpers.findAndHookConstructor(batteryImage, Context.class, AttributeSet.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        batteryImages.add(param.thisObject);
                    }
                });
            } catch (NoSuchMethodError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }
        } catch (XposedHelpers.ClassNotFoundError e) {
            if (DEBUG)
                XposedBridge.log(TAG + e);
        }
    }

    private static void updateSonyBattery() {
        if (DEBUG) XposedBridge.log(TAG + "batteryImages = " + batteryImages);
        for (Object batteryImage : batteryImages) {
            if (batteryImage == null) continue;
            try {
                XposedHelpers.callMethod(batteryImage, "setTextColor", Color.WHITE);
            } catch (IllegalArgumentException | NoSuchMethodError e) {
                if (DEBUG)
                    XposedBridge.log(TAG + e);
            }
        }
    }
}
