## MMO Econ 
#### A server-side Economy & Shop mod!
Currently supports Minecraft 1.21.1 NeoForge.

This mod is a NeoForge port of my original MMOEcon mod for Fabric, with a new and improved codebase.
It is completely server-side and does NOT need to be installed on any clients in order to function in multiplayer.

### Features:
- Manages persistent player balances server-side.
- Completely customiseable admin GUI shop.
- Player to player payments.
- Sell hand / Inventory.
- A balance Leaderboard.

### Planned Future Features
- Playtime balance rewards.
- Player / Mob kill rewards.
- Player to Player trading / Auction house.
- Player run Chestshops.
- Sell wands.

### Commands
- /bal, /money - Displays the player's balance.
- /pay <playername> <amount> - Pays money to player from your balance.
- /bal top - Displays a list of the server's richest players.
- /shop - opens the GUI shop.
- /sell hand - sells the currently held item, if its listed a valid sellable in the GUI shop.
- /sell inv - sells every item in your inventory that is listed as a valid sellable in the GUI shop.

### Customising the Shop
The admin GUI shop is dynamically populated by /config/mmoecon/MMOShop.json.
If you don't want to write a shop JSON file from scratch, an example one with be generated the first time the mod runs to get you started.

#### Example JSON Structure
```
{
  "categories": [
    {
      "name": "Building Blocks",
      "representativeItem": "minecraft:grass_block",
      "items": [
        { "id": "minecraft:stone",              "buyPrice": 3.0,   "sellPrice": 0.3  },
        { "id": "minecraft:grass_block",        "buyPrice": 1.5,   "sellPrice": 0.1  }
      ]
    },
    {
      "name": "Ores & Gems",
      "representativeItem": "minecraft:diamond",
      "items": [
        { "id": "minecraft:iron_ingot", "buyPrice": 30.0,"sellPrice": 3 },
        { "id": "minecraft:diamond",    "buyPrice": 200.0,"sellPrice": 20.0  }
      ]
    }
  ]
}

```
