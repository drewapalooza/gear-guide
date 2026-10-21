"""Build offline bank assignments. Inputs and HTTP cache live outside the release.
Usage: python tools/build_bank_catalogue.py ../../work [--fetch]
Only explicit category membership is used; broad skill categories are not rules.
"""
import csv, hashlib, json, sys, time, urllib.parse, urllib.request
from pathlib import Path

root = Path(sys.argv[1])
fetch = '--fetch' in sys.argv
cache = root / 'wiki-bank-categories'
cache.mkdir(exist_ok=True)
rules = [
 (1, 'Teleportation items|Currency|Weight-reducing equipment'),
 (6, 'Herblore secondaries|Herbs|Potions|Seeds|Saplings|Farming tools|Compost'),
 (5, 'Fishing equipment|Food|Fish|Fruit|Meat|Pies|Pizza|Drinks|Tea|Burnt food'),
 (7, 'Pickaxes|Axes|Hunter creatures|Talismans|Tiaras|Essence pouches|Mining equipment|Woodcutting equipment|Hunter equipment|Thieving equipment|Runecraft equipment'),
 (8, 'Ores|Metal bars|Logs|Planks|Gems|Uncut gems|Bones|Remains|Ashes|Crafting materials|Construction materials|Fletching materials|Smithing equipment|Crafting equipment|Tools'),
 (9, 'Quest items|Clue scrolls|Treasure Trails items|Pets|Holiday items'),
 (4, 'Runes|Magic weapons|Magic armour'),
 (3, 'Ranged weapons|Ranged armour|Ammunition slot items'),
 (2, 'Melee weapons|Melee armour|Hybrid armour'),
]
membership = {}
for tab, names in rules:
 for category in names.split('|'):
  file = cache / (category.replace(' ', '_') + '.json')
  if not file.exists():
   if not fetch: raise SystemExit('Missing cached category: ' + category + '; use --fetch')
   members, continuation = [], {}
   while True:
    params = dict(action='query', list='categorymembers', cmtitle='Category:'+category, cmlimit=500, format='json', **continuation)
    url = 'https://oldschool.runescape.wiki/api.php?' + urllib.parse.urlencode(params)
    for attempt in range(5):
     try:
      req = urllib.request.Request(url, headers={'User-Agent':'GearGuidePersonalBankOrganizer/0.8 (offline category catalogue)'})
      with urllib.request.urlopen(req, timeout=60) as response: data=json.load(response)
      if 'error' in data: raise ValueError(data['error'])
      break
     except Exception:
      if attempt == 4: raise
      time.sleep(2 ** attempt)
    members.extend(data['query']['categorymembers'])
    continuation=data.get('continue', {})
    time.sleep(.15)
    if not continuation: break
   file.write_text(json.dumps(members, ensure_ascii=False), encoding='utf-8')
   print(category, len(members), flush=True)
  for member in json.loads(file.read_text(encoding='utf-8')):
   if member['ns']==0: membership.setdefault(member['title'], []).append((tab,category))

new = json.loads((root/'reboxed-items-complete.json').read_text(encoding='utf-8'))
old = json.loads((root/'all-item-details.json').read_text(encoding='utf-8'))
names = json.loads((root/'all-item-names.json').read_text(encoding='utf-8'))
notes = json.loads((root/'all-item-notes.json').read_text(encoding='utf-8'))
items = {**old, **new}
names = {**{k:v['name'] for k,v in items.items() if v.get('name') and v['name']!='null'}, **names}
# Exact functional exceptions. Wiki titles, never substrings; all IDs/variants
# referring to that page inherit its intended bank use.
exceptions = {
 'Hammer':(8,'Production tool'), 'Iron spit':(5,'Cooking tool'),
 'Bucket of sand':(8,'Crafting ingredient'), 'Soda ash':(8,'Crafting ingredient'),
 'Herb sack':(6,'Herb storage'), 'Seed box':(6,'Seed storage'),
 'Rune pouch':(1,'Reusable utility'), 'Divine rune pouch':(1,'Reusable utility'),
 'Coconut':(6,'Herblore and Farming supply'), 'Rune arrowtips':(8,'Unfinished ammunition'),
 'Aether catalyst':(7,'Runecraft supply'), 'Stymphike feather':(8,'Fletching ingredient'),
 'Tackle box':(5,'Fishing storage'), 'Coal bag':(8,'Ore storage'),
 'Gem bag':(8,'Gem storage'), 'Rock hammer':(2,'Slayer finisher'),
}
functional_groups = {
 1: 'Enhanced crystal teleport seed|Quetzal feed|Desert shirt|Desert robe|Falador shield 3|Lockpick|Candle lantern|Grubby key|Brimstone key|Crystal key|Loop half of key|Tooth half of key',
 2: "Strength hood|Inoculation bracelet|Sunfire fanatic cuirass|Sunfire fanatic chausses|Salve amulet(ei)|Salve amulet (e)|Ring of the gods (i)|Lightbearer|Dark totem|Dark totem base|Dark totem middle|Dark totem top|Ancient shard|Soul bearer|Ash sanctifier|Bracelet of ethereum|Prayer book|Mirror shield|Devout boots|Boots of stone|Insulated boots|Monk's robe|Monk's robe top|Bag of salt|Eye of the Duke",
 3: 'Bronze cannonball|Iron cannonball|Steel cannonball|Mithril cannonball|Adamant cannonball|Rune cannonball|Granite cannonball|Armadyl dragonhide set',
 4: 'Bones to peaches (tablet)',
 5: 'Papaya fruit',
 6: "Silklined herb sack|Vial|Vial of water|Bird nest (ring)|Bird nest (seeds)|Bird nest (empty)|Giant egg sac|Swamp tar|Amulet of chemistry|Alchemist's amulet|Alchemist's signet",
 7: 'Boots of the Eye|Robe bottoms of the Eye|Forestry kit|Celestial ring|Binding necklace|Blood essence|Dynamite|Pheasant tail feathers|Sturdy beehive parts',
 8: 'Thread|Holy elixir|Green dragonhide|Scaly blue dragonhide|Blessed bone shards|Pure essence|Rune essence|Bucket of slime|Plank sack|Teak repair kit|Diamond ring|Emerald ring|Emerald bracelet|Gold ring|Giant seaweed|Granite dust|Shadow quartz|Ice quartz|Ensouled hellhound head|Flax|Sunfire splinters|Bow string spool|Plank|Oak plank|Teak plank|Mahogany plank|Ironwood plank|Camphor plank|Bronze nails|Iron nails|Steel nails|Mithril nails|Adamantite nails|Rune nails|Bolt of cloth|Bolt of linen|Bolt of canvas|Arrow shaft|Swamp paste|Bow string|Ball of wool|Soft clay|Water orb|Fire orb|Earth orb|Air orb|Adamant arrowtips|Mithril dart tip|Adamant dart tip|Adamant bolts(unf)|Rune javelin tips',
}
for tab,titles in functional_groups.items():
 for title in titles.split('|'): exceptions[title]=(tab,'Functional bank grouping')

