package com.udacity.project4

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment.Companion.buildToastMessage
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.util.DataBindingIdlingResource
import com.udacity.project4.util.monitorActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.get

@RunWith(AndroidJUnit4::class)
@LargeTest
class RemindersActivityTest :
    AutoCloseKoinTest() {

        private lateinit var repository: ReminderDataSource
        private lateinit var appContext: Application
        private val dataBindingIdlingResource = DataBindingIdlingResource()

        /**
         * As we use Koin as a Service Locator Library to develop our code, we'll also use Koin to test our code.
         * at this step we will initialize Koin related code to be able to use it in out testing.
         */

        @Before
        fun init() {
            stopKoin()
            appContext = getApplicationContext()
            val myModule = module {
                viewModel {
                    RemindersListViewModel(
                        appContext,
                        get() as ReminderDataSource
                    )
                }
                single {
                    SaveReminderViewModel(
                        appContext,
                        get() as ReminderDataSource
                    )
                }
                single { RemindersLocalRepository(get()) as ReminderDataSource }
                single { LocalDB.createRemindersDao(appContext) }
            }

            startKoin {
                modules(listOf(myModule))
            }
            repository = get()

            runBlocking {
                repository.deleteAllReminders()
            }
        }

        @Before
        fun registerIdlingResources() {
            IdlingRegistry.getInstance().register(dataBindingIdlingResource)
        }

        @After
        fun unregisterIdlingResources() {
            IdlingRegistry.getInstance().unregister(dataBindingIdlingResource)
        }

        @Test
        fun addReminder() = runBlocking {
            val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
            dataBindingIdlingResource.monitorActivity(activityScenario)

            Espresso.onView(withId(R.id.noDataTextView)).check(matches(isDisplayed()))
            Espresso.onView(withId(R.id.addReminderFAB)).perform(click())
            Espresso.onView(withId(R.id.reminderTitle))
                .perform(ViewActions.replaceText("Simple reminder"))
            Espresso.onView(withId(R.id.reminderDescription))
                .perform(ViewActions.replaceText("A place where you can maintain simplicity"))
            Espresso.onView(withId(R.id.selectLocation)).perform(click())
            Espresso.onView(withId(R.id.map)).perform(click())
            Espresso.pressBack()

            repository.saveReminder(
                ReminderDTO(
                    "Simple reminder",
                    "A place where you can maintain simplicity",
                    "Anywhere",
                    0.0,
                    0.0
                )
            )
            Espresso.onView(withText("Simple reminder"))
                .check(matches(isDisplayed()))
            Espresso.onView(withText("A place where you can maintain simplicity"))
                .check(matches(isDisplayed()))
            activityScenario.close()

        }

    /** Test Toast is displayed after Saving on Database **/
        @Test
        fun addReminderToDB_Toast()  {
            val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
            dataBindingIdlingResource.monitorActivity(activityScenario)

        Espresso.onView(withId(R.id.addReminderFAB)).perform(click())
        Espresso.onView(withId(R.id.reminderTitle))
            .perform(ViewActions.replaceText("Simple reminder"))
        Espresso.onView(withId(R.id.reminderDescription))
            .perform(ViewActions.replaceText("A place where you can maintain simplicity"))
        Espresso.onView(withId(R.id.selectLocation)).perform(click())
        Espresso.onView(withId(R.id.map)).perform(click())
        Espresso.onView(withId(R.id.saveLocation)).perform(click())
        Espresso.onView(withId(R.id.saveReminder)).perform(click())

        Espresso.onView(
            withText(buildToastMessage("Reminder Saved !")))
            .inRoot(ToastMatcher())
            .check(matches(isDisplayed()))
        activityScenario.close()
        }

        /** Test Snackbar is displayed after Error **/
        @Test
        fun addReminder_ErrorSnackBarShown()  {
            val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)
            dataBindingIdlingResource.monitorActivity(activityScenario)

            // Click on the edit button, create, and save.
            Espresso.onView(withId(R.id.addReminderFAB)).perform(click())
            Espresso.onView(withId(R.id.reminderTitle))
                .perform(ViewActions.replaceText("Simple reminder"))
            Espresso.onView(withId(R.id.reminderDescription))
                .perform(ViewActions.replaceText("A place where you can maintain simplicity"))
            Espresso.onView(withId(R.id.saveReminder)).perform(click())

            // Verify error snackbar is displayed on screen.
            Espresso.onView(withText(R.string.err_select_location))
                .check(matches(isDisplayed()))

            activityScenario.close()
        }
}


