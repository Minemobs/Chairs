package fr.minemobs.chairs;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChairListener implements Listener {

    private static final Map<Block, LivingEntity> chairs = new HashMap<>();
    private final JavaPlugin plugin;

    ChairListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChairInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().isSneaking()
                || !(event.getClickedBlock().getBlockData() instanceof Stairs stairs))
            return;
        LivingEntity ast = null;
        if (!chairs.containsKey(event.getClickedBlock())) {
            Location centeredLoc = getCenteredLoc(event.getClickedBlock());
            centeredLoc.setDirection(stairs.getFacing().getDirection().multiply(-1));
            ArmorStand asT = event.getPlayer().getWorld().spawn(centeredLoc, ArmorStand.class, chair -> {
                chair.setMarker(true);
                chair.setSilent(true);
                chair.setGravity(false);
                chair.setPersistent(true);
                chair.setVisible(false);
            });
            chairs.put(event.getClickedBlock(), asT);
            ast = asT;
        }
        if (ast == null)
            ast = chairs.get(event.getClickedBlock());
        if (!ast.getPassengers().isEmpty())
            return;
        ast.addPassenger(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getDismounted() instanceof ArmorStand ast))
            return;
        Location location = ast.getLocation();
        chairs.remove(location.subtract(.5d, .25d, .5d).getBlock());
        player.teleport(location.add(0d, 1d, 0d).setDirection(player.getEyeLocation().getDirection()));
        ast.remove();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!chairs.containsKey(event.getBlock()))
            return;
        LivingEntity entity = chairs.get(event.getBlock());
        entity.eject();
        entity.remove();
    }

    @EventHandler
    public void onBlockExplode(EntityExplodeEvent event) {
        chairs.keySet().stream()
                .filter(chair -> event.blockList().contains(chair))
                .toList().forEach(block -> {
                    LivingEntity entity = chairs.get(block);
                    entity.eject();
                    entity.remove();
                });
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        onPiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        onPiston(event.getBlocks(), event.getDirection());
    }

    private Location getCenteredLoc(Block block) {
        return getCenteredLoc(block.getLocation());
    }

    private Location getCenteredLoc(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
                .add(.5d, .25d, .5d);
    }

    private void onPiston(List<Block> blocks, BlockFace direction) {
        blocks.stream().filter(chairs::containsKey).map(Block::getState).forEach(block -> {
            LivingEntity ast = chairs.get(block.getBlock());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location add = getCenteredLoc(block.getBlock().getLocation().add(direction.getDirection()));
                Block blk = add.getBlock();
                if (blk.getBlockData() instanceof Stairs rot)
                    add.setDirection(rot.getFacing().getDirection());
                setEntityPos(ast, add);
                chairs.put(blk, ast);
            });
        });
    }

    private void setEntityPos(Entity entity, Location loc) {
        try {
            Class<?> entityClass = Class.forName("org.bukkit.craftbukkit."
                    + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]
                    + ".entity.CraftEntity");
            Object ent = entityClass.cast(entity);
            Method getHandle = ent.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(ent);
            Method method = getHandle.getReturnType().getMethod("a", double.class, double.class, double.class,
                    float.class, float.class);
            method.invoke(handle, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe(() -> Throwables.getStackTraceAsString(e));
        }
    }

    public static void killAllChairs() {
        ImmutableMap.copyOf(chairs).forEach((block, entity) -> {
            entity.eject();
            entity.remove();
            chairs.remove(block);
        });
    }
}