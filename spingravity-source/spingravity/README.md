# Spin Gravity (Fabric, Minecraft 26.3)

Artificial gravity for a rotating habitat.

* Gravity follows your distance from the spin axis: `g = w^2 * r`.
  Higher up = closer to the axis = lower gravity. Lower down = further out = higher gravity.
* 1 block = 1 m. "1 G" of gameplay gravity = vanilla gravity.
* Settings are saved per world in `<world>/spingravity.json`.

## Commands
| Command | Effect |
|---|---|
| `/spin` / `/spin info` | Shows rpm, rim speed, **gravity at sea level in Gs**, gravity at your height, rpm needed for 1 G |
| `/spin rpm <rpm>` | Set the spin directly |
| `/spin gravity <G>` | Set the spin so sea-level gravity is that many Gs |
| `/spin radius <blocks>` | Radius to the axis (keeps the sea-level G the same) |
| `/spin floor <y>` / `/spin floor here` | Which height counts as "sea level" |
| `/spin preset cooper` | 1 km radius, 1 G at Y=63 |
| `/spin preset tube` | 128-block radius, 1 G at your current height (for flat/void worlds) |
| `/spin on` / `/spin off` | Enable/disable |
| `/spin hud` | Toggle an action-bar readout of your current G |

Only the overworld is affected. Players only (mobs/items keep vanilla gravity).
Anyone can run the commands: use a permissions mod on shared servers.

## Build the jar
Needs JDK 25 and Gradle 9.6:

    gradle build

The jar is `build/libs/spingravity-1.0.0.jar` (not the `-sources` one).
No local setup? Push this folder to a GitHub repo: the `build` workflow compiles it and
attaches the jar to the run under "Artifacts".

## Put it on Modrinth
Create a project (type: Mod, Client and Server), then upload the jar as a version:
game version 26.3, loader Fabric, dependency Fabric API (required).
