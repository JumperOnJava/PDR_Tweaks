PDR Tweaks
this mod adds few useful features for create mod

Smart gearshift
crafts using clutch, gearshift, redstone link, precision mechanism
this gearshift can be controlled by redstone links/ controllers
allows to spin forward/in-reverse depending on signals
configured with ui opened on right click, ui is similar to controller ui but with two redstone link addresses instead of 4, one is forward, other is reverse
there should be swap button which swaps adresses between two inputs
three modes:
    - sum mode (default): spins forward/reverse based on inputs, doesn't spin if both inputs active
    - latest mode: spins based on latest input. If forward is active and in that time reverse is enabled it sping in reverse
    - power-reverse mode: spins if spin input is on (renames forward in ui); reverses if reverse input is passed

ProjectPDR-Create material-list compatibility:
    Create mod has schematicannon which generates and you can put material list onto clipboard.
    I want player to be able to quickly get all items needed for material list
    When player right clicks onto transmutation table it generates nbt chests with items with needed items for this material list
    What to check (in order):
        - can player use this table (does team/unteamness match)
        - does player/team has enough EMC to create this recipe (count chests in too)
        - does player/team has knowledge about all the items in recipe
    There is also sneak mode which allows to get only unchecked items, and automatically checks them in clipboard 
    or gets only as much items as player can with their current team/personal knowledge/emc score (check them too)
    Make sure you use same correct rules that projectpdr mod uses 
