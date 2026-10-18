package com.stream.master;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class DownloadsFragment extends BaseFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_downloads, container, false);
        View browse = v(R.id.btnBrowseDownloads);
        if (browse != null) browse.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showHomeFromButton();
        });
        View search = v(R.id.globalSearchButton);
        if (search != null) search.setOnClickListener(x -> { if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).openSearch(); });
        return rootView;
    }
}
