package info.papdt.lolistat.mod;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import info.papdt.lolistat.support.Settings;
import info.papdt.lolistat.support.Utility;

public class ModLoli implements IXposedHookLoadPackage, IXposedHookZygoteInit
{
	private static final String TAG = ModLoli.class.getSimpleName() + ":";
    public static final String ACTION_SHOULD_BLACKEN_ICONS_CHANGED = "info.papdt.lolistat.ModLoli.action.SHOULD_BLACKEN_ICONS_CHANGED";
    public static final String EXTRA_BLACKEN_ICONS = "extra_blacken_icons";
    public static final String EXTRA_BLACKEN_BUTTONS = "extra_blacken_buttons";
    private static final int BLACKEN_THRESHOLD = 0xff * 2;

	private static final long MIN_BREAK = 500;
	private static int STATUS_HEIGHT = 0;
    private static int navigationHeight = 0;
	private Settings mSettings;

	@Override
	public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
		if (mSettings == null)
			mSettings = Settings.getInstance(null);
		
		if (lpparam.packageName.equals("com.android.systemui")) {
			ModSystemUI.hookSystemUI(lpparam.classLoader);
			ModSignalCluster.doHooks(lpparam.classLoader);
        }
	}
	
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam param) throws Throwable {
		
		if (mSettings == null) {
			mSettings = Settings.getInstance(null);
		}
		
		ModNavigationBar.hookNavigationBar(null);
		
		final Class<?> internalStyleable = XposedHelpers.findClass("com.android.internal.R.styleable", null);
		final Field internalThemeField = XposedHelpers.findField(internalStyleable, "Theme");
		final Field internalColorPrimaryDarkField = XposedHelpers.findField(internalStyleable, "Theme_colorPrimaryDark");
		final Field internalTranslucentStatusField = XposedHelpers.findField(internalStyleable, "Theme_windowTranslucentStatus");
		final int[] theme = (int[]) internalThemeField.get(null);
		final int theme_colorPrimaryDark = internalColorPrimaryDarkField.getInt(null);
		final int theme_translucentStatus = internalTranslucentStatusField.getInt(null);

		XposedHelpers.findAndHookMethod(Activity.class, "onPostCreate", Bundle.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
				Activity activity = (Activity) mhparams.thisObject;

				// Ignore floating activities
				int isFloating = XposedHelpers.getStaticIntField(XposedHelpers.findClass("com.android.internal.R.styleable", null), "Window_windowIsFloating");
				if (activity.getWindow().getWindowStyle().getBoolean(isFloating, false))
					return;

				// Ignore translucent activities
				int isTranslucent = XposedHelpers.getStaticIntField(XposedHelpers.findClass("com.android.internal.R.styleable", null), "Window_windowTranslucentStatus");
				if (activity.getWindow().getWindowStyle().getBoolean(isTranslucent, false))
					return;

				String packageName = activity.getApplicationInfo().packageName;
				String className = activity.getClass().getName();

				if (packageName.equals("com.android.systemui"))
					return;

				mSettings.reload();

				int tintMode = mSettings.getInt(packageName, className, Settings.TINT_MODE, Settings.TINT_MODE_CLASSIC);

				// Ignore if disabled mode
				if (tintMode == Settings.TINT_MODE_DISABLED)
					return;

				// custom color
				int custom = mSettings.getInt(packageName, className, Settings.CUSTOM_COLOR, 0);

				if (custom != 0) {
                    activity.getWindow().setStatusBarColor(custom);
                    // Ignore if custom color defined
                    return;
                }

				// Ignore if launcher
				if (Utility.isLauncher(activity, packageName)) return;

				// Ignore if have defined colorPrimaryDark already
				TypedArray a = activity.getTheme().obtainStyledAttributes(theme);
				int colorPrimaryDark = a.getColor(theme_colorPrimaryDark, Color.TRANSPARENT);
				boolean translucentStatus = a.getBoolean(theme_translucentStatus, false);
				a.recycle();

				if (tintMode == Settings.TINT_MODE_CLASSIC && (colorPrimaryDark != Color.TRANSPARENT
                        && colorPrimaryDark != Color.BLACK || translucentStatus)) return;

				final Window window = activity.getWindow();
				int flags = window.getAttributes().flags;

				if (tintMode == Settings.TINT_MODE_CLASSIC && ((flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0 ||
					((flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0 &&
						activity.getWindow().getStatusBarColor() != Color.TRANSPARENT))) {

					return;
				}

				ViewGroup decor = (ViewGroup) window.getDecorView();
				int sysui = decor.findViewById(android.R.id.content).getSystemUiVisibility();

				if ((sysui & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0 ||
					(sysui & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0 ||
					(sysui & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0) {
					return;
				}

				window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

/*				// HACK: Steal root layout to fit system windows
				// But we do not need to worry about fullscreen windows
				if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0) {
					View child = decor.getChildAt(0);
					FrameLayout layout = new FrameLayout(decor.getContext());
					decor.removeView(child);
					layout.addView(child);
					decor.addView(layout);
					layout.setFitsSystemWindows(true);
				}*/

				XposedHelpers.setAdditionalInstanceField(activity, "shouldTint", true);
				XposedHelpers.setAdditionalInstanceField(decor, "activity", activity);
                XposedHelpers.setAdditionalInstanceField(window, "activity", activity);
			}
		});

		XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
				Activity activity = (Activity) mhparams.thisObject;
				Boolean shouldTint = (Boolean) XposedHelpers.getAdditionalInstanceField(activity, "shouldTint");
				if (shouldTint != null && shouldTint) {
					final Window window = activity.getWindow();
					final View decor = window.getDecorView();
					decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        long last = 0;

                        @Override
                        public void onGlobalLayout() {

                            int flags = window.getAttributes().flags;

                            // We do the check here, because activities may change their layout params
                            if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
                                return;

                            long now = System.currentTimeMillis();

                            if (now - last >= MIN_BREAK) {
                                XposedHelpers.setAdditionalInstanceField(decor, "isDecor", true);
                                XposedHelpers.setAdditionalInstanceField(decor, "window", window);
                                //decor.postInvalidate();
                                last = now;
                            }
                            //decor.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                    });
                    Boolean shouldBlackenIcon = (Boolean) XposedHelpers.getAdditionalInstanceField(activity, "shouldBlackenIcon");
                    Boolean shouldBlackenBtn = (Boolean) XposedHelpers.getAdditionalInstanceField(activity, "shouldBlackenBtn");

                    sendShouldBlackenIntent(activity, shouldBlackenIcon != null && shouldBlackenIcon, shouldBlackenBtn != null && shouldBlackenBtn);
				} else {
                    sendShouldBlackenIntent(activity, false, false);
                }
			}
		});
		
		XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
				Activity activity = (Activity) mhparams.thisObject;
                Boolean shouldTint = (Boolean) XposedHelpers.getAdditionalInstanceField(activity, "shouldTint");
				if (shouldTint != null && shouldTint) {
					final View decor = activity.getWindow().getDecorView();
					Bitmap newBitmap = (Bitmap) XposedHelpers.getAdditionalInstanceField(decor, "newBitmap");
					Canvas newCanvas = (Canvas) XposedHelpers.getAdditionalInstanceField(decor, "newCanvas");
					
					if (newCanvas != null) {
						newCanvas.setBitmap(null);
					}
					
					if (newBitmap != null) {
						newBitmap.recycle();
					}
					
					XposedHelpers.setAdditionalInstanceField(decor, "newBitmap", null);
					XposedHelpers.setAdditionalInstanceField(decor, "newCanvas", null);

                    sendShouldBlackenIntent(activity, false, false);
				}
			}
		});
		
		XposedHelpers.findAndHookMethod(View.class, "draw", Canvas.class, new XC_MethodHook() {
			@Override
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam mhparams) throws Throwable {
				Boolean isDecor = (Boolean) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "isDecor");
				if (isDecor != null && isDecor) {
					Activity activity = (Activity) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "activity");

                    String packageName = activity.getApplicationInfo().packageName;
                    String className = activity.getClass().getName();
                    int tintMode = mSettings.getInt(packageName, className, Settings.TINT_MODE, Settings.TINT_MODE_CLASSIC);
                    boolean shouldBlackenBtn = false;
					
					if (activity.getActionBar() != null && !activity.getActionBar().isShowing()) return;
					
					Canvas canvas = (Canvas) mhparams.args[0];

					Bitmap newBitmap = (Bitmap) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "newBitmap");
					Canvas newCanvas = (Canvas) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "newCanvas");

					View v = (View) mhparams.thisObject;

					if (STATUS_HEIGHT == 0) {
						STATUS_HEIGHT = Utility.getStatusBarHeight(v.getContext()) + 1;
					}

                    if (navigationHeight == 0) {
                        navigationHeight = v.getHeight() - Utility.getNavigationBarHeight(v.getContext()) - 1;
                    }

					if (newBitmap == null) {
						// We only crop the top part of the view.
						newBitmap = Bitmap.createBitmap(v.getWidth(), tintMode == Settings.TINT_MODE_CLASSIC
                                ? STATUS_HEIGHT + 1 : v.getHeight(), Bitmap.Config.ARGB_4444);
						newBitmap.setHasAlpha(false);
						newBitmap.setDensity(canvas.getDensity());
					}

					if (newCanvas == null) {
						newCanvas = new Canvas();
						newCanvas.setBitmap(newBitmap);
					}
					
					// Get an image of the view
					// Do not use the "getDrawingCache" because
					// We do not need image of the entire view.
					((Method) mhparams.method).invoke(mhparams.thisObject, newCanvas);
					
					Window window = (Window) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "window");
					
					int width = v.getWidth();
					
					int color1 = newBitmap.getPixel(width / 2, STATUS_HEIGHT);
					int color2 = newBitmap.getPixel(1, STATUS_HEIGHT);
					int color3 = newBitmap.getPixel(width - 1, STATUS_HEIGHT);
					int color4 = newBitmap.getPixel(width / 4, STATUS_HEIGHT);
					int color5 = newBitmap.getPixel(width / 4 * 3, STATUS_HEIGHT);
					int color = Utility.colorAverage(color1, color2, color3, color4, color5);
					Integer colorLast = (Integer) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "lastColor");

                    switch (tintMode) {
                        case Settings.TINT_MODE_CLASSIC:
                            if (colorLast == null || color != colorLast) {
                                int dark = Utility.darkenColor(color, 0.8f);
                                window.setStatusBarColor(dark);
                                XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "lastColor", color);
                            }
                            break;

                        case Settings.TINT_MODE_FULL_TINTED:
                            if (colorLast == null || color != colorLast) {
                                window.setStatusBarColor(color);
                                XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "lastColor", color);
                            }

							// ignored navigation bar when landscape
							Configuration configuration = activity.getResources().getConfiguration();
							if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) break;

                            int navigationColor1 = newBitmap.getPixel(width / 2, navigationHeight);
                            int navigationColor2 = newBitmap.getPixel(1, navigationHeight);
                            int navigationColor3 = newBitmap.getPixel(width - 1, navigationHeight);
                            int navigationColor4 = newBitmap.getPixel(width / 4, navigationHeight);
                            int navigationColor5 = newBitmap.getPixel(width / 4 * 3, navigationHeight);
                            int navigationColor = Utility.colorAverage(navigationColor1,
                                    navigationColor2, navigationColor3, navigationColor4, navigationColor5);
                            Integer navigationColorLast = (Integer) XposedHelpers.getAdditionalInstanceField(mhparams.thisObject, "lastNavigationColor");
                            shouldBlackenBtn = (Color.red(navigationColor) + Color.green(navigationColor) + Color.blue(navigationColor)) > BLACKEN_THRESHOLD;
                            XposedHelpers.setAdditionalInstanceField(activity, "shouldBlackenBtn", shouldBlackenBtn);
                            if (navigationColorLast == null || navigationColor != navigationColorLast) {
                                window.setNavigationBarColor(navigationColor);
                                XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "lastNavigationColor", navigationColor);
                            }
                            break;
                    }

                    // Color in recents
                    ActivityManager.TaskDescription des = new ActivityManager.TaskDescription(null, null, color);
                    activity.setTaskDescription(des);

					XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "isDecor", false);
					
					//XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "oldCanvas", canvas);
					XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "newBitmap", newBitmap);
					XposedHelpers.setAdditionalInstanceField(mhparams.thisObject, "newCanvas", newCanvas);
					
					// We must mask the view as dirty, or we will never see it flush
					v.invalidate();

                    boolean shouldBlackenIcon = (Color.red(color) + Color.green(color) + Color.blue(color)) > BLACKEN_THRESHOLD;
                    XposedHelpers.setAdditionalInstanceField(activity, "shouldBlackenIcon", shouldBlackenIcon);
                    sendShouldBlackenIntent(activity, shouldBlackenIcon, shouldBlackenBtn);
                }
            }
        });
    }

    private static void sendShouldBlackenIntent(Context context, boolean blackenIcon, boolean blackenButton) {
        Intent intent = new Intent(ACTION_SHOULD_BLACKEN_ICONS_CHANGED);
        intent.putExtra(EXTRA_BLACKEN_ICONS, blackenIcon);
        intent.putExtra(EXTRA_BLACKEN_BUTTONS, blackenButton);
        context.sendBroadcast(intent);
    }

}
