package com.netease.yunxin.app.chatroom;

import android.app.Application;
import com.netease.yunxin.kit.alog.ALog;

/**
 * Application entry for the text-first Supabase build.
 * Audio/NetEase room services are intentionally not initialized here.
 */
public class VoiceRoomApplication extends Application {

  private static final String TAG = "NEChatroom";

  @Override
  public void onCreate() {
    super.onCreate();
    ALog.init(this, ALog.LEVEL_ALL);
    ALog.i(TAG, "Text-first Supabase build started");
  }
}
