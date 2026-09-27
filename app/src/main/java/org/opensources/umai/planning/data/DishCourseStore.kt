package org.opensources.umai.planning.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.opensources.umai.core.settings.safeData
import org.opensources.umai.planning.domain.DishCourse

private val Context.dishCourseDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_dish_courses")

/** What the automatic planning knows about courses beyond what Mealie holds. */
interface DishCourses {

    /** The course the user gave categories and tags, by organizer id. */
    val userCourses: Flow<Map<String, DishCourse>>

    suspend fun setUserCourse(organizerId: String, course: DishCourse?)

    /** Courses the language model gave recipes nothing else placed, by recipe id. */
    suspend fun modelCourses(): Map<String, DishCourse>

    suspend fun rememberModelCourses(courses: Map<String, DishCourse>)
}

/**
 * Kept on the device: Mealie has no notion of the course of a category, and
 * the answers of the local model are only worth keeping to save the phone
 * from asking again.
 */
class DishCourseStore(context: Context) : DishCourses {

    private val dataStore = context.applicationContext.dishCourseDataStore
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override val userCourses: Flow<Map<String, DishCourse>> = dataStore.safeData
        .map { decode(it[KeyUser]) }

    override suspend fun setUserCourse(organizerId: String, course: DishCourse?) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KeyUser]).toMutableMap()
            if (course == null) current.remove(organizerId) else current[organizerId] = course
            prefs[KeyUser] = encode(current)
        }
    }

    override suspend fun modelCourses(): Map<String, DishCourse> =
        decode(dataStore.safeData.first()[KeyModel])

    override suspend fun rememberModelCourses(courses: Map<String, DishCourse>) {
        if (courses.isEmpty()) return
        dataStore.edit { prefs -> prefs[KeyModel] = encode(decode(prefs[KeyModel]) + courses) }
    }

    /** Both maps are keyed by ids of the instance they were written for. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decode(text: String?): Map<String, DishCourse> {
        if (text == null) return emptyMap()
        val raw = try {
            Json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            // Written by an older build in another shape: the courses are chosen again.
            emptyMap()
        }
        return raw.mapNotNull { (id, name) -> DishCourse.entries.firstOrNull { it.name == name }?.let { id to it } }.toMap()
    }

    private fun encode(courses: Map<String, DishCourse>): String =
        Json.encodeToString(serializer, courses.mapValues { it.value.name })

    private companion object {
        val KeyUser = stringPreferencesKey("user_courses")
        // Versioned with the prompt of ModelCourseClassifier: new questions, new answers.
        val KeyModel = stringPreferencesKey("model_courses_v3")
    }
}
