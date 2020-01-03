package mods.betterfoliage.client.integration

import mods.betterfoliage.BetterFoliage
import mods.betterfoliage.client.Client
import mods.betterfoliage.client.render.LogRegistry
import mods.betterfoliage.client.render.column.ColumnTextureInfo
import mods.betterfoliage.client.render.column.SimpleColumnInfo
import mods.octarinecore.client.render.CombinedContext
import mods.octarinecore.client.render.Quad
import mods.octarinecore.client.render.lighting.QuadIconResolver
import mods.octarinecore.client.render.lighting.LightingCtx
import mods.octarinecore.client.resource.*
import mods.octarinecore.common.rotate
import mods.octarinecore.metaprog.ClassRef
import mods.octarinecore.metaprog.allAvailable
import net.minecraft.block.BlockState
import net.minecraft.client.renderer.model.BlockModel
import net.minecraft.client.renderer.model.IUnbakedModel
import net.minecraft.client.renderer.model.ModelResourceLocation
import net.minecraft.client.renderer.texture.AtlasTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.Direction
import net.minecraft.util.Direction.*
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.ModList
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Level.DEBUG
import org.apache.logging.log4j.Logger

object IC2RubberIntegration {

    val BlockRubWood = ClassRef("ic2.core.block.BlockRubWood")

    init {
        if (ModList.get().isLoaded("ic2") && allAvailable(BlockRubWood)) {
            Client.log(Level.INFO, "IC2 rubber support initialized")
            LogRegistry.addRegistry(IC2LogSupport)
        }
    }
}

object TechRebornRubberIntegration {

    val BlockRubberLog = ClassRef("techreborn.blocks.BlockRubberLog")

    init {
        if (ModList.get().isLoaded("techreborn") && allAvailable(BlockRubberLog)) {
            Client.log(Level.INFO, "TechReborn rubber support initialized")
            LogRegistry.addRegistry(TechRebornLogSupport)
        }
    }
}

class RubberLogInfo(
    axis: Axis?,
    val spotDir: Direction,
    topTexture: TextureAtlasSprite,
    bottomTexture: TextureAtlasSprite,
    val spotTexture: TextureAtlasSprite,
    sideTextures: List<TextureAtlasSprite>
) : SimpleColumnInfo(axis, topTexture, bottomTexture, sideTextures) {

    override val side: QuadIconResolver = { ctx: CombinedContext, idx: Int, quad: Quad ->
        val worldFace = (if ((idx and 1) == 0) SOUTH else EAST).rotate(ctx.modelRotation)
        if (worldFace == spotDir) spotTexture else {
            val sideIdx = if (this.sideTextures.size > 1) (ctx.semiRandom(1) + dirToIdx[worldFace.ordinal]) % this.sideTextures.size else 0
            this.sideTextures[sideIdx]
        }
    }

    class Key(override val logger: Logger, val axis: Axis?, val spotDir: Direction, val textures: List<String>): ModelRenderKey<ColumnTextureInfo> {
        override fun resolveSprites(atlas: AtlasTexture) = RubberLogInfo(
            axis,
            spotDir,
            atlas[textures[0]] ?: missingSprite,
            atlas[textures[1]] ?: missingSprite,
            atlas[textures[2]] ?: missingSprite,
            textures.drop(3).map { atlas[it] ?: missingSprite }
        )
    }
}

object IC2LogSupport : ModelRenderRegistryBase<ColumnTextureInfo>() {
    override val logger = BetterFoliage.logDetail

    override fun processModel(state: BlockState, modelLoc: ModelResourceLocation, models: List<Pair<IUnbakedModel, ResourceLocation>>): ModelRenderKey<ColumnTextureInfo>? {
        // check for proper block class, existence of ModelBlock, and "state" blockstate property
        if (!IC2RubberIntegration.BlockRubWood.isInstance(state.block)) return null
        val blockLoc = models.firstOrNull() as Pair<BlockModel, ResourceLocation> ?: return null
        val type = state.values.entries.find { it.key.getName() == "state" }?.value?.toString() ?: return null

        // logs with no rubber spot
        if (blockLoc.derivesFrom(ResourceLocation("block/cube_column"))) {
            val axis = when(type) {
                "plain_y" -> Axis.Y
                "plain_x" -> Axis.X
                "plain_z" -> Axis.Z
                else -> null
            }
            val textureNames = listOf("end", "end", "side").map { blockLoc.first.resolveTextureName(it) }
            if (textureNames.any { it == "missingno" }) return null
            logger.log(DEBUG, "IC2LogSupport: block state ${state.toString()}")
            logger.log(DEBUG, "IC2LogSupport:             axis=$axis, end=${textureNames[0]}, side=${textureNames[2]}")
            return SimpleColumnInfo.Key(logger, axis, textureNames)
        }

        // logs with rubber spot
        val spotDir = when(type) {
            "dry_north", "wet_north" -> NORTH
            "dry_south", "wet_south" -> SOUTH
            "dry_west", "wet_west" -> WEST
            "dry_east", "wet_east" -> EAST
            else -> null
        }
        val textureNames = listOf("up", "down", "north", "south").map { blockLoc.first.resolveTextureName(it) }
        if (textureNames.any { it == "missingno" }) return null
        logger.log(DEBUG, "IC2LogSupport: block state ${state.toString()}")
        logger.log(DEBUG, "IC2LogSupport:             spotDir=$spotDir, up=${textureNames[0]}, down=${textureNames[1]}, side=${textureNames[2]}, spot=${textureNames[3]}")
        return if (spotDir != null) RubberLogInfo.Key(logger, Axis.Y, spotDir, textureNames) else SimpleColumnInfo.Key(logger, Axis.Y, textureNames)
    }
}

object TechRebornLogSupport : ModelRenderRegistryBase<ColumnTextureInfo>() {
    override val logger = BetterFoliage.logDetail

    override fun processModel(state: BlockState, modelLoc: ModelResourceLocation, models: List<Pair<IUnbakedModel, ResourceLocation>>): ModelRenderKey<ColumnTextureInfo>? {
        // check for proper block class, existence of ModelBlock
        if (!TechRebornRubberIntegration.BlockRubberLog.isInstance(state.block)) return null
        val blockLoc = models.firstOrNull() as Pair<BlockModel, ResourceLocation> ?: return null

        val hasSap = state.values.entries.find { it.key.getName() == "hassap" }?.value as? Boolean ?: return null
        val sapSide = state.values.entries.find { it.key.getName() == "sapside" }?.value as? Direction ?: return null

        logger.log(DEBUG, "$logName: block state $state")
        if (hasSap) {
            val textureNames = listOf("end", "end", "sapside", "side").map { blockLoc.first.resolveTextureName(it) }
            logger.log(DEBUG, "$logName:             spotDir=$sapSide, end=${textureNames[0]}, side=${textureNames[2]}, spot=${textureNames[3]}")
            if (textureNames.all { it != "missingno" }) return RubberLogInfo.Key(logger, Axis.Y, sapSide, textureNames)
        } else {
            val textureNames = listOf("end", "end", "side").map { blockLoc.first.resolveTextureName(it) }
            logger.log(DEBUG, "$logName:             end=${textureNames[0]}, side=${textureNames[2]}")
            if (textureNames.all { it != "missingno" })return SimpleColumnInfo.Key(logger, Axis.Y, textureNames)
        }
        return null
    }
}