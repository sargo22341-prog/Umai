package org.opensources.umai.planning.data

import org.opensources.umai.core.format.ApiDates
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.CreateMealPlanEntryDto
import org.opensources.umai.core.network.dto.MealPlanEntryDto
import org.opensources.umai.core.network.dto.UpdateMealPlanEntryDto
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.profile.data.toDomain
import org.opensources.umai.recipe.data.toDomain
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Meal plan backed entirely by `/api/households/mealplans`. Umai keeps no local
 * planning database: Mealie is the source of truth.
 */
class MealPlanRepository(private val apiProvider: () -> MealieApi?) {

    /**
     * The day the household's weeks start on, a preference of Mealie
     * (`GET /api/households/preferences`) the meal plan follows.
     */
    suspend fun firstDayOfWeek(): ApiResult<DayOfWeek> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.householdPreferences().toDomain().firstDay }
    }

    suspend fun entries(start: LocalDate, end: LocalDate): ApiResult<List<MealPlanEntry>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            api.mealPlans(
                startDate = ApiDates.format(start),
                endDate = ApiDates.format(end),
            ).items.mapNotNull { it.toDomain() }
        }
    }

    suspend fun add(
        date: LocalDate,
        type: MealType,
        recipeId: String?,
        title: String = "",
        text: String = "",
    ): ApiResult<MealPlanEntry> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            api.createMealPlan(
                CreateMealPlanEntryDto(
                    date = ApiDates.format(date),
                    entryType = type.apiValue,
                    title = title,
                    text = text,
                    recipeId = recipeId,
                ),
            )
        }.let { result ->
            when (result) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> result.value.toDomain()
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(NetworkError.InvalidResponse)
            }
        }
    }

    suspend fun update(entry: MealPlanEntry): ApiResult<MealPlanEntry> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val groupId = entry.groupId ?: return ApiResult.Failure(NetworkError.InvalidResponse)
        val userId = entry.userId ?: return ApiResult.Failure(NetworkError.InvalidResponse)
        return apiCall {
            api.updateMealPlan(
                id = entry.id,
                entry = UpdateMealPlanEntryDto(
                    id = entry.id,
                    date = ApiDates.format(entry.date),
                    entryType = entry.type.apiValue,
                    title = entry.title,
                    text = entry.text,
                    recipeId = entry.recipe?.id,
                    groupId = groupId,
                    userId = userId,
                ),
            )
        }.let { result ->
            when (result) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> result.value.toDomain()
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(NetworkError.InvalidResponse)
            }
        }
    }

    suspend fun delete(id: Int): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.deleteMealPlan(id) }
    }
}

fun MealPlanEntryDto.toDomain(): MealPlanEntry? {
    val day = ApiDates.parseDate(date) ?: return null
    return MealPlanEntry(
        id = id,
        date = day,
        type = MealType.fromApi(entryType),
        title = title,
        text = text,
        recipe = recipe?.toDomain(),
        groupId = groupId,
        userId = userId,
    )
}
