@file:JvmName("Utils")
package mods.octarinecore.client.resource

import mods.betterfoliage.loader.Refs
import mods.octarinecore.PI2
import mods.octarinecore.client.render.HSB
import mods.octarinecore.stripStart
import mods.octarinecore.tryDefault
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.ModelBlock
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.resources.IResource
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.SimpleReloadableResourceManager
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.model.IModel
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.Math.*
import javax.imageio.ImageIO

/** Concise getter for the Minecraft resource manager. */
val resourceManager: SimpleReloadableResourceManager get() =
    Minecraft.getMinecraft().resourceManager as SimpleReloadableResourceManager

/** Append a string to the [ResourceLocation]'s path. */
operator fun ResourceLocation.plus(str: String) = ResourceLocation(resourceDomain, resourcePath + str)

/** Index operator to get a resource. */
operator fun IResourceManager.get(domain: String, path: String): IResource? = get(ResourceLocation(domain, path))
/** Index operator to get a resource. */
operator fun IResourceManager.get(location: ResourceLocation): IResource? = tryDefault(null) { getResource(location) }

/** Index operator to get a texture sprite. */
operator fun TextureMap.get(name: String): TextureAtlasSprite? = getTextureExtry(ResourceLocation(name).toString())

/** Load an image resource. */
fun IResource.loadImage() = ImageIO.read(this.inputStream)

/** Get the lines of a text resource. */
fun IResource.getLines(): List<String> {
    val result = arrayListOf<String>()
    inputStream.bufferedReader().useLines { it.forEach { result.add(it) } }
    return result
}

/** Index operator to get the RGB value of a pixel. */
operator fun BufferedImage.get(x: Int, y: Int) = this.getRGB(x, y)
/** Index operator to set the RGB value of a pixel. */
operator fun BufferedImage.set(x: Int, y: Int, value: Int) = this.setRGB(x, y, value)

/** Get an [InputStream] to an image object in PNG format. */
val BufferedImage.asStream: InputStream get() =
    ByteArrayInputStream(ByteArrayOutputStream().let { ImageIO.write(this, "PNG", it); it.toByteArray() })

/**
 * Calculate the average color of a texture.
 *
 * Only non-transparent pixels are considered. Averages are taken in the HSB color space (note: Hue is a circular average),
 * and the result transformed back to the RGB color space.
 */
val TextureAtlasSprite.averageColor: Int? get() {
    val locationNoDirs = ResourceLocation(iconName)
    val locationWithDirs = ResourceLocation(locationNoDirs.resourceDomain, "textures/blocks/%s.png".format(locationNoDirs.resourcePath))
    val image = resourceManager[locationWithDirs]?.loadImage() ?: return null

    var numOpaque = 0
    var sumHueX = 0.0
    var sumHueY = 0.0
    var sumSaturation = 0.0f
    var sumBrightness = 0.0f
    for (x in 0..image.width - 1)
        for (y in 0..image.height - 1) {
            val pixel = image[x, y]
            val alpha = (pixel shr 24) and 255
            val hsb = HSB.fromColor(pixel)
            if (alpha == 255) {
                numOpaque++
                sumHueX += cos((hsb.hue.toDouble() - 0.5) * PI2)
                sumHueY += sin((hsb.hue.toDouble() - 0.5) * PI2)
                sumSaturation += hsb.saturation
                sumBrightness += hsb.brightness
            }
        }

    // circular average - transform sum vector to polar angle
    val avgHue = (atan2(sumHueY.toDouble(), sumHueX.toDouble()) / PI2 + 0.5).toFloat()
    return HSB(avgHue, sumSaturation / numOpaque.toFloat(), sumBrightness / numOpaque.toFloat()).asColor
}

/**
 * Get the actual location of a texture from the name of its [TextureAtlasSprite].
 */
fun textureLocation(iconName: String) = ResourceLocation(iconName).let {
    if (it.resourcePath.startsWith("mcpatcher")) it
    else ResourceLocation(it.resourceDomain, "textures/${it.resourcePath}")
}

@Suppress("UNCHECKED_CAST")
val IModel.modelBlockAndLoc: Pair<ModelBlock, ResourceLocation>? get() {
    if (Refs.VanillaModelWrapper.isInstance(this))
        return Pair(Refs.model_VMW.get(this) as ModelBlock, Refs.location_VMW.get(this) as ResourceLocation)
    else if (Refs.WeightedRandomModel.isInstance(this)) Refs.models_WRM.get(this)?.let {
        (it as List<IModel>).forEach {
            it.modelBlockAndLoc.let { if (it != null) return it }
        }
    }
    else if (Refs.MultiModel.isInstance(this)) Refs.base_MM.get(this)?.let {
        return (it as IModel).modelBlockAndLoc
    }
    // TODO support net.minecraftforge.client.model.ModelLoader.MultipartModel
    return null
}

fun Pair<ModelBlock, ResourceLocation>.derivesFrom(targetLocation: ResourceLocation): Boolean {
    if (second.stripStart("models/") == targetLocation) return true
    if (first.parent != null && first.parentLocation != null)
        return Pair(first.parent, first.parentLocation!!).derivesFrom(targetLocation)
    return false
}

fun IModel.derivesFromModel(modelLoc: String) = modelBlockAndLoc?.derivesFrom(ResourceLocation(modelLoc)) ?: false
