package com.netease.yunxin.app.chatroom.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.netease.yunxin.app.chatroom.data.SupabaseApi;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Text-first Supabase app entry point. Audio/NERoom is deliberately not started.
 * Session tokens are kept in memory for this first integration pass.
 */
public class SupabaseTextActivity extends Activity {
  private final SupabaseApi api = SupabaseApi.getInstance();
  private String accessToken;
  private String userId;
  private String currentRoomId;
  private String currentRoomTitle;
  private LinearLayout content;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!api.isConfigured()) {
      showMessage(
          "Supabase is not configured",
          "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to Android/local.properties, then sync Gradle.");
      showLogin();
      return;
    }
    showLogin();
  }

  private void showLogin() {
    LinearLayout root = page("NEChatroom");
    addText(root, "تسجيل الدخول أو إنشاء حساب", 20, true);
    EditText email = input("البريد الإلكتروني");
    EditText password = input("كلمة المرور");
    password.setInputType(129);
    EditText username = input("اسم المستخدم (للحساب الجديد)");
    EditText displayName = input("الاسم الظاهر");
    root.addView(email);
    root.addView(password);
    root.addView(username);
    root.addView(displayName);

    Button login = button("تسجيل الدخول");
    login.setOnClickListener(v -> {
      String e = email.getText().toString().trim();
      String p = password.getText().toString();
      if (e.isEmpty() || p.isEmpty()) {
        showMessage("بيانات ناقصة", "أدخل البريد الإلكتروني وكلمة المرور.");
        return;
      }
      setBusy(login, true);
      api.signIn(e, p, new SupabaseApi.Result<JSONObject>() {
        @Override public void onSuccess(JSONObject value) {
          runOnUiThread(() -> {
            setBusy(login, false);
            acceptSession(value);
          });
        }
        @Override public void onError(String message) {
          runOnUiThread(() -> {
            setBusy(login, false);
            showMessage("تعذر تسجيل الدخول", message);
          });
        }
      });
    });
    root.addView(login);

    Button register = button("إنشاء حساب جديد");
    register.setOnClickListener(v -> {
      String e = email.getText().toString().trim();
      String p = password.getText().toString();
      String u = username.getText().toString().trim();
      String d = displayName.getText().toString().trim();
      if (e.isEmpty() || p.length() < 6 || u.isEmpty()) {
        showMessage("بيانات غير مكتملة", "أدخل البريد واسم المستخدم وكلمة مرور من 6 أحرف على الأقل.");
        return;
      }
      setBusy(register, true);
      api.signUp(e, p, u, d.isEmpty() ? u : d, new SupabaseApi.Result<JSONObject>() {
        @Override public void onSuccess(JSONObject value) {
          runOnUiThread(() -> {
            setBusy(register, false);
            if (value.has("access_token")) {
              acceptSession(value);
            } else {
              showMessage("تم إنشاء الحساب", "إذا كان تأكيد البريد مفعلاً، افتح رسالة التأكيد ثم سجّل الدخول.");
            }
          });
        }
        @Override public void onError(String message) {
          runOnUiThread(() -> {
            setBusy(register, false);
            showMessage("تعذر إنشاء الحساب", message);
          });
        }
      });
    });
    root.addView(register);
    addText(root, "نسخة تجريبية نصية: الصوت غير مفعّل في هذه المرحلة.", 13, false);
    setContentView(wrap(root));
  }

  private void acceptSession(JSONObject session) {
    accessToken = session.optString("access_token", "");
    JSONObject user = session.optJSONObject("user");
    if (user == null) user = session;
    userId = user.optString("id", "");
    if (accessToken.isEmpty() || userId.isEmpty()) {
      showMessage("تأكيد البريد مطلوب", "تحقق من بريدك الإلكتروني ثم سجّل الدخول.");
      return;
    }
    showRooms();
  }

  private void showRooms() {
    LinearLayout root = page("الغرف الصوتية (دردشة كتابية مؤقتًا)");
    addText(root, "مرحبًا بك", 18, true);
    Button create = button("＋ إنشاء غرفة");
    create.setOnClickListener(v -> promptCreateRoom());
    root.addView(create);
    Button refresh = button("تحديث الغرف");
    refresh.setOnClickListener(v -> loadRooms(root));
    root.addView(refresh);
    addText(root, "الغرف المتاحة", 16, true);
    setContentView(wrap(root));
    loadRooms(root);
  }

  private void loadRooms(LinearLayout root) {
    api.listActiveRooms(accessToken, new SupabaseApi.Result<JSONArray>() {
      @Override public void onSuccess(JSONArray rooms) {
        runOnUiThread(() -> {
          root.removeViews(4, Math.max(0, root.getChildCount() - 4));
          if (rooms.length() == 0) addText(root, "لا توجد غرف بعد. أنشئ أول غرفة.", 14, false);
          for (int i = 0; i < rooms.length(); i++) {
            JSONObject room = rooms.optJSONObject(i);
            if (room == null) continue;
            String id = room.optString("id");
            String title = room.optString("title", "غرفة بدون اسم");
            String description = room.optString("description", "");
            Button open = button(title + (description.isEmpty() ? "" : "\n" + description));
            open.setOnClickListener(v -> enterRoom(id, title));
            root.addView(open);
          }
        });
      }
      @Override public void onError(String message) {
        runOnUiThread(() -> showMessage("تعذر تحميل الغرف", message));
      }
    });
  }

  private void promptCreateRoom() {
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(12);
    form.setPadding(pad, pad, pad, pad);
    EditText title = input("اسم الغرفة");
    EditText description = input("وصف الغرفة");
    form.addView(title);
    form.addView(description);
    new AlertDialog.Builder(this)
        .setTitle("إنشاء غرفة")
        .setView(form)
        .setNegativeButton("إلغاء", null)
        .setPositiveButton("إنشاء", (dialog, which) -> {
          String name = title.getText().toString().trim();
          if (name.isEmpty()) {
            showMessage("اسم الغرفة مطلوب", "اكتب اسمًا للغرفة.");
            return;
          }
          api.createRoom(
              accessToken,
              userId,
              name,
              description.getText().toString().trim(),
              "public",
              new SupabaseApi.Result<JSONObject>() {
                @Override public void onSuccess(JSONObject room) {
                  runOnUiThread(() -> {
                    currentRoomId = room.optString("id");
                    currentRoomTitle = room.optString("title", name);
                    showChat();
                  });
                }
                @Override public void onError(String message) {
                  runOnUiThread(() -> showMessage("تعذر إنشاء الغرفة", message));
                }
              });
        })
        .show();
  }

  private void enterRoom(String roomId, String roomTitle) {
    api.joinRoom(accessToken, roomId, userId, "member", new SupabaseApi.Result<JSONObject>() {
      @Override public void onSuccess(JSONObject ignored) {
        runOnUiThread(() -> {
          currentRoomId = roomId;
          currentRoomTitle = roomTitle;
          showChat();
        });
      }
      @Override public void onError(String message) {
        runOnUiThread(() -> {
          // The owner may already have been inserted as a member during room creation.
          if (message.toLowerCase().contains("duplicate") || message.toLowerCase().contains("unique")) {
            currentRoomId = roomId;
            currentRoomTitle = roomTitle;
            showChat();
          } else {
            showMessage("تعذر دخول الغرفة", message);
          }
        });
      }
    });
  }

  private void showChat() {
    LinearLayout root = page(currentRoomTitle == null ? "الغرفة" : currentRoomTitle);
    Button back = button("← العودة إلى الغرف");
    back.setOnClickListener(v -> leaveCurrentRoom());
    root.addView(back);
    Button refresh = button("تحديث الرسائل");
    root.addView(refresh);
    ScrollView scroll = new ScrollView(this);
    LinearLayout messages = new LinearLayout(this);
    messages.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(messages);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    EditText message = input("اكتب رسالة...");
    Button send = button("إرسال");
    LinearLayout composer = new LinearLayout(this);
    composer.setOrientation(LinearLayout.HORIZONTAL);
    composer.addView(message, new LinearLayout.LayoutParams(0, -2, 1));
    composer.addView(send);
    root.addView(composer);
    refresh.setOnClickListener(v -> loadMessages(messages));
    send.setOnClickListener(v -> {
      String body = message.getText().toString().trim();
      if (body.isEmpty()) return;
      setBusy(send, true);
      api.sendMessage(accessToken, currentRoomId, userId, body, new SupabaseApi.Result<JSONObject>() {
        @Override public void onSuccess(JSONObject ignored) {
          runOnUiThread(() -> {
            setBusy(send, false);
            message.setText("");
            loadMessages(messages);
          });
        }
        @Override public void onError(String error) {
          runOnUiThread(() -> {
            setBusy(send, false);
            showMessage("تعذر إرسال الرسالة", error);
          });
        }
      });
    });
    setContentView(wrap(root));
    loadMessages(messages);
  }

  private void loadMessages(LinearLayout messages) {
    api.listMessages(accessToken, currentRoomId, new SupabaseApi.Result<JSONArray>() {
      @Override public void onSuccess(JSONArray rows) {
        runOnUiThread(() -> {
          messages.removeAllViews();
          for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String sender = row.optString("sender_id", "");
            if (sender.length() > 8) sender = sender.substring(0, 8);
            addText(messages, sender + ": " + row.optString("body", ""), 14, false);
          }
        });
      }
      @Override public void onError(String error) {
        runOnUiThread(() -> showMessage("تعذر تحميل الرسائل", error));
      }
    });
  }

  private void leaveCurrentRoom() {
    String roomId = currentRoomId;
    currentRoomId = null;
    if (roomId != null) {
      api.leaveRoom(accessToken, roomId, userId, new SupabaseApi.Result<JSONObject>() {
        @Override public void onSuccess(JSONObject ignored) {}
        @Override public void onError(String ignored) {}
      });
    }
    showRooms();
  }

  private LinearLayout page(String title) {
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(18), dp(20), dp(18), dp(16));
    content.setBackgroundColor(0xFFF7F7FA);
    addText(content, title, 22, true);
    return content;
  }

  private View wrap(View view) {
    ScrollView scroll = new ScrollView(this);
    scroll.addView(view);
    return scroll;
  }

  private EditText input(String hint) {
    EditText edit = new EditText(this);
    edit.setSingleLine(true);
    edit.setHint(hint);
    edit.setTextSize(16);
    return edit;
  }

  private Button button(String label) {
    Button button = new Button(this);
    button.setText(label);
    return button;
  }

  private void addText(LinearLayout parent, String value, int size, boolean bold) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(0xFF20212A);
    if (bold) text.setTypeface(null, 1);
    text.setPadding(0, dp(8), 0, dp(8));
    parent.addView(text);
  }

  private void setBusy(Button button, boolean busy) {
    button.setEnabled(!busy);
    if (busy) button.setText("جارٍ التنفيذ...");
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private void showMessage(String title, String message) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("حسنًا", null)
        .show();
  }

  @Override
  public void onBackPressed() {
    if (currentRoomId != null) {
      leaveCurrentRoom();
    } else {
      super.onBackPressed();
    }
  }
}
