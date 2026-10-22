# ShapedPortals

Custom-shaped Nether and horizontal End portals with saved records, integrity repair, and in-game administration. Minecraft handles travel unless Wormholes accepts a Nether portal.

## Requirements

- Minecraft 26.1 through 26.3 on Spigot, Paper, Folia, or a compatible server.
- Java 25.
- Network access for dependency downloads on the first start.

## Install

1. Stop the server and back up its worlds and plugin data.
2. Place the ShapedPortals distribution jar in `plugins/`, replacing the previous jar.
3. Start the server, check `/sp status`, and open `/sp config` to review the settings.

See the [wiki](https://docs.volmitsoftware.com/shapedportals), [configuration reference](https://docs.volmitsoftware.com/shapedportals/01-installation-configuration), and [commands and permissions](https://docs.volmitsoftware.com/shapedportals/00-overview#commands).

## Build

Use Java 25. Build the sibling `Wormholes` API with its `apiJar` task first, or supply `-PwormholesApiJar=<path>`. From the ShapedPortals repository:

```powershell
.\gradlew.bat build buildSwiftSwamp
```

The build resolves local VolmLib automatically and stages the verified distribution as `../BUILDS/ShapedPortals.jar`. Override the staging directory with `-PbuildsDirectory=<path>`. See [building from source](https://docs.volmitsoftware.com/shapedportals/04-architecture-limits#build-from-source) for dependency and publication details.
