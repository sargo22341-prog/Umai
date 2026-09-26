package org.opensources.umai.llm.data

import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.youtube.domain.ModelRecipeBuilder
import org.opensources.umai.youtube.domain.TranscriptCue
import org.opensources.umai.youtube.domain.TranscriptSource
import org.opensources.umai.youtube.domain.YouTubeVideo

/**
 * The request the app sends to rebuild a recipe from a short video heard in
 * French: 4 min 45, an ingredient list in the description, no chapters.
 */
internal object ImportPromptSample {

    fun request(): LlmRequest {
        val builder = ModelRecipeBuilder(NoModel)
        return builder.request(video, language = "fr", transcriptChars = ModelRecipeBuilder.transcriptChars(NoModel.contextSize))
    }

    private object NoModel : LanguageModel {
        override suspend fun isReady() = false

        override val contextSize = 16_384

        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome =
            error("Only the prompt is built")
    }

    private val said = listOf(
        "Bonjour à tous, aujourd'hui je vous montre des petits pains farcis à la poêle, sans four et vraiment économiques. Dans un saladier je mets quatre cents grammes de farine, une pincée de sel, et je fais un puits au milieu.",
        "Je verse deux cent cinquante millilitres de lait tiède et quarante grammes de beurre fondu, puis je mélange avec une cuillère avant de pétrir à la main pendant cinq bonnes minutes, jusqu'à obtenir une pâte bien lisse.",
        "On couvre le saladier avec un torchon et on laisse reposer la pâte une trentaine de minutes à température ambiante. Pendant ce temps on prépare la garniture, c'est très simple, vous pouvez mettre ce que vous voulez.",
        "Moi je prends du fromage râpé et un peu de charcuterie, ici du jambon de dinde que je coupe en petits morceaux. Vous pouvez aussi mettre du poulet, du thon, des légumes, tout ce qui reste dans votre frigo.",
        "La pâte a bien reposé, je la dégaze et je la divise en huit pâtons à peu près de la même taille. Je les boule un par un en rabattant les bords vers le centre, comme ça ils seront bien réguliers.",
        "Sur le plan de travail légèrement fariné, j'étale chaque pâton avec le rouleau, pas trop fin, environ un demi-centimètre, pour que la farce ne perce pas la pâte à la cuisson.",
        "Au centre je dépose une bonne cuillère de fromage et un peu de jambon, je referme en pinçant bien les bords tout autour, puis je retourne et j'aplatis doucement avec la paume de la main.",
        "Voilà, on fait pareil avec tous les pâtons. N'hésitez pas à bien souder les bords, sinon le fromage va couler dans la poêle, et ce serait dommage.",
        "Je fais chauffer une poêle antiadhésive à feu moyen, sans matière grasse. Je dépose les petits pains et je les laisse cuire environ quatre minutes de chaque côté, avec un couvercle.",
        "Ils sont bien dorés et bien gonflés, le fromage est fondu à l'intérieur. Voilà, ils sont prêts, c'est vraiment délicieux et ça revient à cinquante centimes le petit pain. Abonnez-vous et à bientôt.",
    )

    private val video = YouTubeVideo(
        id = "y3L14JKSSYI",
        title = "Petits pains farcis à la poêle 😍 Sans four et économique seulement 50 centimes 💶",
        author = "Deli Cuisine",
        description = """
            Petits pains farcis à la poêle 😍 Sans four et économique seulement 50 centimes 💶

            Abonne toi par là ➡️ https://bit.ly/2TPE1hm sans oublier d'activer la cloche🔔

            Mes Réseaux Sociaux:
            Snapchat: https://www.snapchat.com/add/deli_cuisine
            Page Facebook: https://www.facebook.com/Deli-Cuisine
            Groupe Facebook: https://www.facebook.com/groups/577062799832857/
            Insta: https://www.instagram.com/deli__cuisine/

            Ingrédients :

            400g de farine
            250ml de lait
            40g de beurre fondu
            Sel

            Fromage râpé
            Charcuterie au choix
        """.trimIndent(),
        durationSeconds = 285,
        thumbnailUrl = null,
        chapters = emptyList(),
        transcript = said.mapIndexed { index, text -> TranscriptCue(index * 30.0, minOf(index * 30.0 + 30, 285.0), text) },
        transcriptSource = TranscriptSource.HEARD,
    )
}
