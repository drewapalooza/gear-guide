package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.*;

import java.awt.Color;
import java.nio.file.*;
import java.util.*;

/** Read-only bank guide. Assignments are local metadata, never bank operations. */
final class BankOrganizer {
    static final String[] NAMES = {
        "Travel & utility",
        "Melee & shared combat",
        "Ranged & ammo",
        "Magic & runes",
        "Food & fishing",
        "Potions, herbs & farming",
        "Gathering & tools",
        "Production & materials",
        "Quest, clues & misc"
    };
    static final Color[] COLORS = {
        new Color(255, 207, 72),
        new Color(242, 103, 103),
        new Color(110, 210, 120),
        new Color(110, 160, 255),
        new Color(255, 156, 72),
        new Color(210, 120, 235),
        new Color(76, 211, 215),
        new Color(207, 177, 136),
        new Color(216, 220, 229)
    };

    static final class Choice {
        final int tab;
        final boolean review;
        final String reason;

        Choice(int tab, boolean review, String reason) {
            this.tab = tab;
            this.review = review;
            this.reason = reason;
        }
    }

    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private String account;
    private Map<Integer, Integer> overrides = new HashMap<>();

    BankOrganizer(Path root) {
        this.root = root;
    }

    synchronized void account(String id) throws java.io.IOException {
        if (Objects.equals(id, account)) return;
        account = null;
        overrides = new HashMap<>();
        if (id == null) return;
        if (!id.matches("[a-f0-9]{24}")) throw new IllegalArgumentException("Invalid account");
        Path file = root.resolve(id + "-bank-guide.json");
        if (Files.exists(file)) {
            try {
                JsonObject data = new JsonParser().parse(Files.readString(file)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : data.entrySet()) {
                    int item = Integer.parseInt(e.getKey()), tab = e.getValue().getAsInt();
                    if (item > 0 && tab >= 1 && tab <= 9) overrides.put(item, tab);
                }
            } catch (RuntimeException ex) {
                overrides.clear();
                throw new java.io.IOException(
                        "Cannot read bank-guide assignments; file preserved.", ex);
            }
        }
        account = id;
    }

    synchronized void assign(String expectedAccount, int item, int tab) throws java.io.IOException {
        if (account == null || !account.equals(expectedAccount))
            throw new IllegalStateException("Account changed; refresh the bank guide.");
        if (item <= 0 || tab < 0 || tab > 9)
            throw new IllegalArgumentException("Invalid assignment");
        Map<Integer, Integer> next = new HashMap<>(overrides);
        if (tab == 0) next.remove(item);
        else next.put(item, tab);
        GuideStore.atomicWrite(root.resolve(account + "-bank-guide.json"), gson.toJson(next));
        overrides = next;
    }

    synchronized Map<Integer, Integer> overrides() {
        return new HashMap<>(overrides);
    }

    private static final class Catalogue {
        static final Map<Integer, Choice> TAGS = load();

