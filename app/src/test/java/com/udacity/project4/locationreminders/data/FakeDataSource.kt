package com.udacity.project4.locationreminders.data

import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result

class FakeDataSource(var reminders: MutableList<ReminderDTO>? = mutableListOf()) : ReminderDataSource {

    private var returnError = false

    override suspend fun getReminders(): Result<List<ReminderDTO>> {
        if (returnError)
            return Result.Error("Test Exception")
        reminders?.let {
            return@let Result.Success(ArrayList(it))
        }
        return Result.Error("No reminders found")
    }

    override suspend fun saveReminder(reminder: ReminderDTO) {
        reminders?.add(reminder)
    }

    override suspend fun getReminder(id: String): Result<ReminderDTO> {
        if (returnError)
            return Result.Error("Test Exception")
        val found = reminders?.first { it.id == id }
        return if (found != null)
            Result.Success(found)
        else
            Result.Error("Reminder $id not found")
    }

    override suspend fun deleteAllReminders() {
        reminders?.clear()
    }

    fun returnErrorTest(value: Boolean) {
        returnError = value
    }
}