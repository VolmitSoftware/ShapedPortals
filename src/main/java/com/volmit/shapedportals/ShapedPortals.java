package com.volmit.shapedportals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShapedPortals extends JavaPlugin implements Listener {
    private Set<Player> creating = new HashSet<>();
    private Config config = loadConfig();

    public void onEnable()
    {
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void onDisable()
    {
        HandlerList.unregisterAll((Plugin) this);
    }

    private Config loadConfig() {
        File file = new File(getDataFolder(), "config.json");
        file.getParentFile().mkdirs();
        Config config = new Config();

        if(file.exists())
        {
            try
            {
                BufferedReader b = new BufferedReader(new FileReader(file));
                StringBuilder c = new StringBuilder();
                String l;

                while((l = b.readLine()) != null)
                {
                    c.append(l);
                }

                b.close();
                config = new Gson().fromJson(c.toString(), Config.class);
            }

            catch(Throwable ignored)
            {

            }
        }

        try
        {
            PrintWriter pw = new PrintWriter(file);
            pw.println(new GsonBuilder().setPrettyPrinting().create().toJson(config));
            pw.close();
        }

        catch(Throwable ignored)
        {

        }

        return config;
    }


    @SuppressWarnings("RedundantCollectionOperation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PortalCreateEvent e)
    {
        if(config.creationSounds && !e.getBlocks().isEmpty())
        {
            e.getWorld().playSound(e.getBlocks().get((int) (Math.random() * (e.getBlocks().size() - 1)))
                    .getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 0.67f);
        }

        if(e.getReason().equals(PortalCreateEvent.CreateReason.FIRE))
        {
            if(e.getEntity() != null && e.getEntity() instanceof Player && creating.contains((Player)e.getEntity()))
            {
                creating.remove((Player)e.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e)
    {
        if(e.getBlockPlaced().getType().equals(Material.FIRE))
        {
            if(!config.enablePortals)
            {
                return;
            }

            creating.add(e.getPlayer());
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if(creating.contains(e.getPlayer()))
                {
                    creating.remove(e.getPlayer());
                    //noinspection deprecation
                    getServer().getScheduler().scheduleAsyncDelayedTask(this, ()
                            -> constructNetherPortal(e.getBlockPlaced(), e.getPlayer(), PortalCreateEvent.CreateReason.FIRE));
                }
            },1);
        }
    }

    private void spread(Block cursor, List<Block> blocks, boolean x, AtomicBoolean fail)
    {
        if(blocks.size() > config.maxNetherPortalBlocks)
        {
            fail.set(true);
        }

        if(fail.get())
        {
            return;
        }

        blocks.add(cursor);
        Block[] test = new Block[]{cursor.getRelative(BlockFace.UP),
                cursor.getRelative(BlockFace.DOWN),
                cursor.getRelative(x ? BlockFace.EAST : BlockFace.SOUTH),
                cursor.getRelative(x ? BlockFace.WEST : BlockFace.NORTH)};

        for(Block i : test)
        {
            if(blocks.contains(i))
            {
                continue;
            }

            if(couldBePortal(i))
            {
                spread(i, blocks, x, fail);
            }

            else if(!isNetherPortalBlock(i))
            {
                fail.set(true);
            }

            if(fail.get())
            {
                return;
            }
        }
    }

    private boolean couldBePortal(Block b)
    {
        return !b.getType().isSolid()
                && (b.getType().isAir()
                || (b.getType().equals(Material.FIRE) || b.getType().equals(Material.SOUL_FIRE))
        );
    }

    @SuppressWarnings("ConstantConditions")
    public void constructNetherPortal(Block fire, Entity potentialCreator, PortalCreateEvent.CreateReason reason) {
        boolean x = false;
        World w = fire.getWorld();
        List<Block> blocks = new ArrayList<>();

        for(int i = fire.getY()-1; i >= w.getMinHeight(); i--)
        {
            Block findRing = w.getBlockAt(fire.getX(), i, fire.getZ());
            if(isNetherPortalBlock(findRing))
            {
                if((isNetherPortalBlock(findRing.getRelative(BlockFace.NORTH))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.SOUTH)))

                        || (isNetherPortalBlock(findRing.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH)))

                        || (isNetherPortalBlock(findRing.getRelative(BlockFace.DOWN).getRelative(BlockFace.NORTH))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.DOWN).getRelative(BlockFace.SOUTH)))

                )
                {
                    x = false;
                }

                else if((isNetherPortalBlock(findRing.getRelative(BlockFace.EAST))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.WEST)))

                        || (isNetherPortalBlock(findRing.getRelative(BlockFace.UP).getRelative(BlockFace.EAST))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.UP).getRelative(BlockFace.WEST)))

                        || (isNetherPortalBlock(findRing.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST))
                        || isNetherPortalBlock(findRing.getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST))))
                {
                    x =  true;
                }

                else
                {
                    return;
                }

                break;
            }
        }

        AtomicBoolean fail = new AtomicBoolean();
        spread(fire, blocks, x, fail);

        if(!fail.get())
        {
            boolean finalX = x;
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                List<BlockState> states = new ArrayList<>();
                BlockData b = createNetherPortalBlock(finalX);
                for(Block i : blocks)
                {
                    BlockState state = i.getState();
                    state.setBlockData(b);
                    states.add(state);
                }

                PortalCreateEvent c = new PortalCreateEvent(states, w, potentialCreator, reason);
                getServer().getPluginManager().callEvent(c);
                if(!c.isCancelled())
                {
                    for(BlockState i : states)
                    {
                        i.update(true);
                    }
                }
            });
        }
    }

    public BlockData createNetherPortalBlock(boolean x)
    {
        Orientable b = (Orientable) Material.NETHER_PORTAL.createBlockData();
        b.setAxis(x ? Axis.X : Axis.Z);
        return b;
    }

    private boolean isNetherPortalBlock(Block b)
    {
        return b.getType().equals(Material.OBSIDIAN)
                || (config.allowCryingObsidian && b.getType().equals(Material.CRYING_OBSIDIAN));
    }

    @SuppressWarnings("FieldMayBeFinal")
    static class Config
    {
        private boolean creationSounds = true;
        private boolean enablePortals = true;
        private boolean allowCryingObsidian = true;
        private int maxNetherPortalBlocks = 32;
    }
}
