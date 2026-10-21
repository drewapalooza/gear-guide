Task reading follows RuneLite SlayerPlugin (https://github.com/runelite/runelite).

/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

Equipment requirement baseline: OSRSBox items-skill-requirements.json, revision dae12e3400add0c71465f07a334f9d8f86bebce8.
https://github.com/osrsbox/osrsbox-db/blob/dae12e3400add0c71465f07a334f9d8f86bebce8/data/items/items-skill-requirements.json
Data attribution: OSRSBox and OSRS Wiki contributors; OSRS content belongs to Jagex. Wiki-derived data is provided under CC BY-NC-SA 3.0: https://creativecommons.org/licenses/by-nc-sa/3.0/ . The distributed requirements data includes corrections/newer item entries in GearRequirements.java and retains this attribution. No OSRSBox program code is included.
Upgrade references: https://oldschool.runescape.wiki/w/Duke_Sucellus/Strategies ; https://oldschool.runescape.wiki/w/Scythe_of_vitur ; https://oldschool.runescape.wiki/w/Emberlight ; https://oldschool.runescape.wiki/w/Sunfire_fanatic_armour .

Bank catalogue categories (0.7.0): derived from RuneLite's public cache item names/notes and OSRSBox's items-complete.json, with Gear Guide's category rules and review flags. Sources and hashes are distributed in bank-categories-sources.json. Attribution: RuneLite, OSRSBox, OSRS Wiki contributors and Jagex. Wiki-derived category data retains the CC BY-NC-SA 3.0 terms above. No source icons or third-party program code are bundled by this feature.

Bank catalogue 0.8.0 adds OSRSReboxed contributors (https://github.com/DayV-git/osrsreboxed-db, revision 33ea73face3264264206b6c4fb8bb272982d6f0a) and OSRS Wiki category membership (https://oldschool.runescape.wiki/api.php, retrieved 2026-09-29). The derived category dataset, including functional corrections, retains CC BY-NC-SA 3.0 attribution/share-alike terms. Source and input hashes accompany the dataset; generator and mapping rules are included in tools/build_bank_catalogue.py. This feature includes data only, not third-party generator code.

