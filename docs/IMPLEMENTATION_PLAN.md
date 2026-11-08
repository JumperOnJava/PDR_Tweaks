# PDR Tweaks — Implementation Plan

Based on research of the Create mod (6.0.x, MC 1.21.1, NeoForge 21.1.x) and ProjectPDR (1.1.0) APIs.

---

## Feature 1: Smart Gearshift

### Requirements (from README)
- A kinetic block that changes rotation direction based on redstone link signals
- Controlled by Create redstone links / controllers
- Three modes: **sum** (default), **latest**, **power-reverse**
- Right-click opens a UI with two redstone link frequency slots (forward + reverse) + a swap button
- Model/texture similar to Create's gearshift (weird hue to differentiate; keep original texture nearby)
- Added to Create's creative tab

### Architecture

**Class hierarchy** (mirrors Create's gearshift):
```
AbstractEncasedShaftBlock (Create)  →  RotatedPillarKineticBlock → KineticBlock → Block
SmartGearshiftBlock extends AbstractEncasedShaftBlock implements IBE<SmartGearshiftBlockEntity>
```

**Files to create:**

| File | Purpose |
|------|---------|
| `content/kinetics/SmartGearshiftBlock.java` | Block class. Extends `AbstractEncasedShaftBlock`, implements `IBE<SmartGearshiftBlockEntity>`. Adds `POWERED` state property. On right-click, opens UI. |
| `content/kinetics/SmartGearshiftBlockEntity.java` | Extends `SplitShaftBlockEntity` (or `DirectionalShaftHalvesBlockEntity`). Overrides `getRotationSpeedModifier(Direction face)` based on mode + received signals. Contains two `LinkBehaviour` instances (receiver mode) for forward/reverse frequencies. |
| `content/kinetics/SmartGearshiftMode.java` | Enum: `SUM`, `LATEST`, `POWER_REVERSE` |
| `content/kinetics/SmartGearshiftMenu.java` | `AbstractContainerMenu` for the UI — two ghost item slots (frequency items) + mode selector + swap button. |
| `content/kinetics/SmartGearshiftScreen.java` | Client screen extending Create's `AbstractSimiContainerScreen` or vanilla `AbstractContainerScreen`. |
| `content/kinetics/SmartGearshiftConfigPacket.java` | Network packet to sync frequency/mode changes between client and server. |
| `registry/PDRBlocks.java` | DeferredRegister for blocks — registers `smart_gearshift` |
| `registry/PDRBlockEntities.java` | DeferredRegister for block entity types — registers `smart_gearshift` BE |
| `registry/PDRMenus.java` | DeferredRegister for menu types |
| `registry/PDRCreativeTab.java` | Adds smart gearshift to Create's `base` creative tab via `BuildCreativeModeTabContentsEvent` |

### Key implementation details

**Block entity — rotation logic:**
```java
// SmartGearshiftBlockEntity extends SplitShaftBlockEntity
// Has two LinkBehaviour receivers (forward, reverse) + a mode field

@Override
public float getRotationSpeedModifier(Direction face) {
    if (!hasSource() || face == getSourceFacing()) return 1;
    
    boolean forward = forwardSignal > 0;
    boolean reverse = reverseSignal > 0;
    
    return switch (mode) {
        case SUM -> (forward && !reverse) ? 1 : (reverse && !forward) ? -1 : 0; // both = stop
        case LATEST -> lastActive == FORWARD ? 1 : lastActive == REVERSE ? -1 : 0;
        case POWER_REVERSE -> forward ? (reverse ? -1 : 1) : 0; // spin if forward on; reverse overrides
    };
}
```

Wait — re-reading the README modes:
- **sum mode**: spins forward/reverse based on inputs, doesn't spin if both active → `forward && !reverse → 1`, `reverse && !forward → -1`, both or neither → `1` (no change? or 0?). "doesn't spin if both" likely means no reversal (stays forward = modifier 1) or stops (0). Based on gearshift behavior, "doesn't spin" means no direction change → return 1 (pass-through). Actually re-reading: "spins forward/reverse based on inputs, doesn't spin if both inputs active" — both active = no modification = return 1.
- **latest mode**: whichever input was activated last determines direction.
- **power-reverse mode**: "spins if spin input is on (renames forward in ui); reverses if reverse input is passed" → forward on = spin normally (1), reverse on = reverse (-1), forward+reverse = reverse (-1).

**LinkBehaviour integration:**
- Use `LinkBehaviour.receiver(be, slots, signalCallback)` for both forward and reverse
- Each receiver gets its own `ValueBoxTransform` slot pair (like `RedstoneLinkFrequencySlot`)
- The signal callback stores the received strength and triggers `sendData()` + `notifyUpdate()`
- On mode change or signal change, call `updateSpeed = true` on the BE to trigger re-propagation

**UI:**
- Right-click opens a menu (block-based, not item-based like LinkedController)
- Two ghost item slots for frequency items (reuse Create's `RedstoneLinkFrequencySlot` positioning or custom `ValueBoxTransform`)
- Mode cycle button (cycles SUM → LATEST → POWER_REVERSE)
- Swap button (swaps the two frequency item stacks)
- Similar visual style to `LinkedControllerScreen` (uses `AllGuiTextures.LINKED_CONTROLLER` as base)

**Assets:**
- Copy `assets/create/models/block/gearshift/block.json` and `block_powered.json` → our namespace
- Copy `assets/create/textures/block/gearshift_off.png` and `gearshift_on.png` → tint with a weird hue (e.g., purple/magenta)
- Keep original Create textures referenced in a comment or as `gearshift_original.png` nearby
- Blockstate JSON with `axis` + `powered` variants (mirroring Create's gearshift blockstate)
- Item model JSON

**Creative tab:**
```java
@SubscribeEvent
public static void addToCreateTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()) {
        event.accept(PDRBlocks.SMART_GEARSHIFT.get());
    }
}
```

---

## Feature 2: ProjectPDR-Create Material List Compatibility

### Requirements (from README)
- Right-click on transmutation table → generates NBT chests with items needed for the material list (from Create schematicannon clipboard)
- Check order: (1) team match, (2) enough EMC, (3) knowledge of all items
- Sneak mode: get only unchecked items + auto-check them in clipboard, OR get as many as possible with current EMC/knowledge
- Chests appear on top of the transmutation table as item entities

### Architecture

This is a **mixin/event handler** on the transmutation table's right-click, not a new block.

**Files to create:**

| File | Purpose |
|------|---------|
| `integration/TransmutationTableMaterialList.java` | Core logic: parse clipboard, check permissions, spawn chests |
| `mixin/TransmutationStoneMixin.java` | Mixin into `TransmutationStone.useWithoutItem` to intercept right-click when player holds a clipboard with material list |
| `network/MaterialListRequestPacket.java` | Client→server packet for sneak-mode requests (since useWithoutItem is server-side already, may not need this) |

### Key implementation details

**Trigger detection:**
- Mixin into `TransmutationStone.useWithoutItem()` — before the normal menu opens, check if player is holding a Create clipboard item with `ClipboardContent` data component
- If clipboard has `CLIPBOARD_CONTENT` with entries that have `icon` (ItemStack) + `itemAmount` → it's a material list
- Normal right-click (no clipboard or empty clipboard) → proceed with normal transmutation table behavior
- Right-click with material-list clipboard → run material list logic instead

**Material list parsing:**
```java
// Read from held clipboard item:
List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(clipboardStack);
for (List<ClipboardEntry> page : pages) {
    for (ClipboardEntry entry : page) {
        if (!entry.icon.isEmpty() && entry.itemAmount > 0) {
            // entry.icon = ItemStack, entry.itemAmount = count needed
            // entry.checked = whether already gathered
        }
    }
}
```

**Check order (server-side):**
1. **Team check**: `TransmutationTableBlockEntity.getTeam()` vs `player.getTeam()` — must match (same logic as `TransmutationStone.useWithoutItem`)
2. **EMC check**: Get `IKnowledgeProvider` via `player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)`, check `provider.getEmc()` (which already returns team EMC if player is on a team) + EMC in klein stars in input slots. Sum total item EMC costs and compare.
3. **Knowledge check**: For each required item, `provider.hasKnowledge(ItemInfo.fromStack(stack))` — which already checks both personal and team knowledge

**Sneak mode logic:**
- If player is sneaking:
  - Get only **unchecked** entries from clipboard
  - After spawning chests, mark those entries as `checked = true` in the clipboard
  - OR: get only as many items as EMC/knowledge allows (partial fulfillment), mark those as checked
- If not sneaking: get ALL items (if checks pass)

**Chest generation:**
```java
// For each batch of items (up to 27 per chest):
ItemStack chest = new ItemStack(Items.CHEST);
// Create a chest item with BlockEntityTag containing the items
CompoundTag beTag = new CompoundTag();
ListTag items = new ListTag();
for (int i = 0; i < chestItems.size(); i++) {
    CompoundTag slotTag = new CompoundTag();
    slotTag.putByte("Slot", (byte) i);
    chestItems.get(i).save(registries, slotTag);
    items.add(slotTag);
}
beTag.put("Items", items);
chest.set(DataComponents.CONTAINER, ...); // or use BlockEntityTag for pre-1.20.5 style
// Actually in 1.21.1: use DataComponents.CONTAINER with ItemContainerContents

// Spawn as item entity on top of the table:
ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, chestStack);
level.addFreshEntity(entity);
```

In MC 1.21.1, chest contents are stored via `DataComponents.CONTAINER` (ItemContainerContents), not BlockEntityTag. Need to use:
```java
ItemContainerContents contents = ItemContainerContents.fromItems(itemList);
chestStack.set(DataComponents.CONTAINER, contents);
```

**Using projectpdr API correctly:**
```java
// Get knowledge provider (handles team automatically):
IKnowledgeProvider provider = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);

// Check knowledge (checks both personal + team):
boolean hasKnowledge = provider.hasKnowledge(ItemInfo.fromStack(stack));

// Get EMC (returns team EMC if on team, personal otherwise):
BigInteger emc = provider.getEmc();

// Get item EMC value:
long itemEmc = IEMCProxy.INSTANCE.getValue(stack);

// Add knowledge (for auto-checking in sneak mode — actually we don't add knowledge,
// we mark clipboard entries as checked):
// provider.addKnowledge(info) — NOT needed; we just update the clipboard

// Check team match (same as TransmutationStone):
TransmutationTableBlockEntity table = (TransmutationTableBlockEntity) level.getBlockEntity(pos);
String tableTeam = table.getTeam();
String playerTeam = player.getTeam() == null ? null : player.getTeam().getName();
if (tableTeam != null && !tableTeam.equals(playerTeam)) { /* deny */ }
```

---

## Registration & Setup

### Main mod class changes (`PDRTweaks.java`):
```java
@Mod(PDRTweaks.MODID)
public class PDRTweaks {
    public static final String MODID = "pdr_tweaks";
    
    public PDRTweaks(IEventBus modEventBus) {
        PDRBlocks.BLOCKS.register(modEventBus);
        PDRBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        PDRMenus.MENUS.register(modEventBus);
        PDRPackets.register(modEventBus);
        // Register client screen via NeoForge's RegisterMenuScreensEvent
        // Register creative tab additions via NeoForge.EVENT_BUS
    }
}
```

### Package structure:
```
org.example.pdr_tweaks/
├── PDRTweaks.java                    (modified — registration entry point)
├── content/
│   └── kinetics/
│       ├── SmartGearshiftBlock.java
│       ├── SmartGearshiftBlockEntity.java
│       ├── SmartGearshiftMode.java
│       ├── SmartGearshiftMenu.java
│       ├── SmartGearshiftScreen.java
│       ├── SmartGearshiftConfigPacket.java
│       └── SmartGearshiftSlot.java   (ValueBoxTransform for frequency slots)
├── integration/
│   └── projectpdr/
│       └── TransmutationTableMaterialList.java
├── mixin/
│   ├── TransmutationStoneMixin.java
│   └── pdr_tweaks.mixins.json
├── registry/
│   ├── PDRBlocks.java
│   ├── PDRBlockEntities.java
│   ├── PDRMenus.java
│   └── PDRPackets.java
└── client/
    └── PDRClient.java                (screen registration)
```

### Resources:
```
src/main/resources/
├── assets/pdr_tweaks/
│   ├── blockstates/smart_gearshift.json
│   ├── models/block/smart_gearshift/block.json
│   ├── models/block/smart_gearshift/block_powered.json
│   ├── models/block/smart_gearshift/item.json
│   ├── models/item/smart_gearshift.json
│   ├── textures/block/smart_gearshift_off.png   (tinted weird hue)
│   ├── textures/block/smart_gearshift_on.png    (tinted weird hue)
│   ├── textures/block/gearshift_original_off.png (copy of Create's original for reference)
│   ├── textures/block/gearshift_original_on.png  (copy of Create's original for reference)
│   └── lang/en_us.json
├── data/pdr_tweaks/
│   ├── loot_table/blocks/smart_gearshift.json
│   └── recipe/crafting/smart_gearshift.json      (crafting: clutch + gearshift + redstone link + precision mechanism)
└── pdr_tweaks.mixins.json
```

---

## Dependencies Summary

| API | Usage |
|-----|-------|
| `AbstractEncasedShaftBlock` | Base class for smart gearshift block |
| `SplitShaftBlockEntity` / `DirectionalShaftHalvesBlockEntity` | Base class for smart gearshift BE |
| `KineticBlockEntity` | Speed/rotation system |
| `IBE<T>` | Block-entity binding interface |
| `LinkBehaviour` | Redstone link receiver (forward + reverse) |
| `IRedstoneLinkable` | Interface implemented by LinkBehaviour |
| `RedstoneLinkNetworkHandler.Frequency` | Frequency representation |
| `ValueBoxTransform.Dual` | UI slot positioning for frequency items |
| `AllCreativeModeTabs.BASE_CREATIVE_TAB` | Create's creative tab key for adding our block |
| `BuildCreativeModeTabContentsEvent` | NeoForge event to add to Create's tab |
| `PECapabilities.KNOWLEDGE_CAPABILITY` | Get player knowledge/EMC provider |
| `IEMCProxy.INSTANCE.getValue()` | Get EMC value of an item |
| `IKnowledgeProvider.hasKnowledge()` | Check knowledge (personal + team) |
| `IKnowledgeProvider.getEmc()` | Get EMC (team-aware) |
| `TransmutationTableBlockEntity.getTeam()` | Check table team binding |
| `ClipboardEntry.readAll()` | Parse material list from clipboard |
| `AllDataComponents.CLIPBOARD_CONTENT` | Data component for clipboard content |
| `DataComponents.CONTAINER` | Store items in chest NBT (1.21.1) |

---

## Implementation Order

1. **Setup**: Modify `PDRTweaks.java`, create registry classes, update `neoforge.mods.toml` if needed
2. **Smart Gearshift — Block & BE**: `SmartGearshiftBlock`, `SmartGearshiftBlockEntity`, `SmartGearshiftMode` — get basic block placed and rotating
3. **Smart Gearshift — LinkBehaviour**: Add two receiver LinkBehaviours to the BE, verify signals flow
4. **Smart Gearshift — Rotation logic**: Implement `getRotationSpeedModifier` for all 3 modes
5. **Smart Gearshift — UI**: Menu + Screen + Packet for configuration (frequencies, mode, swap)
6. **Smart Gearshift — Assets**: Blockstate, models, textures (tinted), lang, loot table, recipe
7. **Smart Gearshift — Creative tab**: `BuildCreativeModeTabContentsEvent` handler
8. **Material List — Mixin**: Intercept transmutation table right-click with clipboard
9. **Material List — Parsing**: Read clipboard entries, extract items + amounts + checked state
10. **Material List — Checks**: Team, EMC, knowledge — in correct order
11. **Material List — Chest spawning**: Create NBT chests, spawn as item entities on table
12. **Material List — Sneak mode**: Filter unchecked, auto-check in clipboard, partial fulfillment
13. **Testing**: Build, run client, verify both features in-game