def classify(d):
 url=d.get('wiki_url') or ''
 title=urllib.parse.unquote(urllib.parse.urlsplit(url).path.split('/w/',1)[-1]).replace('_',' ') if '/w/' in url else d.get('wiki_name')
 if title in exceptions:
  tab,reason=exceptions[title]; return [tab,False,'Curated Wiki item use: '+title+' — '+reason]
 categories=membership.get(title, [])
 if categories:
  tab,cat=categories[0]
  return [tab,False,'OSRS Wiki category: '+cat+' ('+title+')']
 e=d.get('equipment') or {}
 w=d.get('weapon') or {}
 kind=w.get('weapon_type','')
 if e:
  if kind in ('staff','powered_staff','powered_wand'): tab=4
  elif kind in ('bow','crossbow','thrown','chinchompas'): tab=3
  elif e.get('magic_damage',0)>0: tab=4
  elif e.get('ranged_strength',0)>0: tab=3
  elif e.get('attack_magic',0)>max(0,e.get('attack_ranged',0),e.get('attack_stab',0),e.get('attack_crush',0),e.get('attack_slash',0)): tab=4
  elif e.get('attack_ranged',0)>max(0,e.get('attack_magic',0),e.get('attack_stab',0),e.get('attack_crush',0),e.get('attack_slash',0)): tab=3
  elif e.get('melee_strength',0)>0 or max(e.get('attack_stab',0),e.get('attack_crush',0),e.get('attack_slash',0))>0: tab=2
  else: return [9,True,'Unresolved item use: review assignment']
  return [tab,False,'Structured equipment type/bonuses: '+('OSRSReboxed' if str(d['id']) in new else 'OSRSBox archive')]
 return [9,True,'Unresolved item use: review assignment']

result={k:classify(items.get(k,{})) for k in names}
# Propagate exact linked item IDs, including note/placeholder IDs absent from data.
for _ in range(3):
 for k,d in items.items():
  base=d.get('linked_id_item')
  if base and str(base) in result: result[k]=result[str(base)].copy()
  elif k in result:
   for link in ('linked_id_noted','linked_id_placeholder'):
    if d.get(link): result[str(d[link])]=result[k].copy()
 for k,base in notes.items():
  if str(base) in result: result[k]=result[str(base)].copy()
out=Path(__file__).resolve().parents[1]/'src/main/resources/com/gearguide'
(out/'bank-categories.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
manifest={'sources':[
 {'url':'https://github.com/DayV-git/osrsreboxed-db','commit':'33ea73face3264264206b6c4fb8bb272982d6f0a'},
 {'url':'https://oldschool.runescape.wiki/api.php','method':'Exact page membership in selected categories; no recursive inference'},
 {'url':'https://github.com/runelite/static.runelite.net/tree/gh-pages/cache/item'},
 {'url':'https://github.com/osrsbox/osrsbox-db','use':'Legacy IDs absent from current dataset'}],
 'inputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [root/'reboxed-items-complete.json',root/'all-item-details.json',root/'all-item-names.json',root/'all-item-notes.json',*sorted(cache.glob('*.json'))]},
 'generated':'2026-09-29',
 'generatorSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
 'items':len(result),'needsReview':sum(v[1] for v in result.values())}
(out/'bank-categories-sources.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
with (root/'bank-catalogue-audit.csv').open('w',encoding='utf-8',newline='') as f:
 writer=csv.writer(f);writer.writerow(['id','name','tab','review','reason'])
 for k,v in result.items(): writer.writerow([k,names.get(k,''),*v])
print('Catalogue:',manifest['items'],'Review:',manifest['needsReview'])
