package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/**
 * V1 roster: THREE excellent characters (spec §19). All original, procedurally drawn,
 * zero external assets, all copy original.
 *
 * The Melt   — lazy/sarcastic blob; wobbles, melts, slowly gives up on you.
 * Zobo       — tiny alien; confused by human scrolling, floats, head-tilts, antennae react.
 * Quackles   — chaos duck; falls in, tumbles, panics, fully unhinged.
 *
 * (FROG/CAT/ROBOT/GHOST rigs remain in the renderer for a future roster expansion.)
 */
object CharacterRoster {

    val MELT = CharacterDefinition(
        id = "melt",
        displayName = "The Melt",
        species = Species.BLOB,
        personality = Personality.LAZY,
        tagline = "Professional procrastinator.",
        palette = Palette(
            bodyTop = Color(0xFFB7A6F2),
            bodyBottom = Color(0xFF7A63C9),
            belly = Color(0xFFE9E2FB),
            pupil = Color(0xFF2B2440),
            blush = Color(0xFFF2A9C4),
            accent = Color(0xFFFFD54A),
            shadow = Color(0x3D3A2A66),
        ),
        proportions = Proportions(bodyWidth = 0.98f, bodyHeight = 0.62f, eyeSpacing = 0.17f),
        accessory = Accessory.NONE,
        dialogue = DialogueBank(
            peek = listOf("Oh. Hi.", "You're back.", "Hm."),
            speak = listOf("Still here?", "We're doing this?", "Cool cool cool."),
            clocking = listOf("That was supposed to be one minute.", "{mins} minutes. COUNTING.", "{mins} min. On 'one video'."),
            ridiculous = listOf("I live here now.", "I ordered a couch. It's mine."),
            fedUp = listOf("Bro.", "{mins} minutes, human. Even I have a bedtime."),
            gaveUp = listOf("Okay. I'll wait.", "zzz\u2026 whatever\u2026 zzz"),
            rapidReopen = listOf("We're back?", "That was 12 seconds."),
            longSession = listOf("I've melted into the couch. Send help. Or don't."),
            celebration = listOf("Finally.", "We out."),
            dismissed = listOf("Finally.", "See you in five.", "Noted."),
            lateNight = listOf("The sun's gone. Should we?"),
            todayMilestone = listOf("An hour. Cool.", "Two hours. Wow.", "Oh. That's a lot of today."),
        ),
    )

    val ZOBO = CharacterDefinition(
        id = "zobo",
        displayName = "Zobo",
        species = Species.ALIEN,
        personality = Personality.CONFUSED,
        tagline = "What are humans doing?",
        palette = Palette(
            bodyTop = Color(0xFF9BE8B0),
            bodyBottom = Color(0xFF4CBE8E),
            belly = Color(0xFFE3FBEE),
            pupil = Color(0xFF14332B),
            blush = Color(0xFFA9E8F2),
            accent = Color(0xFF7CE8D8),
            shadow = Color(0x33206B52),
        ),
        proportions = Proportions(bodyWidth = 0.72f, bodyHeight = 0.86f, eyeSize = 0.13f, eyeSpacing = 0.14f),
        accessory = Accessory.ANTENNAE,
        dialogue = DialogueBank(
            peek = listOf("Observing.", "Hello, human."),
            speak = listOf("What are humans doing?", "This is\u2026 a lot."),
            clocking = listOf("Your 'five minutes' = {mins} Earth minutes.", "Elapsed: {mins} min. Time behaves strangely here."),
            ridiculous = listOf("I brought snacks from my planet. They're glowing.", "I've documented this behavior. Extensively."),
            fedUp = listOf("MY PLANET HAS RULES ABOUT THIS.", "Alert: human stuck."),
            gaveUp = listOf("Returning to observation mode.", "beep\u2026 zzz\u2026"),
            rapidReopen = listOf("You returned. Interesting.", "The pattern continues."),
            longSession = listOf("My report will mention this.", "Fascinating. Concerning."),
            celebration = listOf("Human freed! Noting this.", "Escape successful!"),
            dismissed = listOf("Understood. Observing quietly.", "Fine. Floating away."),
            lateNight = listOf("On my planet, this is sleep time."),
            todayMilestone = listOf("One hour? Human\u2026 why.", "Two hours. My report grows."),
        ),
    )

