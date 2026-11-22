# Potion Prep

Pick a potion and your bank shows only what you need to make it, laid out in the order you use it. The side panel shows how many you can make and what to do next.

Switch to **Herb run** and it plans and guides your herb runs: which patches are ready, the best teleport you own for each, what to withdraw, and a step-by-step guide with a walking path to every patch.

<p>
  <img src="docs/panel.png" alt="Potion Prep side panel" width="241">
  <img src="docs/bank.png" alt="Bank filtered to stamina potion ingredients" width="456">
</p>

## Using it

1. Open the **Potion Prep** panel (green flask icon) and click a potion. Search narrows the grid; Enter picks the first match.
2. Turn on **Show in bank**. While it's on and the Potion Prep panel is open, your bank shows just that potion's ingredients. Close the panel, click any other bank tab, or turn the toggle off to get your normal bank back.

Each grid tile shows how many of that potion you can make from your bank and inventory. Red **Lv** means your Herblore level is too low. An orange count means you can make it with a botanical pie (+4 Herblore). The pie then gets its own row at the top of the bank view, and the first step tells you to eat it. Hover a tile to see the Herblore XP for everything you can make, including cleaning herbs and any potions made along the way.

Herblore gear you own (prescription goggles, the alchemist outfit, amulet of chemistry or alchemist's amulet, Herblore cape) is laid out on the left of the bank view like the equipment tab. Gear that's worn, in your inventory, or left as a bank placeholder shows faded. To use your own outfit instead, wear it and click **Save worn gear**. Each mode keeps its own.

## Bank layout

One row per task, in the order you do them. Each row holds what the task needs, but not what it makes:

| Row | Superantipoison |
| --- | --- |
| Clean | grimy irit |
| Make unf | irit, vial of water |
| Crush | unicorn horn, pestle and mortar |
| Mix | irit potion (unf), unicorn horn dust |

Potions used as an ingredient, like super energy for a stamina potion, show all four doses, (1) to (4). Items you don't have show faded. The finished potion isn't shown.

## Panel

- **Count:** how many you can make, and how many need prep first (cleaning, unf, crushing).
- **Ingredients:** what you have, from bank and inventory. Potions are counted in doses.
- **To do:** the prep steps in order, then anything you're short of. Hover a shortage to see where to get it.

## Potions

Attack, Antipoison, Strength, Restore, Energy, Defence, Agility, Combat, Prayer, Super attack, Superantipoison, Fishing, Super energy, Hunter, Super strength, Weapon poison, Super restore, Super defence, Antifire, Ranging, Magic, Stamina, Zamorak brew, Antidote++, Saradomin brew, Extended antifire, Anti-venom, Super combat, Super antifire, Anti-venom+, Extended super antifire.

## Herb run

Click **Herb run** at the top of the panel.

- **Seeds:** pick a herb, and optionally limpwurt for flower patches and snape grass for allotments. Each dropdown shows how many seeds you have.
- **Patches:** all ten herb patches. Ones you can't reach yet (quests, the Morytania elite diary, 65 Farming for the guild) are skipped and say why. With RuneLite's **Time Tracking** plugin on, each patch shows Ready, Diseased, Dead, Empty or how long is left, and patches still growing are left out of the run.
- **Teleports:** each patch picks the best one you can use: unlimited items first, then house portals, fairy rings and spells, then tablets. Click it to choose another. Spells check your spellbook, Magic level and runes for the whole run, counting the rune pouch, combination runes, staves and tomes. Tablets are budgeted the same way.
- **Supplies:** have / need for seeds, compost, tools and runes. The tool leprechaun's storage is read from the game, so tools, compost and a bottomless bucket kept there aren't asked for from the bank. The bucket's uses are tracked from the leprechaun, Check and each use, and the rake only appears if a patch needs raking (none at all with Tithe Farm's Auto-weed on).
- **Harvest:** what you can expect from the run, and what it adds to your potions. Herbs use your Farming level, compost, magic secateurs, Farming cape, diaries and disease risk. Snape grass uses its own chance to save, compost and secateurs. Limpwurt is 3 roots plus a bonus that grows with Farming level. The wiki doesn't know disease rates for allotments and flowers, so those assume the crop survives.

**Show in bank** lays out the run: tools and compost, seeds, runes, then teleports in run order, with your saved gear on the left. Each item shows how many you still need to withdraw. The count goes down as you take things, including runes in a rune pouch, and disappears when you have enough.

**Start run** guides you patch by patch. The panel lists each stop with its teleport and the option to pick, then a short status for each plot: Pick, Clear, Cure, Rake, Plant + compost, Plant, Compost or Done. Compost and seed can go on in either order, and compost at the leprechaun counts. A walking path is drawn on the ground and minimap, the game's farming icon marks the patch on the minimap and world map, and the patch is outlined when you arrive. Plots tick off as the game confirms planting and composting, and the run moves on once you leave a planted patch. If you run out of compost or seeds, the run carries on without them. The run stops itself after the last patch. With the Shortest Path plugin installed, you also get its route to each patch.

## Settings

- **Bank column:** which column the rows line up to. 8 is the right-most.
- **Skip growing patches:** leave patches that are still growing out of the herb run (on by default).
- **Herb run travel:** things the plugin can't see from the game: house portals (including the portal nexus), a mounted Xeric's talisman and spirit trees you've grown. Portal chamber portals are also picked up when you visit your own house.

## Notes

- Needs the core **Bank Tags** plugin, which is on by default.
- The layout only changes how the bank is drawn. Nothing is moved, clicked or withdrawn for you.
- Potions and unf potions in potion storage are counted each time you open the bank. If you haven't unlocked potion storage, only your bank and inventory are counted.
- Crushing a lava scale gives 3 to 6 shards. Counts assume 3.
- Quetzal whistle and Xeric's talisman charges come from their chat messages. Check them once so the plugin knows.
- The harvest estimate uses the OSRS Wiki's chance-to-save formula; it's an average, not a promise.

## Support

Found a bug or a wrong recipe? [Open an issue](https://github.com/EAS-Andrew/runelite-potion-prep/issues).

## Development

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew test        # planner unit tests
./gradlew runClient   # RuneLite with the plugin loaded, in developer mode
```
