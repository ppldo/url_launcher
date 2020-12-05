package io.flutter.plugins.urllauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Launches components for URLs. */
class UrlLauncher {
  private final Context applicationContext;
  @Nullable private Activity activity;
  private final List<String> browsers;

  /**
   * Uses the given {@code applicationContext} for launching intents.
   *
   * <p>It may be null initially, but should be set before calling {@link #launch}.
   */
  UrlLauncher(Context applicationContext, @Nullable Activity activity) {
    this.applicationContext = applicationContext;
    this.activity = activity;
    this.browsers = getListOfBrowser(applicationContext);
  }

  public static List<String> getListOfBrowser(Context context) {
    List<String> browserPackageName = new ArrayList<String>();
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("https://www.un-existing-site-i-think.com"));
      PackageManager pm = context.getPackageManager();
      List<ResolveInfo> browserList = pm.queryIntentActivities(intent, 0);
      for (ResolveInfo info : browserList) {
        browserPackageName.add(info.activityInfo.packageName);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return browserPackageName;
  }

  void setActivity(@Nullable Activity activity) {
    this.activity = activity;
  }

  /** Returns whether the given {@code url} resolves into an existing component. */
  boolean canLaunch(String url, boolean forbidBrowser) {
    Intent launchIntent = new Intent(Intent.ACTION_VIEW);
    launchIntent.setData(Uri.parse(url));
    ActivityInfo info =
        launchIntent.resolveActivityInfo(applicationContext.getPackageManager(), 0);

    return info != null && (!forbidBrowser || this.browsers.contains(info.packageName));
  }

  /**
   * Attempts to launch the given {@code url}.
   *
   * @param headersBundle forwarded to the intent as {@code Browser.EXTRA_HEADERS}.
   * @param useWebView when true, the URL is launched inside of {@link WebViewActivity}.
   * @param enableJavaScript Only used if {@param useWebView} is true. Enables JS in the WebView.
   * @param enableDomStorage Only used if {@param useWebView} is true. Enables DOM storage in the
   * @return {@link LaunchStatus#NO_ACTIVITY} if there's no available {@code applicationContext}.
   *     {@link LaunchStatus#ACTIVITY_NOT_FOUND} if there's no activity found to handle {@code
   *     launchIntent}. {@link LaunchStatus#OK} otherwise.
   */
  LaunchStatus launch(
      String url,
      Bundle headersBundle,
      boolean useWebView,
      boolean enableJavaScript,
      boolean enableDomStorage,
      boolean forbidBrowser) {
    if (activity == null) {
      return LaunchStatus.NO_ACTIVITY;
    }

    if (!this.canLaunch(url, forbidBrowser)) {
      return LaunchStatus.ACTIVITY_NOT_FOUND;
    }

    Intent launchIntent;
    if (useWebView) {
      launchIntent =
          WebViewActivity.createIntent(
              activity, url, enableJavaScript, enableDomStorage, headersBundle);
    } else {
      launchIntent =
          new Intent(Intent.ACTION_VIEW)
              .setData(Uri.parse(url))
              .putExtra(Browser.EXTRA_HEADERS, headersBundle);
    }

    try {
      activity.startActivity(launchIntent);
    } catch (ActivityNotFoundException e) {
      return LaunchStatus.ACTIVITY_NOT_FOUND;
    }

    return LaunchStatus.OK;
  }

  /** Closes any activities started with {@link #launch} {@code useWebView=true}. */
  void closeWebView() {
    applicationContext.sendBroadcast(new Intent(WebViewActivity.ACTION_CLOSE));
  }

  /** Result of a {@link #launch} call. */
  enum LaunchStatus {
    /** The intent was well formed. */
    OK,
    /** No activity was found to launch. */
    NO_ACTIVITY,
    /** No Activity found that can handle given intent. */
    ACTIVITY_NOT_FOUND,
  }
}
