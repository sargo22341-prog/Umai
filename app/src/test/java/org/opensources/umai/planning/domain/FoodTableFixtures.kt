package org.opensources.umai.planning.domain

import java.io.File

/** A few foods of the Ciqual table, with their real values, and the files shipped with the app. */
object FoodTableFixtures {

    val apple = ReferenceFood(
        code = 13039,
        nameFr = "Pomme, chair et peau, crue",
        nameEn = "Apple, flesh and skin, raw",
        unit = FoodUnit.GRAM,
        per100 = NutritionFacts(mapOf(Nutrient.ENERGY to 54.0, Nutrient.CARBOHYDRATES to 11.6, Nutrient.SUGARS to 9.3)),
    )

    val coffee = ReferenceFood(
        code = 18004,
        nameFr = "Café, non instantané, sans sucres ajoutés, prêt à boire",
        nameEn = "Coffee, non instant no added sugars, ready-to-drink",
        unit = FoodUnit.MILLILITRE,
        per100 = NutritionFacts(mapOf(Nutrient.ENERGY to 5.78, Nutrient.CARBOHYDRATES to 0.6, Nutrient.SUGARS to 0.0)),
    )

    val smokedSalmon = ReferenceFood(
        code = 25993,
        nameFr = "Saumon fumé, à l'aneth et au citron",
        nameEn = "Salmon, smoked, marinated with dill and lemon",
        unit = FoodUnit.GRAM,
        per100 = NutritionFacts(mapOf(Nutrient.ENERGY to 201.0)),
    )

    val table = FoodTable(
        foods = listOf(apple, coffee, smokedSalmon),
        usualFoods = listOf(
            UsualFood(apple, listOf("pomme"), listOf("apple"), serving = 150.0, unit = FoodUnit.GRAM),
            UsualFood(
                coffee,
                listOf("café", "café sans sucre"),
                listOf("coffee"),
                serving = 150.0,
                unit = FoodUnit.MILLILITRE,
            ),
        ),
    )

    /** The tables shipped in the app's assets; unit tests run in the module's directory. */
    fun shippedTable(): FoodTable =
        FoodTable.read(asset("ciqual.tsv").readLines(), asset("basic_foods.tsv").readLines())

    fun asset(name: String): File = File("src/main/assets", name)
}