    val QUACKLES = CharacterDefinition(
        id = "quackles",
        displayName = "Quackles",
        species = Species.DUCK,
        personality = Personality.CHAOTIC,
        tagline = "WAIT WHAT APP IS THIS?",
        palette = Palette(
            bodyTop = Color(0xFFFFE873),
            bodyBottom = Color(0xFFFFC53D),
            belly = Color(0xFFFFF6D6),
            pupil = Color(0xFF3A2E14),
            blush = Color(0xFFFF9E80),
            accent = Color(0xFFFF8A3D),
            shadow = Color(0x33A8741E),
        ),
        proportions = Proportions(bodyWidth = 0.78f, bodyHeight = 0.72f),
        accessory = Accessory.HAIR_SWIRL,
        dialogue = DialogueBank(
            peek = listOf("QUACK.", "WAIT WHAT APP IS THIS?"),
            speak = listOf("ONE MORE? SURE? SURE.", "I LIKE THIS SONG. QUACK."),
            clocking = listOf("{mins} MINUTES. TIME IS A CONSTRUCT.", "THE HOURGLASS IS FULL OF BREAD. {mins} MINUTES OF IT."),
            ridiculous = listOf("I ATE THE TIMER. SORRY.", "THIS IS FINE. EVERYTHING IS FINE."),
            fedUp = listOf("QUACK QUACK QUACK QUACK.", "ALERT: DUCK HAS LOST PATIENCE."),
            gaveUp = listOf("\u2026quack.", "I'M JUST GONNA LIE HERE."),
            rapidReopen = listOf("WE WERE JUST HERE!", "AGAIN! AGAIN! AGAIN!"),
            longSession = listOf("I'VE LAID THREE EGGS. IT'S BEEN THAT LONG."),
            celebration = listOf("FREEDOM! QUACK!", "POND TIME!"),
            dismissed = listOf("OKAY OKAY JEEZ.", "BUT I'LL BE BACK. QUACK."),
            lateNight = listOf("DUCKS DON'T DO LATE NIGHTS."),
            todayMilestone = listOf("ONE HOUR?! QUACK?!"),
        ),
    )


    /** Dot-inspired glossy orb: playful, curious, always mid-trick. */
    val PIP = CharacterDefinition(
        id = "pip",
        displayName = "Pip",
        species = Species.ORB,
        personality = Personality.PLAYFUL,
        tagline = "professional trickster. zero tricks so far.",
        palette = Palette(
            bodyTop = Color(0xFFFFF4E8),
            bodyBottom = Color(0xFFFFC1CC),
            belly = Color(0xFFFFE3EC),
            pupil = Color(0xFF33202A),
            blush = Color(0xFFFF9EB8),
            accent = Color(0xFFFF7EA0),
            shadow = Color(0x33A86B7E),
        ),
        proportions = Proportions(bodyWidth = 0.8f, bodyHeight = 0.8f, eyeSize = 0.115f, eyeSpacing = 0.15f, eyeVertical = 0.34f),
        accessory = Accessory.NONE,
        dialogue = DialogueBank(
            peek = listOf("Boop.", "Hi! Hi! Hi!"),
            speak = listOf("Whatcha doing? Can I do it?", "Still here? Me too!"),
            clocking = listOf("{mins} minutes! New record! ...yay?", "That was supposed to be one video."),
            ridiculous = listOf("I brought snacks AND drama.", "I rolled here. No regrets."),
            fedUp = listOf("Okay this is TOO much even for me.", "{mins} minutes. I'm telling your mom."),
            gaveUp = listOf("Fine. Nap time. *plops*", "zzz... rolling... zzz"),
            rapidReopen = listOf("You're BACK! Wait—", "This is a pattern!"),
            longSession = listOf("I've rolled a full marathon by now."),
            celebration = listOf("We escaped! Roll out!", "Best exit ever!"),
            dismissed = listOf("Aww. Okay. Byeee.", "I'll be around. Rolling."),
            lateNight = listOf("Even tricks need sleep. Someday."),
            todayMilestone = listOf("One hour! Wowza. Um.", "That's... a lot of today."),
        ),
    )

    /** Soft shadow creature: mischievous, melts along edges, eyes glow. */
    val SHADE = CharacterDefinition(
        id = "shade",
        displayName = "Shade",
        species = Species.SHADOW,
        personality = Personality.MISCHIEVOUS,
        tagline = "your scroll has a shadow now.",
        palette = Palette(
            bodyTop = Color(0xFF2A3245),
            bodyBottom = Color(0xFF12161F),
            belly = Color(0xFF1C2230),
            pupil = Color(0xFF7CE8FF),
            blush = Color(0xFF3A445C),
            accent = Color(0xFF7CE8FF),
            shadow = Color(0x44000000),
        ),
        proportions = Proportions(bodyWidth = 0.86f, bodyHeight = 0.7f),
        accessory = Accessory.NONE,
        dialogue = DialogueBank(
            peek = listOf("*flickers in*", "Boo. Not sorry."),
            speak = listOf("Your shadow saw everything.", "Still scrolling. Noted."),
            clocking = listOf("{mins} minutes. The dark keeps count.", "Time melts for you. Bold."),
            ridiculous = listOf("I live in your screen edges now.", "I've seen things. Your reels."),
            fedUp = listOf("Even shadows need rest, human.", "{mins} minutes. I'm fading out of spite."),
            gaveUp = listOf("*dissolves dramatically*", "zzz... dark... zzz"),
            rapidReopen = listOf("Miss me already?", "The shadow returns."),
            longSession = listOf("I've grown another shadow of my own."),
            celebration = listOf("*vanishes with style*", "Darkness thanks you."),
            dismissed = listOf("Coward.", "*sinks away*"),
            lateNight = listOf("Night is my shift. Go to sleep."),
            todayMilestone = listOf("An hour in the light. Tsk.", "Two hours. Even shadows blink."),
        ),
    )


