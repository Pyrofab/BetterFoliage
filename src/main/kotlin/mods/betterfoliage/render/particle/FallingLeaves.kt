package mods.betterfoliage.render.particle

import mods.betterfoliage.config.Config
import mods.betterfoliage.model.HSB
import mods.betterfoliage.texture.LeafParticleKey
import mods.betterfoliage.texture.LeafParticleRegistry
import mods.betterfoliage.util.Double3
import mods.betterfoliage.util.PI2
import mods.betterfoliage.util.minmax
import mods.betterfoliage.util.randomB
import mods.betterfoliage.util.randomD
import mods.betterfoliage.util.randomF
import mods.betterfoliage.util.randomI
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.IParticleRenderType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FallingLeafParticle(
    world: World, pos: BlockPos, leaf: LeafParticleKey, blockColor: Int, random: Random
) : AbstractParticle(
    world, pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5
) {

    companion object {
        @JvmStatic val biomeBrightnessMultiplier = 0.5f
    }

    var rotationSpeed = random.randomF(min = PI2 / 80.0, max = PI2 / 50.0)
    val isMirrored = randomB()
    var wasCollided = false

    init {
        particleAngle = random.randomF(max = PI2)
        prevParticleAngle = particleAngle - rotationSpeed

        maxAge = MathHelper.floor(randomD(0.6, 1.0) * Config.fallingLeaves.lifetime * 20.0)
        motionY = -Config.fallingLeaves.speed

        particleScale = Config.fallingLeaves.size.toFloat() * 0.1f
        setColor(leaf.overrideColor?.asInt ?: blockColor)
        sprite = LeafParticleRegistry[leaf.leafType][randomI(max = 1024)]
    }

    override val isValid: Boolean get() = (sprite != null)


    override fun update() {
        if (rand.nextFloat() > 0.95f) rotationSpeed *= -1.0f
        if (age > maxAge - 20) particleAlpha = 0.05f * (maxAge - age)

        if (onGround || wasCollided) {
            velocity.setTo(0.0, 0.0, 0.0)
            if (!wasCollided) {
                age = age.coerceAtLeast(maxAge - 20)
                wasCollided = true
            }
        } else {
            val cosRotation = cos(particleAngle).toDouble(); val sinRotation = sin(particleAngle).toDouble()
            velocity.setTo(cosRotation, 0.0, sinRotation).mul(Config.fallingLeaves.perturb)
                .add(LeafWindTracker.current).add(0.0, -1.0, 0.0).mul(Config.fallingLeaves.speed)
            prevParticleAngle = particleAngle
            particleAngle += rotationSpeed
        }
    }

    fun calculateParticleColor(textureAvgColor: Int, blockColor: Int) {
        val texture = HSB.fromColor(textureAvgColor)
        val block = HSB.fromColor(blockColor)

        val weightTex = texture.saturation / (texture.saturation + block.saturation)
        val weightBlock = 1.0f - weightTex

        // avoid circular average for hue for performance reasons
        // one of the color components should dominate anyway
        val particle = HSB(
            weightTex * texture.hue + weightBlock * block.hue,
            weightTex * texture.saturation + weightBlock * block.saturation,
            weightTex * texture.brightness + weightBlock * block.brightness * biomeBrightnessMultiplier
        )
        setColor(particle.asColor)
    }

    override fun getRenderType(): IParticleRenderType = IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
}

object LeafWindTracker {
    var random = Random()
    val target = Double3.zero
    val current = Double3.zero
    var nextChange: Long = 0

    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    fun changeWind(world: World) {
        nextChange = world.worldInfo.gameTime + 120 + random.nextInt(80)
        val direction = PI2 * random.nextDouble()
        val speed = abs(random.nextGaussian()) * Config.fallingLeaves.windStrength +
                (if (!world.isRaining) 0.0 else abs(random.nextGaussian()) * Config.fallingLeaves.stormStrength)
        target.setTo(cos(direction) * speed, 0.0, sin(direction) * speed)
    }

    @SubscribeEvent
    fun handleWorldTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) Minecraft.getInstance().world?.let { world ->
            // change target wind speed
            if (world.worldInfo.dayTime >= nextChange) changeWind(world)

            // change current wind speed
            val changeRate = if (world.isRaining) 0.015 else 0.005
            current.add(
                (target.x - current.x).minmax(-changeRate, changeRate),
                0.0,
                (target.z - current.z).minmax(-changeRate, changeRate)
            )
        }
    }

    @SubscribeEvent
    fun handleWorldLoad(event: WorldEvent.Load) { if (event.world.isRemote) changeWind(event.world.world) }
}