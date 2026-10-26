package com.stream.master;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;


public class DownloadsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        // Bottom navigation is owned centrally by BaseActivity.

        View browse = findViewById(R.id.btnBrowseDownloads);
        if (browse != null) {
            browse.setOnClickListener(v -> goTo(MainActivity.class));
        }
    }

    private void goTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
