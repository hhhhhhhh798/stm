package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Cast implements Serializable {
    @SerializedName("name") private String name;
    @SerializedName("character") private String character;
    @SerializedName("profile_path") private String profilePath;
    @SerializedName("image") private String image;
    @SerializedName("photo") private String photo;

    public Cast() {}

    public String getName() { return name; }
    public String getCharacter() { return character; }
    public String getImageUrl() {
        if (profilePath != null && !profilePath.trim().isEmpty()) return profilePath;
        if (image != null && !image.trim().isEmpty()) return image;
        return photo;
    }
}
