/*
 * Supabase REST/Auth adapter for the NEChatroom Android sample.
 * Uses the app's existing OkHttp dependency; no audio SDK calls are made here.
 */
package com.netease.yunxin.app.chatroom.data;

import com.netease.yunxin.app.chatroom.BuildConfig;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SupabaseApi {
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final OkHttpClient HTTP =
      new OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  private final String baseUrl;
  private final String publishableKey;

  public interface Result<T> {
    void onSuccess(T value);
    void onError(String message);
  }

  private SupabaseApi() {
    baseUrl = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
    publishableKey =
        BuildConfig.SUPABASE_PUBLISHABLE_KEY == null
            ? ""
            : BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim();
  }

  private static final class Holder {
    private static final SupabaseApi INSTANCE = new SupabaseApi();
  }

  public static SupabaseApi getInstance() {
    return Holder.INSTANCE;
  }

  public boolean isConfigured() {
    return !baseUrl.isEmpty() && !publishableKey.isEmpty();
  }

  public void signUp(
      String email, String password, String username, String displayName, Result<JSONObject> result) {
    JSONObject payload = new JSONObject();
    JSONObject metadata = new JSONObject();
    try {
      payload.put("email", email);
      payload.put("password", password);
      metadata.put("username", username);
      metadata.put("display_name", displayName);
      payload.put("data", metadata);
    } catch (JSONException e) {
      result.onError(e.getMessage());
      return;
    }
    request("POST", "/auth/v1/signup", payload, null, result);
  }

  public void signIn(String email, String password, Result<JSONObject> result) {
    JSONObject payload = new JSONObject();
    try {
      payload.put("email", email);
      payload.put("password", password);
    } catch (JSONException e) {
      result.onError(e.getMessage());
      return;
    }
    request("POST", "/auth/v1/token?grant_type=password", payload, null, result);
  }

  public void signOut(String accessToken, Result<JSONObject> result) {
    request("POST", "/auth/v1/logout", new JSONObject(), accessToken, result);
  }

  public void listActiveRooms(String accessToken, Result<JSONArray> result) {
    String path =
        "/rest/v1/rooms?select=id,owner_id,title,description,cover_url,room_type,status,created_at"
            + "&status=eq.active&order=created_at.desc";
    requestArray("GET", path, null, accessToken, result);
  }

  public void createRoom(
      String accessToken,
      String userId,
      String title,
      String description,
      String roomType,
      Result<JSONObject> result) {
    JSONObject payload = new JSONObject();
    try {
      payload.put("owner_id", userId);
      payload.put("title", title);
      payload.put("description", description);
      payload.put("room_type", roomType == null ? "public" : roomType);
      payload.put("status", "active");
    } catch (JSONException e) {
      result.onError(e.getMessage());
      return;
    }

    request(
        "POST",
        "/rest/v1/rooms?select=id,owner_id,title,description,room_type,status,created_at",
        payload,
        accessToken,
        new Result<JSONObject>() {
          @Override
          public void onSuccess(JSONObject room) {
            String roomId = room.optString("id", "");
            if (roomId.isEmpty()) {
              result.onError("Supabase returned a room without an id.");
              return;
            }
            joinRoom(accessToken, roomId, userId, "owner", new Result<JSONObject>() {
              @Override
              public void onSuccess(JSONObject ignored) {
                result.onSuccess(room);
              }

              @Override
              public void onError(String message) {
                result.onError("Room was created, but joining as owner failed: " + message);
              }
            });
          }

          @Override
          public void onError(String message) {
            result.onError(message);
          }
        });
  }

  public void joinRoom(
      String accessToken, String roomId, String userId, String role, Result<JSONObject> result) {
    JSONObject payload = new JSONObject();
    try {
      payload.put("room_id", roomId);
      payload.put("user_id", userId);
      payload.put("role", role == null ? "member" : role);
    } catch (JSONException e) {
      result.onError(e.getMessage());
      return;
    }
    request(
        "POST",
        "/rest/v1/room_members?on_conflict=room_id,user_id",
        payload,
        accessToken,
        result,
        "resolution=merge-duplicates,return=representation");
  }

  public void leaveRoom(
      String accessToken, String roomId, String userId, Result<JSONObject> result) {
    String path =
        "/rest/v1/room_members?room_id=eq."
            + encode(roomId)
            + "&user_id=eq."
            + encode(userId);
    request("DELETE", path, null, accessToken, result);
  }

  public void listMessages(
      String accessToken, String roomId, Result<JSONArray> result) {
    String path =
        "/rest/v1/chat_messages?select=id,room_id,sender_id,message_type,body,created_at"
            + "&room_id=eq."
            + encode(roomId)
            + "&deleted_at=is.null&order=created_at.asc";
    requestArray("GET", path, null, accessToken, result);
  }

  public void sendMessage(
      String accessToken, String roomId, String senderId, String message, Result<JSONObject> result) {
    JSONObject payload = new JSONObject();
    try {
      payload.put("room_id", roomId);
      payload.put("sender_id", senderId);
      payload.put("message_type", "text");
      payload.put("body", message);
    } catch (JSONException e) {
      result.onError(e.getMessage());
      return;
    }
    request(
        "POST",
        "/rest/v1/chat_messages?select=id,room_id,sender_id,message_type,body,created_at",
        payload,
        accessToken,
        result);
  }

  private void requestArray(
      String method, String path, JSONObject payload, String accessToken, Result<JSONArray> result) {
    execute(method, path, payload, accessToken, null, new Result<String>() {
      @Override
      public void onSuccess(String body) {
        try {
          result.onSuccess(new JSONArray(body));
        } catch (JSONException e) {
          result.onError("Invalid JSON response from Supabase.");
        }
      }

      @Override
      public void onError(String message) {
        result.onError(message);
      }
    });
  }

  private void request(
      String method, String path, JSONObject payload, String accessToken, Result<JSONObject> result) {
    request(method, path, payload, accessToken, result, "return=representation");
  }

  private void request(
      String method,
      String path,
      JSONObject payload,
      String accessToken,
      Result<JSONObject> result,
      String prefer) {
    execute(method, path, payload, accessToken, prefer, new Result<String>() {
      @Override
      public void onSuccess(String body) {
        try {
          if (body == null || body.trim().isEmpty()) {
            result.onSuccess(new JSONObject());
          } else if (body.trim().startsWith("[")) {
            JSONArray array = new JSONArray(body);
            result.onSuccess(array.length() > 0 ? array.getJSONObject(0) : new JSONObject());
          } else {
            result.onSuccess(new JSONObject(body));
          }
        } catch (JSONException e) {
          result.onError("Invalid JSON response from Supabase.");
        }
      }

      @Override
      public void onError(String message) {
        result.onError(message);
      }
    });
  }

  private void execute(
      String method,
      String path,
      JSONObject payload,
      String accessToken,
      String prefer,
      Result<String> result) {
    if (!isConfigured()) {
      result.onError(
          "Supabase is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to Android/local.properties.");
      return;
    }
    if (path.startsWith("/rest/") && (accessToken == null || accessToken.trim().isEmpty())) {
      result.onError("A signed-in Supabase user is required.");
      return;
    }

    RequestBody body =
        payload == null ? null : RequestBody.create(payload.toString(), JSON);
    Request.Builder builder =
        new Request.Builder()
            .url(baseUrl + path)
            .header("apikey", publishableKey)
            .header("Accept", "application/json");
    if (accessToken != null && !accessToken.trim().isEmpty()) {
      builder.header("Authorization", "Bearer " + accessToken);
    } else if (path.startsWith("/rest/")) {
      builder.header("Authorization", "Bearer " + publishableKey);
    }
    if (prefer != null) {
      builder.header("Prefer", prefer);
    }
    builder.method(method, body);

    HTTP.newCall(builder.build()).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        result.onError(e.getMessage() == null ? "Network request failed." : e.getMessage());
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        try (Response closeable = response) {
          String responseBody = closeable.body() == null ? "" : closeable.body().string();
          if (!closeable.isSuccessful()) {
            result.onError(parseError(responseBody, closeable.code()));
          } else {
            result.onSuccess(responseBody);
          }
        }
      }
    });
  }

  private static String parseError(String body, int statusCode) {
    try {
      JSONObject error = new JSONObject(body);
      String message = error.optString("msg");
      if (message.isEmpty()) message = error.optString("message");
      if (message.isEmpty()) message = error.optString("error_description");
      if (message.isEmpty()) message = error.optString("error");
      if (!message.isEmpty()) return "Supabase (" + statusCode + "): " + message;
    } catch (JSONException ignored) {
      // Use the HTTP status when the response is not JSON.
    }
    return "Supabase request failed (HTTP " + statusCode + ").";
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return value;
    }
  }
}
