package com.stream.master;

import android.content.Context;
import android.view.View;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/** Shared helpers for persistent primary-navigation fragments. */
public abstract class BaseFragment extends Fragment {
    protected View rootView;

    protected Context ctx() { return requireContext(); }

    protected BaseActivity host() { return (BaseActivity) requireActivity(); }

    protected String getCurrentLang() { return host().getCurrentLang(); }

    protected void showCustomToast(String message) { host().showCustomToast(message); }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected <T extends View> T v(int id) {
        return rootView == null ? null : rootView.findViewById(id);
    }
}
