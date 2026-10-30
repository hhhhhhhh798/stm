package com.stream.master;

import com.google.gson.annotations.SerializedName;

/**
 * Maps the tiny, hand-edited file at force_update_json_url (see api/index.js's
 * getGithubUrls() / GITHUB_FORCE_UPDATE_JSON). Deliberately a separate file and a separate
 * fetch from UpdateInfo: that file is regenerated fresh by CI on every release and would
 * silently overwrite any manual choice made here otherwise.
 */
public class ForceUpdateInfo {
    @SerializedName("force_update")
    public boolean force_update;
}
