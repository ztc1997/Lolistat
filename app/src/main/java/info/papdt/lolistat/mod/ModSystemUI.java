package info.papdt.lolistat.mod;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.LinkedHashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import info.papdt.lolistat.support.Settings;

import static info.papdt.lolistat.BuildConfig.DEBUG;

public class ModSystemUI
{
	private static final String TAG = ModSystemUI.class.getSimpleName() + ":";

    private static boolean shouldBlackenIcons, shouldBlackenBtns;
    private static LinkedHashSet<ImageView> notiIconViews = new LinkedHashSet<>();
    private static LinkedHashSet<ImageView> sysIconViews = new LinkedHashSet<>();
    private static LinkedHashSet<ImageView> btnViews = new LinkedHashSet<>();
    private static LinkedHashSet<TextView> textViews = new LinkedHashSet<>();

	public static void hookSystemUI(ClassLoader loader) {
		if (DEBUG) {
			XposedBridge.log(TAG + "Loading SystemUI");
		}
		
		Settings settings = Settings.getInstance(null);
		if (!settings.getBoolean("global", "global", Settings.TINT_ICONS, false))
			return;
		
		// Thanks to MohanmmadAG
		final Class<?> iconView = XposedHelpers.findClass("com.android.systemui.statusbar.StatusBarIconView", loader);

        final XC_MethodHook imageViewHookConstructorHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                addNotiIconView((ImageView) param.thisObject);
            }
        };

        XposedHelpers.findAndHookConstructor(iconView, Context.class, String.class, Notification.class, imageViewHookConstructorHook);
        XposedHelpers.findAndHookConstructor(iconView, Context.class, AttributeSet.class, imageViewHookConstructorHook);

        final Class<?> keyButtonView = XposedHelpers.findClass("com.android.systemui.statusbar.policy.KeyButtonView", loader);

        final XC_MethodHook keyButtonViewConstructorHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                addBtnView((ImageView) param.thisObject);
            }
        };

        XposedHelpers.findAndHookConstructor(keyButtonView, Context.class, AttributeSet.class, int.class, keyButtonViewConstructorHook);
        XposedHelpers.findAndHookConstructor(keyButtonView, Context.class, AttributeSet.class, keyButtonViewConstructorHook);
		
		XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
                Boolean isNotiIcon = (Boolean) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "isNotiIcon");
                if (isNotiIcon != null && isNotiIcon) {

                    if (DEBUG) {
                        XposedBridge.log(TAG + "applying filter to Drawable");
                    }

                    Drawable d = (Drawable) mhparams.args[0];
                    if (d != null)
                        d.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    return;
                }
                Boolean isSysIcon = (Boolean) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "isSysIcon");
                if (isSysIcon != null && isSysIcon) {

                    if (DEBUG) {
                        XposedBridge.log(TAG + "applying filter to Drawable");
                    }

                    Drawable d = (Drawable) mhparams.args[0];
                    if (d != null)
                        d.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.TRANSPARENT, PorterDuff.Mode.SRC_ATOP);
                }
            }
        });

        XposedHelpers.findAndHookMethod(ImageView.class, "setImageResource", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Boolean isNotiIcon = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, "isNotiIcon");
                if (isNotiIcon != null && isNotiIcon) {
                    Drawable d = ((ImageView) param.thisObject).getDrawable();
                    if (d != null)
                        d.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    return;
                }
                Boolean isSysIcon = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, "isSysIcon");
                if (isSysIcon != null && isSysIcon) {
                    Drawable d = ((ImageView) param.thisObject).getDrawable();
                    if (d != null)
                        d.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.TRANSPARENT, PorterDuff.Mode.SRC_ATOP);
                    return;
                }
                Boolean isBtn = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, "isBtn");
                if (isBtn != null && isBtn) {
                    Drawable d = ((ImageView) param.thisObject).getDrawable();
                    d.setColorFilter(shouldBlackenBtns ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                }
            }
        });

        final Class<?> phoneStatusBarView = XposedHelpers.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarView", loader);
        XposedHelpers.findAndHookConstructor(phoneStatusBarView, Context.class, AttributeSet.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                ((Context) param.args[0]).registerReceiver(broadcastReceiver, new IntentFilter(ModLoli.ACTION_SHOULD_BLACKEN_ICONS_CHANGED));
            }
        });

        hookClock(loader);
        ModBattery.doHook(loader);
	}

    private static void hookClock(ClassLoader loader) {
        final Class<?> Clock = XposedHelpers.findClass("com.android.systemui.statusbar.policy.Clock", loader);
        XposedBridge.hookAllConstructors(Clock, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                textViews.add((TextView) param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(Clock, "updateClock", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView textView = (TextView) param.thisObject;
                textView.setTextColor(shouldBlackenIcons ? Color.BLACK : Color.WHITE);
            }
        });
    }

    public static void addNotiIconView(ImageView imageView){
        notiIconViews.add(imageView);
        XposedHelpers.setAdditionalInstanceField(imageView, "isNotiIcon", true);
    }

    public static void addSysIconView(ImageView imageView){
        sysIconViews.add(imageView);
        XposedHelpers.setAdditionalInstanceField(imageView, "isSysIcon", true);
    }

    public static void addBtnView(ImageView imageView){
        btnViews.add(imageView);
        XposedHelpers.setAdditionalInstanceField(imageView, "isBtn", true);
    }

    public static boolean shouldBlackenIcons() {
        return shouldBlackenIcons;
    }

	private static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!intent.getAction().equals(ModLoli.ACTION_SHOULD_BLACKEN_ICONS_CHANGED)) return;
            if (intent.hasExtra(ModLoli.EXTRA_BLACKEN_ICONS)) {
                shouldBlackenIcons = intent.getBooleanExtra(ModLoli.EXTRA_BLACKEN_ICONS, false);
                for (ImageView imageView : notiIconViews) {
                    if (imageView != null) {
                        Drawable drawable = imageView.getDrawable();
                        if (drawable != null)
                            drawable.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    }
                }

                for (ImageView imageView : sysIconViews) {
                    if (imageView != null) {
                        Drawable drawable = imageView.getDrawable();
                        if (drawable != null)
                            drawable.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.TRANSPARENT, PorterDuff.Mode.SRC_ATOP);
                    }
                }

                for (TextView textView : textViews) {
                    textView.setTextColor(shouldBlackenIcons ? Color.BLACK : Color.WHITE);
                }

                ModBattery.updateBattery();
            }
            if (intent.hasExtra(ModLoli.EXTRA_BLACKEN_BUTTONS)) {
                shouldBlackenBtns = intent.getBooleanExtra(ModLoli.EXTRA_BLACKEN_BUTTONS, false);
                for (ImageView imageView : btnViews) {
                    if (imageView != null) {
                        Drawable drawable = imageView.getDrawable();
                        if (drawable != null)
                            drawable.setColorFilter(shouldBlackenBtns ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    }
                }
            }
		}
	};
}
