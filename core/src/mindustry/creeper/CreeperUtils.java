package mindustry.creeper;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.struct.EnumSet;
import arc.util.Timer;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class CreeperUtils {
    public static float updateInterval = 0.025f; // Base update interval in seconds
    public static float transferRate = 0.249f; // Base transfer rate NOTE: keep below 0.25f
    public static float evaporationRate = 0f; // Base creeper evaporation
    public static float creeperDamage = 2.5f; // Base creeper damage
    public static float creeperEvaporationDamageMultiplier = 20f; // Creeper percentage that will remain upon damaging something
    public static float creeperUnitDamage = 4f;
    public static float minCreeper = 0.9f; // Minimum amount of creeper required for transfer

    public static BulletType sporeType = Bullets.artilleryDense;
    public static float sporeMaxRangeMultiplier = 30f;
    public static float sporeAmount = 30f;
    public static float sporeRadius = 3f;
    public static float sporeSpeedMultiplier = 0.15f;
    public static float sporeHealthMultiplier = 10f;
    public static float sporeTargetOffset = 256f;

    public static float unitShieldDamageMultiplier = 1f;
    public static float buildShieldDamageMultiplier = 1.5f;
    public static float shieldBoostProtectionMultiplier = 0.5f;
    public static float shieldCreeperDropAmount = 5f;
    public static float shieldCreeperDropRadius = 3f;

    public static float nullifierRange = 10f;


    public static float nullifyDamage = 1500f; // Damage that needs to be applied for the core to be suspended
    public static float nullifyTimeout = 360f; // The amount of time a core remains suspended (resets upon enough damage applied)

    public static float nullificationPeriod = 10f; // How many seconds all cores have to be nullified (suspended) in order for the game to end
    private static int nullifiedCount = 0;

    public static Team creeperTeam = Team.blue;

    public static HashMap<Integer, Block> creeperBlocks = new HashMap<>();
    public static HashMap<Block, Integer> creeperLevels = new HashMap<>();

    public static HashMap<Block, Emitter> emitterBlocks = new HashMap<>();

    public static Seq<Emitter> creeperEmitters = new Seq<>();
    public static Seq<Tile> creeperableTiles = new Seq<>();
    public static Seq<ForceProjector.ForceBuild> shields = new Seq<>();

    public static Timer.Task runner;
    public static Timer.Task fixedRunner;

    public static String getTrafficlightColor(double value){
        return "#"+Integer.toHexString(java.awt.Color.HSBtoRGB((float)value/3f, 1f, 1f)).substring(2);
    }


    public static float[] targetSpore(){
        float[] ret = null;
        int iterations = 0;

        while(ret == null && iterations < 100 && Groups.player.size() > 0){
            iterations++;
            Player player = Groups.player.index(Mathf.random(0, Groups.player.size()-1));
            if(player.unit() == null || player.x == 0 && player.y == 0)
                continue;

            Unit unit = player.unit();
            ret = new float[]{unit.x + Mathf.random(-sporeTargetOffset, sporeTargetOffset), unit.y + Mathf.random(-sporeTargetOffset, sporeTargetOffset)};
        }

        return (ret != null ? ret : new float[]{0,0});
    }

    public static void sporeCollision(Bullet bullet, float x, float y){
        Tile tile = world.tileWorld(x, y);
        if(!validTile(tile))
            return;

        Call.effect(Fx.sapExplosion, x, y, sporeRadius, Color.blue);

        depositCreeper(tile, sporeRadius, sporeAmount);
    }

    public static void init(){
        sporeType.isCreeper = true;


        creeperBlocks.put(0, Blocks.air);
        creeperBlocks.put(1, Blocks.conveyor);
        creeperBlocks.put(2, Blocks.titaniumConveyor);
        creeperBlocks.put(3, Blocks.armoredConveyor);
        creeperBlocks.put(4, Blocks.plastaniumConveyor);
        creeperBlocks.put(5, Blocks.scrapWall);
        creeperBlocks.put(6, Blocks.titaniumWall);
        creeperBlocks.put(7, Blocks.thoriumWall);
        creeperBlocks.put(8, Blocks.plastaniumWall);
        creeperBlocks.put(9, Blocks.phaseWall);
        creeperBlocks.put(10, Blocks.surgeWall);

        // this is purely for damage multiplication
        creeperBlocks.put(12, Blocks.thoriumReactor);
        creeperBlocks.put(20, Blocks.coreShard);
        creeperBlocks.put(35, Blocks.coreFoundation);
        creeperBlocks.put(50, Blocks.coreNucleus);

        for(var set : creeperBlocks.entrySet()){
            BlockFlag[] newFlags = new BlockFlag[set.getValue().flags.size() + 1];
            int i = 0;
            for (BlockFlag flag : set.getValue().flags) {
                newFlags[i++] = flag;
            }
            newFlags[i] = BlockFlag.generator;
            set.getValue().flags = EnumSet.of(newFlags);
            creeperLevels.put(set.getValue(), set.getKey());
        }

        emitterBlocks.put(Blocks.coreShard, new Emitter(20, 20));
        emitterBlocks.put(Blocks.coreFoundation, new Emitter(8, 20));
        emitterBlocks.put(Blocks.coreNucleus, new Emitter(3, 30));

        // todo: add "spore launchers", etc. (yes creeper world ripoff i know)

        Events.on(EventType.GameOverEvent.class, e -> {
            if(runner != null)
                runner.cancel();
            if(fixedRunner != null)
                fixedRunner.cancel();

            creeperableTiles.clear();
            creeperEmitters.clear();
            shields.clear();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            // DOES NOT SIGNIFY WORLD IS LOADED, need to wait
            try {
                Thread.sleep(2000);
                creeperableTiles.clear();
                creeperEmitters.clear();

                Seq<Building> iterated = new Seq<>();
                for(Tile tile : world.tiles){
                    if(!tile.block().isStatic() && !tile.floor().isDeep())
                        creeperableTiles.add(tile);

                    if(tile.block() != null && emitterBlocks.containsKey(tile.block()) && !iterated.contains(tile.build) && tile.build.team == creeperTeam){
                        iterated.add(tile.build);
                        creeperEmitters.add(new Emitter(tile.build));
                    }
                }
                
                Log.info(creeperableTiles.size + " creeperableTiles");
                Log.info(creeperEmitters.size + " emitters");

                runner = Timer.schedule(CreeperUtils::updateCreeper, 0, updateInterval);
                fixedRunner = Timer.schedule(CreeperUtils::fixedUpdate, 0, 1);

            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(CreeperUtils.creeperBlocks.containsValue(e.tile.block()))
                onCreeperDestroy(e.tile);
            
            e.tile.creep = 0;
            e.tile.newCreep = 0;
        });

        Timer.schedule(() -> {
            if (!state.isGame()) return;
            Call.infoPopup("\uE88B [" + getTrafficlightColor(Mathf.clamp((CreeperUtils.nullifiedCount / Math.max(1.0, creeperEmitters.size)), 0f, 1f)) + "]" + CreeperUtils.nullifiedCount + "/" + CreeperUtils.creeperEmitters.size + "[] emitters suspended", 10f, 20, 50, 20, 527, 0);
            // check for gameover
            if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
                Timer.schedule(() -> {
                    if(CreeperUtils.nullifiedCount == CreeperUtils.creeperEmitters.size){
                        // gameover
                        state.gameOver = true;
                        Events.fire(new EventType.GameOverEvent(Team.sharded));
                    }
                    // failed to win, core got unsuspended
                }, nullificationPeriod);
            }
        }, 0, 10);
    }

    public static void depositCreeper(Tile tile, float radius, float amount){
        Geometry.circle(tile.x, tile.y, (int) radius, (cx, cy) -> {
            Tile ct = world.tile(cx, cy);
            if(!validTile(ct) || (tile.block() instanceof StaticWall || (tile.floor() != null && !tile.floor().placeableOn || tile.floor().isDeep() || tile.block() instanceof Cliff)))
                return;

            ct.creep = Math.min(ct.creep + amount, 10);
            ct.newCreep = ct.creep;
        });
    }

    private static void onCreeperDestroy(Tile tile) {
        tile.creep = 0;
        tile.newCreep = 0;
    }

    public static void fixedUpdate(){
        // dont update anything if game is paused
        if (!state.isPlaying() || state.serverPaused) return;

        int newcount = 0;
        for(Emitter emitter : creeperEmitters){
            emitter.fixedUpdate();
            if(emitter.nullified)
                newcount++;
        }
        for(ForceProjector.ForceBuild shield : shields){
            if(shield == null || shield.dead || shield.health <= 0f || shield.healthLeft <= 0f) {
                shields.remove(shield);
                if (shield == null) continue;
                Core.app.post(shield::kill);

                float percentage = 1f - shield.healthLeft / ((ForceProjector) shield.block).shieldHealth;
                depositCreeper(shield.tile, shieldCreeperDropRadius, shieldCreeperDropAmount * percentage);

                continue;
            }

            double percentage = shield.healthLeft / ((ForceProjector) shield.block).shieldHealth;
            Call.label("[" + getTrafficlightColor(percentage) + "]" + (int) (percentage*100) + "%" + (shield.phaseHeat > 0.1f ? " [#f4ba6e]\uE86B +" + ((int) ((1f - CreeperUtils.shieldBoostProtectionMultiplier) * 100f)) + "%" : ""), 1f, shield.x, shield.y);
        }

        nullifiedCount = newcount;
    }

    public static void updateCreeper(){
        // dont update anything if game is paused
        if (!state.isPlaying() || state.serverPaused) return;

        // update emitters
        for(Emitter emitter : creeperEmitters){
            if(!emitter.update())
                creeperEmitters.remove(emitter);
        }

        // update creeper flow
        for(Tile tile : creeperableTiles){
            transferCreeper(tile, world.tile(tile.x+1, tile.y));
            transferCreeper(tile, world.tile(tile.x-1, tile.y));
            transferCreeper(tile, world.tile(tile.x, tile.y+1));
            transferCreeper(tile, world.tile(tile.x, tile.y-1));
        }

        // clamp creeper
        for(Tile tile : creeperableTiles){
            if(tile.newCreep > 10)
                tile.newCreep = 10;
            else if (tile.newCreep < 0.01)
                tile.newCreep = 0;
            tile.creep = tile.newCreep;
            drawCreeper(tile);
        }
    }

    // creates appropiate blocks for creeper OR damages the tile that it wants to take
    public static void drawCreeper(Tile tile){

        // check if can transfer anyway because weird
        if(tile.creep >= 1f) {

            if(tile.creep < 10f && tile.block() == creeperBlocks.get(10))
                tile.removeNet();

                // deal continuous damage
            if (tile.build != null && tile.build.team != creeperTeam) {

                Core.app.post(() -> {
                    if (tile.build != null) {

                        if (Mathf.chance(0.05f))
                            Call.effect(Fx.bubble, tile.build.x, tile.build.y, 0, Color.blue);

                        tile.build.damageContinuous(creeperDamage * tile.creep);
                        tile.creep -= creeperDamage / creeperEvaporationDamageMultiplier;
                        tile.newCreep -= creeperDamage / creeperEvaporationDamageMultiplier;
                    }
                });
            }

        }
        if (tile.x < world.width() && tile.y < world.height() && tile.creep >= 1f && !(tile.block() instanceof CoreBlock) && creeperLevels.getOrDefault(tile.block(), 10) < Math.round(tile.creep) || tile.block() instanceof TreeBlock){
            Core.app.post(() -> tile.setNet(creeperBlocks.get(Mathf.clamp(Math.round(tile.creep), 0, 10)), creeperTeam, Mathf.random(0, 3)));
        }
    }

    public static boolean canTransfer(Tile source, Tile target){
        boolean amountValid = source.creep > minCreeper;

        if(!validTile(target))
            return false;

        if(target.block() instanceof TreeBlock && amountValid)
            return true;

        if(target.block() instanceof StaticWall || (target.floor() != null && !target.floor().placeableOn || target.floor().isDeep() || target.block() instanceof Cliff))
            return false;

        if(source.build != null && source.build.team != creeperTeam) {
            // wall or something, decline transfer but damage the wall
            drawCreeper(source);
            return false;
        }

        return amountValid;
    }

    public static boolean validTile(Tile tile){
        return tile != null;
    }

    public static void transferCreeper(Tile source, Tile target) {
        if(canTransfer(source, target)){
            float sourceCreeper = source.creep;

            if (sourceCreeper > 0){
                float sourceTotal = source.creep;
                float targetTotal = target.creep;
                float delta;

                if (sourceTotal > targetTotal) {
                    delta = sourceTotal - targetTotal;
                    if (delta > sourceCreeper)
                        delta = sourceCreeper;

                    float adjustedDelta = delta * transferRate;
                    source.newCreep -= adjustedDelta;
                    target.newCreep += adjustedDelta - evaporationRate;
                }
            }
        }
    }
}
