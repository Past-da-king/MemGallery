package com.example.memgallery.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ActionDto(
    @SerializedName("type")
    val type: String, // "EVENT", "TODO", "REMINDER"

    @SerializedName("description")
    val description: String,

    @SerializedName("date")
    val date: String?, // YYYY-MM-DD

    @SerializedName("time")
    val time: String? // HH:MM
)
