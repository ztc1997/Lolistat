package info.papdt.lolistat.mod;

import android.widget.ImageView;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class ModSignalCluster {
    private static final String[] SIGNAL_CLUSTER_ICON_NAMES = {
            "mMobile", "mMobileActivity", "mMobileType",
            "mMobileRoaming", "mWifi", "mWifiActivity",
            "mEthernet", "mEthernetActivity", "mAirplane",
            "mPhoneSignal", "mNoSimSlot", "mVpn"
    };

    private static final String[] MOTO_G_ICON_NAMES = {
            "mMobileActivityView", "mMobileActivityView2",
            "mMobileRoamingView", "mMobileRoamingView2",
            "mMobileSimView", "mMobileSimView2",
            "mMobileStrengthView", "mMobileStrengthView2",
            "mMobileTypeView", "mMobileTypeView2",
            "mWifiActivityView", "mWifiStrengthView"
    };

    private static final String[] LG_ICON_NAMES = {
            "mThirdType","mThirdType2","mThirdActivity"
    };

    //private static boolean shouldBlackenIcons = false;
    //private static LinkedHashSet<ImageView> imageViews = new LinkedHashSet<>();
    private static XC_MethodHook mSignalClusterHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            for (String name : SIGNAL_CLUSTER_ICON_NAMES) {
                try {
                    ImageView view = (ImageView) XposedHelpers.getObjectField(param.thisObject, name);
                    ModSystemUI.addSysIconView(view);
                } catch (NoSuchFieldError ignored) {
                }
            }

            for (String name : MOTO_G_ICON_NAMES) {
                try {
                    ImageView view = (ImageView) XposedHelpers.getObjectField(param.thisObject, name);
                    ModSystemUI.addSysIconView(view);
                } catch (NoSuchFieldError ignored) { }
            }

            for (String name : LG_ICON_NAMES) {
                try {
                    ImageView view = (ImageView) XposedHelpers.getObjectField(param.thisObject, name);
                    ModSystemUI.addSysIconView(view);
                } catch (NoSuchFieldError ignored) { }
            }

            //XposedBridge.log("ModSignalCluster.imageViews = " + imageViews);
            //((View) param.thisObject).getContext().registerReceiver(broadcastReceiver, new IntentFilter(ModLoli.ACTION_SHOULD_BLACKEN_ICONS_CHANGED));
        }
    };

    public static void doHooks(ClassLoader classLoader) {

        String className = "com.android.systemui.statusbar.SignalClusterView";
        String methodName = "onAttachedToWindow";
        try {
            Class<?> SignalClusterView = XposedHelpers.findClass(className, classLoader);

            try {
                findAndHookMethod(SignalClusterView, methodName, mSignalClusterHook);
            } catch (NoSuchMethodError ignored) {
            }
        } catch (XposedHelpers.ClassNotFoundError ignored) {
        }

        try {
            Class<?> MSimSignalClusterView = XposedHelpers.findClass("com.android.systemui.statusbar.MSimSignalClusterView",
                    classLoader);
            findAndHookMethod(MSimSignalClusterView, methodName, mSignalClusterHook);
        } catch (Throwable t) {
            // Not a Moto G
        }

		/* HTC Specific hook */
        if (!android.os.Build.MANUFACTURER.toLowerCase(Locale.getDefault()).contains("htc"))
            return;

        try {
            Class<?> HTCClusterView =
                    XposedHelpers.findClass("com.android.systemui.statusbar.HtcGenericSignalClusterView", classLoader);

            findAndHookMethod(HTCClusterView, methodName, mSignalClusterHook);
        } catch (Throwable ignored) {}
    }
/*
    private static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(ModLoli.ACTION_SHOULD_BLACKEN_ICONS_CHANGED)
                    || !intent.hasExtra(ModLoli.EXTRA_BLACKEN_ICONS)) return;
            shouldBlackenIcons = intent.getBooleanExtra(ModLoli.EXTRA_BLACKEN_ICONS, false);
            for (ImageView imageView : imageViews) {
                if (imageView != null) {
                    Drawable drawable = imageView.getDrawable();
                    if (drawable != null)
                    drawable.setColorFilter(shouldBlackenIcons ? Color.BLACK : Color.WHITE, PorterDuff.Mode.MULTIPLY);
                }
            }
        }
    };*/
}
