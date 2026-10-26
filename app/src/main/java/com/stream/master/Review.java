package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Review implements Serializable {
    @SerializedName("author") private String author;
    @SerializedName("author_name") private String authorName;
    @SerializedName("username") private String username;
    @SerializedName("rating") private String rating;
    @SerializedName("score") private String score;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("date") private String date;
    @SerializedName("content") private String content;
    @SerializedName("review") private String review;
    @SerializedName("avatar_path") private String avatarPath;
    @SerializedName("avatar") private String avatar;
    @SerializedName("image") private String image;

    public Review() {}

    public String getAuthorName() {
        if (author != null && !author.trim().isEmpty()) return author;
        if (authorName != null && !authorName.trim().isEmpty()) return authorName;
        return username;
    }
    public String getRating() { return rating != null && !rating.isEmpty() ? rating : score; }
    public String getDate() { return date != null && !date.isEmpty() ? date : createdAt; }
    public String getContent() { return content != null && !content.isEmpty() ? content : review; }
    public String getAvatarUrl() {
        if (avatarPath != null && !avatarPath.trim().isEmpty()) return avatarPath;
        if (avatar != null && !avatar.trim().isEmpty()) return avatar;
        return image;
    }
}