        private static Map<Integer, Choice> load() {
            Map<Integer, Choice> result = new HashMap<>();
            try (java.io.InputStream stream =
                    BankOrganizer.class.getResourceAsStream("bank-categories.json")) {
                if (stream == null) return result;
                JsonObject json =
                        new JsonParser()
                                .parse(
                                        new java.io.InputStreamReader(
                                                stream, java.nio.charset.StandardCharsets.UTF_8))
                                .getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    JsonArray row = e.getValue().getAsJsonArray();
                    int tab = row.get(0).getAsInt();
                    boolean review = row.get(1).getAsBoolean();
                    String reason =
                            row.size() > 2 ? row.get(2).getAsString() : "Legacy assignment: review";
                    if (tab >= 1 && tab <= 9)
                        result.put(
                                Integer.parseInt(e.getKey()),
                                new Choice(tab, review || row.size() < 3, reason));
                }
            } catch (java.io.IOException | RuntimeException ex) {
                result.clear();
            }
            return Collections.unmodifiableMap(result);
        }
    }

    static int catalogueSize() {
        return Catalogue.TAGS.size();
    }

    static Choice choose(OwnedItem item, Map<Integer, Integer> overrides) {
        Integer manual = overrides.get(item.id);
        if (manual != null) return new Choice(manual, false, "Your saved assignment");
        Choice known = Catalogue.TAGS.get(item.id);
        if (known != null && !known.review) return known;
        return classify(item);
    }

    private static Choice tab(int tab, String reason) {
        return new Choice(tab, false, reason);
    }

    private static final java.util.concurrent.ConcurrentMap<String, java.util.regex.Pattern>
            PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean has(String n, String regex) {
        return PATTERNS.computeIfAbsent(regex, java.util.regex.Pattern::compile).matcher(n).find();
    }

    static Choice classify(OwnedItem i) {
        Choice guess = guess(i);
        return new Choice(
                guess.tab,
                true,
                "Unverified suggestion: " + guess.reason + "; review or assign a tab");
    }

    private static Choice guess(OwnedItem i) {
        String n = i.name == null ? "" : i.name.toLowerCase(Locale.ROOT);
        if (has(
                n,
                "anti-venom|ancient brew|forgotten brew|coconut|cactus spine|lily of the"
                    + " sands|amulet of chemistry|alchemist.s|swamp tar|giant egg sac|tangled"
                    + " toad.s"))
            return tab(6, "Herblore or Farming supplies");
        if (has(
                n,
                "strength cape|strength hood|attack cape|defence cape|hitpoints cape|salve"
                    + " amulet|ring of the gods|lightbearer|holy wrench|anti-dragon shield|devout"
                    + " boots|monk.s robe|prayer book|inoculation bracelet|bracelet of"
                    + " ethereum|dark totem|ancient shard|holy elixir|eye of the duke|granite"
                    + " hammer|dragon boots|proselyte"))
            return tab(2, "Combat equipment, access or supplies");
        if (has(
                n,
                "mark of grace|warrior guild token|hallowed mark|hallowed crystal shard|quetzal"
                    + " feed|boots of lightness|circlet of water|desert shirt|desert robe|giantsoul"
                    + " amulet|pendant of ates|revitalising idol|anima portal|locator"
                    + " orb|^lyre$|grubby key|brimstone key|crystal key|loop half of key|tooth half"
                    + " of key"))
            return tab(1, "Travel, access or utility");
        if (has(
                n,
                "unidentified minerals|golden nugget|abyssal pearls|stardust|termite|pheasant tail"
                    + " feathers|sturdy beehive parts|robe bottoms of the eye|medium pouch|small"
                    + " pouch|large pouch|giant pouch|crowbar|teak repair kit"))
            return tab(7, "Gathering or skilling supplies");
        if (has(
                n,
                "binding necklace|crystal shard|bucket of slime|oyster pearl|gold ring|long"
                    + " bone|imcando hammer|^knife$|swamp paste|water orb|fire orb|earth orb|air"
                    + " orb|sunfire splinters|shadow quartz|ice quartz"))
            return tab(8, "Production or Prayer supplies");
        if (has(n, "tackle box|^beer$|dwarven stout|^onion$|iron spit|papaya fruit"))
            return tab(5, "Food, Cooking or Fishing supplies");
        if (has(
                n,
                "scroll box|purple firelighter|rusty locket|miscellaneous key|bearhead|cowbell"
                    + " amulet|tattered|suqah tooth|ring of visibility|gnome amulet|snake charm|red"
                    + " hot sauce|glarial.s pebble|ogre bellows|tegid.s soap|ring of charos|crystal"
                    + " chime|bullroarer|crystal trinket|bonesack|steel key ring|frog"
                    + " token|vorkath.s head|pug puppy|pristine spider silk"))
            return tab(9, "Quest, clue or keepsake");
        if (has(
                n,
                "granite hammer|rock hammer|rock thrownhammer|dragon warhammer|elder maul|granite"
                    + " maul"))
            return tab(2, "Combat weapon or gargoyle finisher");
        if (has(
                n,
                "clue scroll|clue bottle|clue nest|clue geode|casket|challenge scroll|mimic|master"
                    + " scroll book|ornament kit|pet | plush|toy"
                    + " |holiday|christmas|halloween|easter|ghostspeak|greegree|seal of"
                    + " passage|brittle key|frozen"
                    + " key|commorb|blisterwood|silverlight|darklight|ivandis|ivandis|sunspear|hallowfell|hallowed"
                    + " flail|rat pole|sled|fixed device|beads of the dead|mourn|vyre noble|zombie"
                    + " head|cat training|progress hat"))
            return tab(9, "Quest, clue or keepsake name");
        if (has(
                n,
                "teleport|tele tab|games necklace|ring of dueling|ring of wealth|slayer ring|skills"
                    + " necklace|combat bracelet|amulet of glory|digsite pendant|necklace of"
                    + " passage|burning amulet|xeric.s talisman|drakan.s medallion|pharaoh.s"
                    + " sceptre|enchanted lyre|cam[u]?let|ectophial|royal seed pod|coins|platinum"
                    + " token|tokkul|trading sticks|numulite|graceful|stamina potion|energy"
                    + " potion|agility potion|strange old lockpick|lockpick|rope|spade|light"
                    + " source|lantern|tinderbox|torch|candle|rune pouch|looting bag|ghommal.s"
                    + " hilt|explorer.s ring|ardougne cloak|morytania legs|karamja gloves|falador"
                    + " shield|desert amulet|wilderness sword|radas blessing|rada.s blessing|dramen"
                    + " staff|lunar staff"))
            return tab(1, "Travel, currency or general utility");
        if (has(
                n,
                "potion|super restore|saradomin brew|zamorak"
                    + " brew|antifire|antipoison|antidote|serum |sanfew|guthix"
                    + " rest|vial|grimy|guam|marrentill|tarromin|harralander|ranarr|toadflax|irit|avantoe|kwuarm|snapdragon|cadantine|lantadyme|dwarf"
                    + " weed|torstol|herb sack|seed box|seed$|seeds$|sapling|compost|plant"
                    + " pot|watering can|rake$|seed"
                    + " dibber|secateurs|gardening|tithe|farmer|herbiboar|limpwurt|snape"
                    + " grass|unicorn horn|red spiders. eggs|bird nest|crushed nest|wine of"
                    + " zamorak|white berries|goat horn|mort myre fungus|potato"
                    + " cactus|jangerberries|dragon scale|amylase|eye of newt|chocolate"
                    + " dust|pestle|unfinished|\\(unf\\)|crystal dust"))
            return tab(6, "Potions, Herblore or Farming");
        if (has(
                n,
                "shark|karambwan|anglerfish|manta"
                    + " ray|tuna|lobster|swordfish|monkfish|salmon|trout|sardine|herring|anchovies|shrimp|bass$|sea"
                    + " turtle|dark crab|cooked|raw |bread|cake|pizza|pie$|pie\\(|potato|jug of"
                    + " wine|jug of water|jug$|cheese|butter|chocolate"
                    + " bar|sweet|biscuit|peach|banana|orange$|apple$|lemon$|pineapple|grapes|kebab|stew|curry|meat|fish|harpoon|lobster"
                    + " pot|bait|cooking gauntlets|cooking cape|chef.s|griddle|spatula"))
            return tab(5, "Food, Cooking or Fishing");
        if (has(
                n,
                "rune$|runes$|staff|staves|wand|sceptre|scepter|trident|tome of|book of"
                    + " darkness|ancient"
                    + " book|ahrim|dagon.hai|ancestral|virtus|infinity|mystic|wizard|mage|magic"
                    + " cape|occult|magus|eternal boots|tumeken|elidinis|confliction|imbued"
                    + " .*cape|lunar"
                    + " (helm|torso|legs|gloves|boots|ring|amulet)|splitbark|swampbark|bloodbark"))
            return tab(4, "Magic equipment or runes");
        if (has(
                n,
                "arrow|bolt|dart|javelin|ballista|blowpipe|bow|atlatl|ranged cape|range"
                    + " cape|masori|armadyl|karil|crystal (body|legs|helm)|d.hide|dragonhide"
                    + " (body|chaps|vambraces|shield)|ava.s|anguish|archers ring|venator"
                    + " ring|pegasian|ranger|buckler|coif|leather"
                    + " (body|chaps|vambraces)|studded|eclipse moon|antler"))
            return tab(3, "Ranged gear, ammunition or ammo components");
        if (has(
                n,
                "pickaxe|prospector|mining|woodcut|lumberjack|forestry|felling|log basket|infernal"
                    + " axe|dragon axe|rune axe|adamant axe|steel axe|mithril axe|black axe|bronze"
                    + " axe|iron axe|hunter.s kit|hunter cape|bird snare|box"
                    + " trap|butterfly|impling|noose wand|teasing stick|sickle|essence"
                    + " pouch|abyssal lantern|needle|boots of the eye|robes of the eye|hat of the"
                    + " eye|celestial ring|gem bag|coal bag|thieving|rogue"
                    + " (mask|top|trousers|gloves|boots)|sailing|captain|sailor|chinchompa"))
            return tab(7, "Gathering tools or skilling outfit");
        if (has(
                n,
                " logs?$|^logs$|plank| ore$|^coal$|"
                    + " bar$|gem|uncut|sapphire|emerald|ruby|diamond|dragonstone|onyx|zenyte|dragonhide$|leather$|hide$|battlestaff$|flax|bow"
                    + " string|wool|thread|nails|molten glass|glassblowing|sand$|soda"
                    + " ash|seaweed|clay|limestone|marble|gold leaf|bolt of cloth|steel"
                    + " frame|bronze wire|mould|chisel|hammer$|amy.s"
                    + " saw|saw$|goldsmith|smith|fletching|carpenter|construction|crafting|essence$|talisman|tiara|ashes$|bones$|bone"
                    + " shards|ensouled|granite dust|saltpetre|sulphur|dynamite"))
            return tab(8, "Production materials, tools or Prayer supplies");
        if (has(
                n,
                "rancour|ultor|bellator|avernic|torva|oathplate|inquisitor|justiciar|bandos|dharok|guthan|torag|verac|obsidian|berserker|warrior"
                    + " ring|tyrannical|treasonous|saradomin"
                    + " sword|godsword|halberd|maul|scimitar|longsword|battleaxe|warhammer|greataxe|defender|platebody|platelegs|plateskirt|chainbody|kiteshield|sq"
                    + " shield|full helm|med helm|heraldic helm|spiked manacles|fremennik"
                    + " kilt|obby|regen bracelet|combat bracelet"))
            return tab(2, "Melee equipment family");
        if (has(
                n,
                "hueycoatl"
                    + " hide|zaryte|dizana|quiver|tonalztics|throwing|chinchompa|ranging|ranged|void"
                    + " ranger|green d.hide|red d.hide|blue d.hide|black d.hide"))
            return tab(3, "Ranged equipment family");
        if (has(
                n,
                "ahrim|eternal|eldritch|harmonised|volatile|blue moon|dusk"
                    + " mystic|bloodbark|swampbark|dagon.hai|kodai|malediction|unholy book|ancient"
                    + " wyvern shield|arcane|seer.s|seers ring|tormented bracelet|void mage"))
            return tab(4, "Magic equipment family");
        if (has(
                n,
                "cabbage|cup|bowl|premade|batta|crunchies|worm|sauce|cider|ale$|ale\\(|stout|beer|rum$|vodka|gin$|brandy|cocktail|pineapple|toad.s"
                    + " legs|frog"
                    + " spawn|rocktail|yellowfin|haddock|halibut|mackerel|bream|mullet|catfish|salve"
                    + " eel|eel$|soup|leaves$|tea$|tea\\(|tomato|strawberr|sweetcorn|redberries|dwellberries|chilli|cooking"
                    + " apple|choc|flour|dough|milk|egg$|eggs$|caviar|burnt |cooked"
                    + " |anchovy|squid|jubbly|chompy$"))
            return tab(5, "Food, drink or Cooking supplies");
        if (has(
                n,
                "seedling|seed pod|watering|plant cure|gardening|plant pot|empty"
                    + " sack|basket|supercompost|ultracompost|fruit tree|tree seed|sapling|tortoise"
                    + " shell|red spider|toad.s legs|mixture"
                    + " -|serum|leprechaun|gricoller|herblore|pristine moonlight|cockatrice"
                    + " egg|dragon scale|berries|mix\\(|mix "))
            return tab(6, "Farming or Herblore supplies");
        if (has(
                n,
                "plank|mahogany|teak |oak |rosewood|steel"
                    + " frame|keel|mast|sails|hull|rigging|sailcloth|cloth|canvas|repair kit|ship"
                    + " parts|coffin|thatch|magic stone|marble|limestone|stone block|statuette|gold"
                    + " bracelet|gold necklace|opal ring|opal necklace|topaz|jade ring|silver"
                    + " bar|gold bar|silver ore|gold ore|wooden"
                    + " stock|unstrung|unpowered|clockwork|cog|furniture|bench|table|bookcase|wardrobe|larder|dungeon|hanging"
                    + " skeleton|fireplace|furnace|sawmill|whetstone|banner"
                    + " stand|mounted|fence|hedge|portal frame|garden|curtains|rug$|bolts"
                    + " of|brazier"))
            return tab(8, "Production material, furniture or ship parts");
        if (has(
                n,
                "broken axe|broken pickaxe|mining|prospector|lumberjack|forestry|beaver|rock"
                    + " golem|heron|rift guardian|baby chinchompa|fishing"
                    + " pet|tangleroot|rocky$|giant squirrel|rift"
                    + " guardian|runecraft|hunter|trapper|jerboa|graahk|kyatt|larupia|desert"
                    + " devil|sabre-toothed|spotted kebbit|polar kebbit|feldip weasel|long"
                    + " kebbit|razor-backed|needle|thread|ice"
                    + " gloves|salvage|compass|sextant|nautical|spyglass"))
            return tab(7, "Gathering, skilling or Sailing supplies");
        if (has(
                n,
                "coin pouch|numulite|token|emblem|mark of|trading sticks|trader|teleport|charged"
                    + " orb|amulet of the eye"))
            return tab(1, "Currency, travel or access");
        if (has(
                n,
                "instruction|manual|ancient page|desiccated page|scroll|puzzle box|board game|"
                    + " icon|lamp|experience|book|diary|journal|letter|note$|document|map$|hat$|snelm|robe"
                    + " top|robe"
                    + " bottom|villager|camo|decorative|musketeer|tuxedo|bob.s|shirt|skirt|trousers|tricorn|pirate|naval|shoe|slipper|jumper|coat|flower|crown|holiday|easter|christmas|santa|halloween|pumpkin|h.a.m.|jar"
                    + " of|pet|puppy|kitten|cat$|pug|trophy|relic|sigil|time bubble|pocket"
                    + " kingdom|demon tear|reanimation|reward bag|collection|perfect shell"))
            return tab(9, "Quest, clue, cosmetic, collection or special-mode item");
        if (i.equipmentSlot != null && i.stats != null && i.stats.has("equipment")) {
            JsonObject e = i.stats.getAsJsonObject("equipment");
            int melee = Math.max(stat(e, "astab"), Math.max(stat(e, "aslash"), stat(e, "acrush")));
            if (stat(e, "rstr") > 0 || stat(e, "arange") > Math.max(melee, stat(e, "amagic")))
                return tab(3, "Ranged equipment bonuses");
            if (stat(e, "mdmg") > 0 || stat(e, "amagic") > Math.max(melee, stat(e, "arange")))
                return tab(4, "Magic equipment bonuses");
            if (stat(e, "str") > 0
                    || melee > 0
                    || stat(e, "dstab") > 15
                    || stat(e, "dslash") > 15
                    || stat(e, "dcrush") > 15)
                return tab(2, "Melee or shared combat equipment bonuses");
        }
        if (has(
                n,
                "slayer|rock hammer|rock thrownhammer|boots of stone|insulated boots|earmuffs|nose"
                    + " peg|face mask|bag of salt|ice cooler|fungicide|antifreeze|mirror"
                    + " shield|holy symbol|unholy symbol|god book|book of law|book of war|book of"
                    + " balance|blessing|cannon|cannonball|ash sanctifier|bonecrusher|soul bearer"))
            return tab(2, "Shared combat or Slayer utility");
        return new Choice(
                9, true, "Uncertain: review and assign a tab; not identified as a quest item");
    }

    private static int stat(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsInt() : 0;
    }
}
