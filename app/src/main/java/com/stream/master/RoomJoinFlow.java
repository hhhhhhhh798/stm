package com.stream.master;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.Locale;

/** "Join a watch party": ask for the code, check the room, confirm, then open the player as a guest. */
final class RoomJoinFlow {
    private RoomJoinFlow() { }

    private static final String[] STRING_EXTRAS = {
            "VID", "TITLE", "IMG", "DESC", "RATING", "YEAR", "QUALITY",
            "GENRE", "DIRECTOR", "DURATION", "LANGUAGE", "TMDB_ID", "TYPE"};

    /** [prefill] comes from an invite link (streammaster://room/CODE); null shows the code prompt. */
    static void show(Activity activity, String prefill) {
        final String prefilled = RoomClient.parseCode(prefill);
        if (prefilled != null) {
            check(activity, prefilled, RoomClient.savedName(activity));
            return;
        }

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        box.setPadding(pad, dp(activity, 8), pad, 0);

        final EditText code = new EditText(activity);
        code.setHint(R.string.room_code_hint);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(40)}); // 40: room for a pasted link
        code.setSingleLine(true);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        box.addView(code, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText name = new EditText(activity);
        name.setHint(R.string.room_name_hint);
        name.setSingleLine(true);
        name.setText(RoomClient.savedName(activity));
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        box.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.room_join_title)
                .setView(box)
                .setPositiveButton(R.string.room_join_button, null)   // set below so a bad code keeps the dialog open
                .setNegativeButton(R.string.room_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String parsed = RoomClient.parseCode(code.getText().toString());
            if (parsed == null) {
                code.setError(activity.getString(R.string.room_invalid_code));
                return;
            }
            String n = name.getText().toString().trim();
            RoomClient.saveName(activity, n);
            dialog.dismiss();
            check(activity, parsed, n);
        }));
        dialog.show();
    }

    private static void check(Activity activity, String code, String name) {
        Toast.makeText(activity, R.string.room_checking, Toast.LENGTH_SHORT).show();
        RoomClient.preview(activity, code, (room, error) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (room == null) {
                int msg = "room_not_found".equals(error) || "bad_code".equals(error) ? R.string.room_not_found
                        : "network".equals(error) ? R.string.room_network_error : R.string.room_not_found;
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                return;
            }
            if (room.optBoolean("full")) {
                Toast.makeText(activity, R.string.room_full, Toast.LENGTH_LONG).show();
                return;
            }
            JSONObject media = room.optJSONObject("media");
            if (media == null) {
                Toast.makeText(activity, R.string.room_not_found, Toast.LENGTH_LONG).show();
                return;
            }
            String host = room.optString("hostName", "");
            if (host.isEmpty() || "null".equals(host)) host = activity.getString(R.string.room_host_unknown);
            String message = String.format(Locale.getDefault(), activity.getString(R.string.room_join_confirm_fmt),
                    media.optString("TITLE", ""), host);
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.room_join_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.room_join_button, (d, w) -> activity.startActivity(playerIntent(activity, media, code, name)))
                    .setNegativeButton(R.string.room_cancel, null)
                    .show();
        });
    }

    /** The same extras the normal "play" buttons set, plus the room. The player joins the room itself. */
    static Intent playerIntent(Context context, JSONObject media, String code, String name) {
        Intent intent = new Intent(context, PlayerActivity.class);
        for (String key : STRING_EXTRAS) {
            String v = media.optString(key, "");
            if (!v.isEmpty()) intent.putExtra(key, v);
        }
        if (media.has("SEASON")) intent.putExtra("SEASON", media.optInt("SEASON"));
        if (media.has("EPISODE")) intent.putExtra("EPISODE", media.optInt("EPISODE"));
        intent.putExtra("ROOM_CODE", code);
        intent.putExtra("ROOM_NAME", name == null ? "" : name);
        return intent;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