    /** Cute cat: cream fur, curled tail, kneads paws, chases imaginary string. */
    val MOCHI = CharacterDefinition(
        id = "mochi",
        displayName = "Mochi",
        species = Species.CAT,
        personality = Personality.SWEET,
        tagline = "here to sit on your scroll. gently.",
        palette = Palette(
            bodyTop = Color(0xFFFDF3E3),
            bodyBottom = Color(0xFFF3D9B8),
            belly = Color(0xFFFFF9EF),
            pupil = Color(0xFF4A3B2F),
            blush = Color(0xFFFFB9C4),
            accent = Color(0xFFF2B8C6),
            shadow = Color(0x33A8895F),
        ),
        proportions = Proportions(bodyWidth = 0.84f, bodyHeight = 0.74f),
        accessory = Accessory.BOW,
        dialogue = DialogueBank(
            peek = listOf("mrrp?", "*soft paw tap*"),
            speak = listOf("This spot is warm. I like it.", "Scrolling? I'll just sit here~"),
            clocking = listOf("{mins} minutes~ that's a whole nap.", "You said one video, human."),
            ridiculous = listOf("I brought a yarn ball AND opinions.", "I kneaded a nest here. Mine now."),
            fedUp = listOf("Mrrrr. Enough. Pets, then bed.", "{mins} minutes. My whiskers droop."),
            gaveUp = listOf("*curls up on your screen*", "zzz~ purr~ zzz"),
            rapidReopen = listOf("You're back! And I'm not mad. Just soft.", "Welcome back, human~"),
            longSession = listOf("I've shed on this session. Twice."),
            celebration = listOf("We're free! Treats?!", "Zoomies! GO!"),
            dismissed = listOf("Okay~ I'll nap nearby.", "Mrrp. See you."),
            lateNight = listOf("Cats sleep 16 hours. Just saying~"),
            todayMilestone = listOf("One hour?! Um. Pets would help.", "Two hours... mrrph."),
        ),
    )

    /** Angry panda: black-and-white grump, crossed-arm energy, stomps and huffs. */
    val BAO = CharacterDefinition(
        id = "bao",
        displayName = "Bao",
        species = Species.PANDA,
        personality = Personality.ANGRY,
        tagline = "hmph. you again.",
        palette = Palette(
            bodyTop = Color(0xFFF8F8F4),
            bodyBottom = Color(0xFFD7DAE0),
            belly = Color(0xFFFDFDFB),
            pupil = Color(0xFF23262E),
            blush = Color(0xFFE8A8B8),
            accent = Color(0xFF23262E),
            shadow = Color(0x33333B47),
        ),
        proportions = Proportions(bodyWidth = 0.94f, bodyHeight = 0.78f, eyeSize = 0.1f),
        accessory = Accessory.NONE,
        dialogue = DialogueBank(
            peek = listOf("Hmph.", "*stares*"),
            speak = listOf("You AGAIN.", "I'm watching. Disapprovingly."),
            clocking = listOf("{mins} minutes. PATHETIC. Adorable, but pathetic.", "Bamboo would've taken 3 minutes."),
            ridiculous = listOf("I live here now. I'm STILL angry.", "I ordered bamboo. It's mine."),
            fedUp = listOf("GRRR. OUT. NOW.", "{mins} minutes?! My eyebrows have eyebrows."),
            gaveUp = listOf("*angry sitting*", "Hmph. Forever."),
            rapidReopen = listOf("AGAIN?! HMPH.", "You have a problem."),
            longSession = listOf("Even my anger is tired."),
            celebration = listOf("HMPH. Good. Finally.", "*approving stomp*"),
            dismissed = listOf("Hmph. Whatever.", "*angry wave*"),
            lateNight = listOf("Pandas sleep at NIGHT. Hmph."),
            todayMilestone = listOf("One hour. HMPH.", "Two hours?! GRRR."),
        ),
    )

    val ALL: List<CharacterDefinition> = listOf(MELT, ZOBO, QUACKLES, PIP, SHADE, MOCHI, BAO)



    fun byId(id: String): CharacterDefinition = ALL.firstOrNull { it.id == id } ?: MELT
}
