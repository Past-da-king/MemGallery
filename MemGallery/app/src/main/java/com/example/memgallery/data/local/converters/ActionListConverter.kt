package com.example.memgallery.data.local.converters

import androidx.room.TypeConverter
import com.example.memgallery.data.remote.dto.ActionDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ActionListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromActionList(actions: List<ActionDto>?): String? {
        if (actions == null) return null
        val type = object : TypeToken<List<ActionDto>>() {}.type
        return gson.toJson(actions, type)
    }

    @TypeConverter
    fun toActionList(actionsString: String?): List<ActionDto>? {
        if (actionsString == null) return null
        val type = object : TypeToken<List<ActionDto>>() {}.type
        return gson.fromJson(actionsString, type)
    }
}
