package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.MainCoroutineRule
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@MediumTest
class RemindersLocalRepositoryTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()
    private lateinit var database: RemindersDatabase
    private lateinit var remindersDAO: RemindersDao
    private lateinit var repository: RemindersLocalRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        remindersDAO = database.reminderDao()
        repository =
            RemindersLocalRepository(
                remindersDAO,
                Dispatchers.Main
            )
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun saveReminder_GetById() = mainCoroutineRule.runBlockingTest {
        val reminder = ReminderDTO(
            title = "Work",
            description = "Don't follow your passion but do deep work",
            location = "Dallas",
            latitude = 32.8208751,
            longitude = -96.8716341
        )
        repository.saveReminder(reminder)
        val reminderLoaded = repository.getReminder(reminder.id) as Result.Success<ReminderDTO>
        val loaded = reminderLoaded.data

        assertThat(loaded, Matchers.notNullValue())
        assertThat(loaded.id, `is`(reminder.id))
        assertThat(loaded.description, `is`(reminder.description))
        assertThat(loaded.location, `is`(reminder.location))
        assertThat(loaded.latitude, `is`(reminder.latitude))
        assertThat(loaded.longitude, `is`(reminder.longitude))
    }

    @Test
    fun deleteAll_Reminders() = mainCoroutineRule.runBlockingTest {
        val reminder = ReminderDTO(
            title = "Work",
            description = "Don't follow your passion but do deep work",
            location = "Dallas",
            latitude = 32.8208751,
            longitude = -96.8716341
        )
        repository.saveReminder(reminder)
        repository.deleteAllReminders()
        val reminders = repository.getReminders() as Result.Success<List<ReminderDTO>>
        val data = reminders.data
        assertThat(data.isEmpty(), `is`(true))

    }

    @Test
    fun notFound_GetById() = mainCoroutineRule.runBlockingTest {
        val reminder = repository.getReminder("3") as Result.Error
        assertThat(reminder.message, Matchers.notNullValue())
        assertThat(reminder.message, `is`("Reminder not found!"))
    }

}