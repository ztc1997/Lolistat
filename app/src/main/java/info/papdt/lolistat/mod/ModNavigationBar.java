package info.papdt.lolistat.mod;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import info.papdt.lolistat.support.Settings;

public class ModNavigationBar
{
	public static void hookNavigationBar(ClassLoader loader) throws Throwable {
		final Settings settings = Settings.getInstance(null);
		
		if (!settings.getBoolean("global", "global", Settings.TINT_NAVIGATION, true)) return;

		final Class<?> internalStyleable = XposedHelpers.findClass("com.android.internal.R.styleable", loader);
		final Field internalThemeField = XposedHelpers.findField(internalStyleable, "Theme");
		final Field internalColorPrimaryDarkField = XposedHelpers.findField(internalStyleable, "Theme_colorPrimaryDark");
		final int[] theme = (int[]) internalThemeField.get(null);
		final int theme_colorPrimaryDark = internalColorPrimaryDarkField.getInt(null);

		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", loader, "setStatusBarColor", int.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
                Window window = (Window) mhparams.thisObject;
                Context context = window.getContext();

                String packageName = context.getApplicationInfo().packageName;
                String className = context.getClass().getName();
                int tintMode = settings.getInt(packageName, className, Settings.TINT_MODE, Settings.TINT_MODE_CLASSIC);

                if (tintMode == Settings.TINT_MODE_FULL_TINTED)
                    return;

				int color = Integer.valueOf(mhparams.args[0].toString());

				if (color != 0)
					window.setNavigationBarColor(color);
			}
		});

		XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
				Activity activity = (Activity) mhparams.thisObject;

                String packageName = activity.getApplicationInfo().packageName;
                String className = activity.getClass().getName();
				int tintMode = settings.getInt(packageName, className, Settings.TINT_MODE, Settings.TINT_MODE_CLASSIC);

				if (tintMode == Settings.TINT_MODE_FULL_TINTED)
                    return;

				TypedArray a = activity.getTheme().obtainStyledAttributes(theme);
				int colorPrimaryDark = a.getColor(theme_colorPrimaryDark, Color.TRANSPARENT);
				a.recycle();

				if (colorPrimaryDark != Color.TRANSPARENT && colorPrimaryDark != Color.BLACK) {
					activity.getWindow().setNavigationBarColor(colorPrimaryDark);
				}
			}
		});
	}
}
