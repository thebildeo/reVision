package revision.world.blocks.production

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf
import arc.struct.ObjectFloatMap
import arc.struct.ObjectIntMap
import mindustry.Vars
import mindustry.content.Fx
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Sounds
import mindustry.graphics.Pal
import mindustry.type.Item
import mindustry.ui.Cicon
import mindustry.world.Block
import mindustry.world.Edges
import mindustry.world.Tile
import mindustry.world.meta.BlockGroup
import mindustry.world.meta.Stat
import mindustry.world.meta.StatUnit

open class MultiDrill(name: String) : Block(name) {

    val oreCount = ObjectIntMap<Item>()

    val hardnessDrillMultiplier = 50f
    val drillTime = 280f
    val liquidBoostIntensity = 1.8f

    val warmupSpeed = 0.01f
    val rotateSpeed = 6f

    val drillEffect = Fx.mineHuge
    val updateEffect = Fx.pulverizeRed
    val updateEffectChance = 0.03f

    val heatColor = Color.valueOf("ff5512")

    lateinit var rimRegion: TextureRegion
    lateinit var rotatorRegion: TextureRegion
    lateinit var topRegion: TextureRegion

    init {
        update = true
        solid = true
        group = BlockGroup.drills
        hasLiquids = true
        hasItems = true
        ambientSound = Sounds.drill
        ambientSoundVolume = 0.018f
    }

    override fun load() {
        super.load()
        rimRegion = Core.atlas.find("$name-rim")
        rotatorRegion = Core.atlas.find("$name-rotator")
        topRegion = Core.atlas.find("$name-top")
    }

    override fun icons(): Array<TextureRegion> {
        return arrayOf(region, rotatorRegion, topRegion)
    }

    override fun canPlaceOn(tile: Tile, team: Team): Boolean {
        for (other in tile.getLinkedTilesAs(this, tempTiles)){
            if (canMine(other)) {
                return true
            }
        }
        for (edge in Edges.getEdges(size)) {
            val other = Vars.world.tile(tile.x + edge.x, tile.y + edge.y)
            if (canMine(other)) {
                return true
            }
        }
        return false
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        super.drawPlace(x, y, rotation, valid)

        val tile = Vars.world.tile(x, y) ?: return
        countOre(tile)

        Draw.mixcol(Color.darkGray, 1f)
        var off = 0
        for (ore in oreCount.keys()) {
            val dx: Float = x * Vars.tilesize + off - 4f
            val dy = y * Vars.tilesize + off + size * Vars.tilesize / 2f + 5
            Draw.rect(ore.icon(Cicon.small), dx + off, dy)
            off += 4
        }
        Draw.color()

        Draw.color(Pal.placing)
        Lines.stroke(size.toFloat())
        Lines.square(x * Vars.tilesize + offset, y * Vars.tilesize + offset, (Vars.tilesize / 2f) * (size + 4).toFloat())
    }

    fun countOre(tile: Tile) {
        oreCount.clear()

        for (other in tile.getLinkedTilesAs(this, tempTiles)) {
            if (canMine(other)) {
                oreCount.increment(other.drop(), 0, 1)
            }
        }

        for (edge in Edges.getEdges(size)) {
            val other = Vars.world.tile(tile.x + edge.x, tile.y + edge.y)
            if (canMine(other)) {
                oreCount.increment(other.drop(), 0, 1)
            }
        }
    }

    fun canMine(tile: Tile?) = tile?.drop() != null

    override fun setStats() {
        super.setStats()
        stats.add(Stat.drillSpeed, 60f / drillTime * size * size, StatUnit.itemsSecond)
        stats.add(Stat.boostEffect, liquidBoostIntensity * liquidBoostIntensity, StatUnit.timesSpeed)
    }

    inner class MultiDrillBuild : Building() {

        val ores = ObjectIntMap<Item>()
        val oreProgress = ObjectFloatMap<Item>()

        var timeDrilled = 0f
        var warmup = 0f

        override fun shouldActiveSound() = efficiency() > 0.01f

        override fun ambientVolume() = efficiency() * (size * size) / 4f

        override fun drawSelect() {
            var off = 0
            for (ore in ores.keys()) {
                val dx = x - size * Vars.tilesize / 2f
                val dy = y + size * Vars.tilesize / 2f
                Draw.mixcol(Color.darkGray, 1f)
                Draw.rect(ore.icon(Cicon.small), dx + off, dy)
                off += 4
            }
        }

        override fun drawCracks() {}

        override fun onProximityUpdate() {
            countOre(tile)
            ores.clear()
            oreProgress.clear()
            for (ore in oreCount) {
                ores.put(ore.key, ore.value)
            }
        }

        override fun updateTile() {
            if (ores.isEmpty) return

            if (timer(timerDump, dumpTime.toFloat())) {
                items.each { item, _ -> dump(item) }
            }

            timeDrilled += warmup * delta()

            if (items.total() < ores.size * itemCapacity && consValid()) {
                var speed = 1f

                if (cons.optionalValid()) {
                    speed = liquidBoostIntensity;
                }

                speed *= efficiency()
                warmup = Mathf.lerpDelta(warmup, speed, warmupSpeed)

                for (ore in ores) {
                    oreProgress.increment(ore.key, 0f, delta() * ore.value * speed * warmup)
                }

            } else {
                warmup = Mathf.lerpDelta(warmup, 0f, warmupSpeed)
                return
            }

            for (ore in ores) {
                val delay = drillTime + hardnessDrillMultiplier * ore.key.hardness
                if (oreProgress.get(ore.key, 0f) >= delay && items.get(ore.key) < itemCapacity) {
                    offload(ore.key)
                    oreProgress.increment(ore.key, 0f, -delay)
                }
            }
        }
    }
}