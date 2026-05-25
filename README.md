## MMO Econ 
#### A server-side Player Economy & GUI Shop mod!

### Download
[![Static Badge](https://img.shields.io/badge/GitHub%20Release-Latest-blue?style=for-the-badge&logo=github&logoSize=auto)](https://github.com/Casp3rNZ/MMOEcon-NeoForge/releases/latest)
[![Static Badge](https://img.shields.io/badge/CurseForge-Mod%20Page-orange?style=for-the-badge&logo=curseforge&logoSize=auto)](#)

<img width="1280" height="760" alt="2026-05-25_13 36 10" src="https://github.com/user-attachments/assets/9052ee4c-bc6f-41f2-9069-41c98b653835" />
<img width="1280" height="760" alt="2026-05-25_13 36 24" src="https://github.com/user-attachments/assets/429baea7-4c26-403c-8405-bada90beff46" />
Currently supports only Minecraft 1.21.1 NeoForge.
This mod is a NeoForge port of my original MMOEcon mod for Fabric, with a new and improved codebase.
It is completely server-side and does NOT need to be installed on any clients in order to function in multiplayer.

### Features:
- [x] Manages persistent player balances server-side.
- [x] Completely customiseable admin GUI shop.
- [x] Player to player payments.
- [x] Sell hand / Inventory.
- [x] A balance Leaderboard.
- [x] Sell wands.

### Planned Features
- [ ] Playtime balance rewards.
- [ ] Player / Mob kill rewards.
- [ ] Player to Player trading / Auction house.
- [ ] Player run Chestshops.

### Commands
- /bal, /money - Displays the player's balance.
- /pay <playername> <amount> - Pays money to player from your balance.
- /bal top - Displays a list of the server's richest players.
- /shop - opens the GUI shop.
- /sell hand - sells the currently held item, if its listed a valid sellable in the GUI shop.
- /sell inv - sells every item in your inventory that is listed as a valid sellable in the GUI shop.
- /sellwand give - (OP ONLY) gives the player a sell wand.

### Customising the Shop
The admin GUI shop is dynamically populated by /config/mmoecon/MMOShop.json.
If you don't want to write a shop JSON file from scratch, an example one with be generated the first time the mod runs to get you started.

#### Example MMOShop.JSON Structure
The shop will support any modded items supported by the server its running on.
```
{
  "categories": [
    {
      "name": "Building Blocks",
      "representativeItem": "minecraft:grass_block",
      "items": [
        { "id": "minecraft:stone", "buyPrice": 3.0, "sellPrice": 0.3  },
        { "id": "minecraft:grass_block", "buyPrice": 1.5, "sellPrice": 0.1  }
      ]
    },
    {
      "name": "Modded Items",
      "representativeItem": "create:cogwheel",
      "items": [
        { "id": "create:cogwheel", "buyPrice": 10.0 }
      ]
    }
    {
      "name": "Custom Server-side Items",
      "representativeItem": "minecraft:emerald",
      "items": [
        { "id": "mmoecon:sell_wand", "special":"sell_wand", "buyPrice": 35000.0 }
      ]
    }
  ]
}

```
The Sell Wand is currently the only supported "special" item.
