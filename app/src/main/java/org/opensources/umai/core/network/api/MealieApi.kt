package org.opensources.umai.core.network.api

import org.opensources.umai.core.network.dto.AppInfoDto
import org.opensources.umai.core.network.dto.CreateMealPlanEntryDto
import org.opensources.umai.core.network.dto.HouseholdPreferencesDto
import org.opensources.umai.core.network.dto.IngredientFoodListDto
import org.opensources.umai.core.network.dto.LabelDto
import org.opensources.umai.core.network.dto.MealPlanEntryDto
import org.opensources.umai.core.network.dto.PaginationDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeSummaryDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.RecipeToolDto
import org.opensources.umai.core.network.dto.ShoppingListAddRecipeDto
import org.opensources.umai.core.network.dto.ShoppingListCreateDto
import org.opensources.umai.core.network.dto.ShoppingListDto
import org.opensources.umai.core.network.dto.ShoppingListItemCreateDto
import org.opensources.umai.core.network.dto.ShoppingListItemDto
import org.opensources.umai.core.network.dto.ShoppingListItemUpdateDto
import org.opensources.umai.core.network.dto.ShoppingListItemsCollectionDto
import org.opensources.umai.core.network.dto.ShoppingListSummaryDto
import org.opensources.umai.core.network.dto.TokenResponseDto
import org.opensources.umai.core.network.dto.UpdateMealPlanEntryDto
import org.opensources.umai.core.network.dto.UserDto
import org.opensources.umai.core.network.dto.UserRatingsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Every endpoint below is taken verbatim from the Mealie OpenAPI document in
 * `.context/Mealie/openapi.json`; nothing here is guessed.
 */
interface MealieApi {

    // ---- App / auth -------------------------------------------------------

    @GET("api/app/about")
    suspend fun appInfo(): AppInfoDto

    @FormUrlEncoded
    @POST("api/auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("remember_me") rememberMe: Boolean = true,
    ): TokenResponseDto

    @POST("api/auth/refresh")
    suspend fun refreshToken(): TokenResponseDto

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/users/self")
    suspend fun currentUser(): UserDto

    @GET("api/households/preferences")
    suspend fun householdPreferences(): HouseholdPreferencesDto

    // ---- Recipes ----------------------------------------------------------

    @GET("api/recipes")
    suspend fun recipes(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 24,
        @Query("search") search: String? = null,
        @Query("categories") categories: List<String>? = null,
        @Query("tags") tags: List<String>? = null,
        @Query("tools") tools: List<String>? = null,
        @Query("foods") foods: List<String>? = null,
        @Query("households") households: List<String>? = null,
        @Query("cookbook") cookbook: String? = null,
        @Query("requireAllCategories") requireAllCategories: Boolean? = null,
        @Query("requireAllTags") requireAllTags: Boolean? = null,
        @Query("requireAllTools") requireAllTools: Boolean? = null,
        @Query("requireAllFoods") requireAllFoods: Boolean? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("orderDirection") orderDirection: String? = null,
        @Query("queryFilter") queryFilter: String? = null,
        // Mealie requires a stable seed when `orderBy=random`, so paging through
        // a shuffled list keeps returning a consistent order.
        @Query("paginationSeed") paginationSeed: String? = null,
    ): PaginationDto<RecipeSummaryDto>

    @GET("api/recipes/{slug}")
    suspend fun recipe(@Path("slug") slug: String): RecipeDetailDto

    // ---- Organizers (filter sources) --------------------------------------

    @GET("api/organizers/categories")
    suspend fun categories(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 100,
        @Query("search") search: String? = null,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<RecipeCategoryDto>

    @GET("api/organizers/tags")
    suspend fun tags(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 100,
        @Query("search") search: String? = null,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<RecipeTagDto>

    @GET("api/organizers/tools")
    suspend fun tools(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 100,
        @Query("search") search: String? = null,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<RecipeToolDto>

    @GET("api/foods")
    suspend fun foods(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 50,
        @Query("search") search: String? = null,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<IngredientFoodListDto>

    // ---- Ratings and favourites -------------------------------------------

    @GET("api/users/self/favorites")
    suspend fun favorites(): UserRatingsDto

    @GET("api/users/self/ratings")
    suspend fun ratings(): UserRatingsDto

    @POST("api/users/{id}/favorites/{slug}")
    suspend fun addFavorite(@Path("id") userId: String, @Path("slug") slug: String)

    @DELETE("api/users/{id}/favorites/{slug}")
    suspend fun removeFavorite(@Path("id") userId: String, @Path("slug") slug: String)

    // ---- Meal plan --------------------------------------------------------

    @GET("api/households/mealplans")
    suspend fun mealPlans(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 200,
        @Query("orderBy") orderBy: String? = "date",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<MealPlanEntryDto>

    @POST("api/households/mealplans")
    suspend fun createMealPlan(@Body entry: CreateMealPlanEntryDto): MealPlanEntryDto

    @PUT("api/households/mealplans/{id}")
    suspend fun updateMealPlan(
        @Path("id") id: Int,
        @Body entry: UpdateMealPlanEntryDto,
    ): MealPlanEntryDto

    @DELETE("api/households/mealplans/{id}")
    suspend fun deleteMealPlan(@Path("id") id: Int)

    // ---- Shopping ---------------------------------------------------------

    @GET("api/households/shopping/lists")
    suspend fun shoppingLists(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 50,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<ShoppingListSummaryDto>

    @GET("api/households/shopping/lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto

    @POST("api/households/shopping/lists")
    suspend fun createShoppingList(@Body body: ShoppingListCreateDto): ShoppingListDto

    @DELETE("api/households/shopping/lists/{id}")
    suspend fun deleteShoppingList(@Path("id") id: String)

    @POST("api/households/shopping/items")
    suspend fun createShoppingItem(@Body body: ShoppingListItemCreateDto): ShoppingListItemDto

    @PUT("api/households/shopping/items/{id}")
    suspend fun updateShoppingItem(
        @Path("id") id: String,
        @Body body: ShoppingListItemUpdateDto,
    ): ShoppingListItemsCollectionDto

    @DELETE("api/households/shopping/items/{id}")
    suspend fun deleteShoppingItem(@Path("id") id: String)

    @POST("api/households/shopping/lists/{id}/recipe/{recipeId}")
    suspend fun addRecipeToShoppingList(
        @Path("id") listId: String,
        @Path("recipeId") recipeId: String,
        @Body body: ShoppingListAddRecipeDto,
    ): ShoppingListDto

    @GET("api/groups/labels")
    suspend fun labels(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 100,
        @Query("orderBy") orderBy: String? = "name",
        @Query("orderDirection") orderDirection: String? = "asc",
    ): PaginationDto<LabelDto>
}
