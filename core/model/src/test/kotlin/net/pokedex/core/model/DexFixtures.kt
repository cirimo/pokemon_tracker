package net.pokedex.core.model

/**
 * A miniature grouped-balanced: two boxes, a hole, a duplicated variant (Unown in its
 * generation box and again in a form box), a variant with no released shiny, a shiny lock,
 * an accented name, and an evolution line. Every case the real preset has, at a size a
 * test can reason about.
 */
object DexFixtures {

    private val preset = PresetId("grouped-balanced")

    fun variant(
        id: String,
        dexNum: Int,
        name: String,
        type1: String = "normal",
        type2: String? = null,
        formName: String? = null,
        evolvesFrom: String? = null,
        evolveCondition: String? = null,
        shinyReleased: Boolean = true,
    ) = Variant(
        id = VariantId(id),
        nid = id,
        dexNum = dexNum,
        formId = null,
        displayName = name,
        formName = formName,
        type1 = type1,
        type2 = type2,
        isDefault = formName == null,
        isForm = formName != null,
        isCosmeticForm = false,
        isFemaleForm = false,
        isRegional = false,
        isBattleOnlyForm = false,
        baseSpeciesId = null,
        evolvesFromId = evolvesFrom?.let(::VariantId),
        evolveCondition = evolveCondition,
        shinyReleased = shinyReleased,
        spriteFile = "sprites/$id.webp",
    )

    fun game(id: String, set: String, order: Int) = Game(
        id = GameId(id),
        name = id,
        gameSet = set,
        generation = 9,
        releaseDate = "2022-11-18",
        region = "paldea",
        originMark = "paldea",
        supportsShiny = true,
        sortOrder = order,
    )

    fun available(variant: String, game: String, shinyLocked: Boolean = false, obtainable: Boolean = true) =
        GameAvailability(
            variantId = VariantId(variant),
            gameId = GameId(game),
            obtainable = obtainable,
            eventOnly = false,
            storable = true,
            transferOnly = false,
            shinyLocked = shinyLocked,
            shinyLockReason = if (shinyLocked) "Story-locked." else null,
        )

    val variants = listOf(
        variant("pichu", 172, "Pichu", "electric"),
        variant("pikachu", 25, "Pikachu", "electric", evolvesFrom = "pichu", evolveCondition = "high friendship"),
        variant("raichu", 26, "Raichu", "electric", evolvesFrom = "pikachu", evolveCondition = "thunder stone"),
        variant(
            id = "raichu-alola",
            dexNum = 26,
            name = "Raichu (Alolan)",
            type1 = "electric",
            type2 = "psychic",
            formName = "Alolan Form",
            evolvesFrom = "pikachu",
            evolveCondition = "thunder stone in Alola",
        ),
        variant("mew", 151, "Mew", "psychic"),
        variant("mewtwo", 150, "Mewtwo", "psychic"),
        variant("unown", 201, "Unown", "psychic"),
        variant("flabebe", 669, "Flabébé", "fairy"),
        variant("zacian", 888, "Zacian", "fairy"),
        variant("magearna", 801, "Magearna", "steel", "fairy", shinyReleased = false),
    )

    val games = listOf(
        game("swsh-sw", "swsh", 30),
        game("swsh-sh", "swsh", 40),
        game("sv-s", "sv", 80),
        game("sv-v", "sv", 90),
        game("home", "home", 110),
    )

    val availability = listOf(
        available("pikachu", "sv-s"),
        available("pikachu", "swsh-sw"),
        available("raichu", "sv-v"),
        available("unown", "sv-s", obtainable = false),
        available("flabebe", "sv-v"),
        // Zacian is obtainable in SwSh but shiny-locked there: it must NOT count as
        // "get it shiny in SwSh".
        available("zacian", "swsh-sw", shinyLocked = true),
        available("zacian", "swsh-sh", shinyLocked = true),
        available("zacian", "home"),
    )

    private fun slot(box: Int, index: Int, variant: String, copy: Int = 0) =
        Slot(preset, box, index, VariantId(variant), copy)

    val slots = listOf(
        slot(0, 0, "pichu"),
        slot(0, 1, "pikachu"),
        slot(0, 2, "raichu"),
        slot(0, 3, "raichu-alola"),
        slot(0, 4, "unown"),
        // 0:5 is a hole.
        slot(0, 6, "mewtwo"),
        slot(0, 7, "mew"),
        slot(1, 0, "unown", copy = 1),
        slot(1, 1, "flabebe"),
        slot(1, 2, "zacian"),
        slot(1, 3, "magearna"),
    )

    val boxes = listOf(
        Box(preset, 0, "Kanto 1", slotCount = 8, filledSlotCount = 7),
        Box(preset, 1, "Unown Dex", slotCount = 4, filledSlotCount = 4),
    )

    val dex: Dex = Dex.assemble(
        preset = DexPreset(preset, "Grouped", "", 1, 1, boxCount = 2, filledSlotCount = 11),
        boxes = boxes,
        slots = slots,
        variants = variants,
        species = emptyList(),
        games = games,
        availability = availability,
    )

    fun key(variant: String, copy: Int = 0) = CatchKey(VariantId(variant), copy)
}
