package com.example.memgallery.ui.design

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** The expressive shape library used for avatars, crops, selected states, empty states and the FAB. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object MemShape {
    val cookie: Shape @Composable get() = MaterialShapes.Cookie9Sided.toShape()
    val clover: Shape @Composable get() = MaterialShapes.Clover4Leaf.toShape()
    val softBurst: Shape @Composable get() = MaterialShapes.SoftBurst.toShape()
    val sunny: Shape @Composable get() = MaterialShapes.Sunny.toShape()
    val puffy: Shape @Composable get() = MaterialShapes.Puffy.toShape()
    val gem: Shape @Composable get() = MaterialShapes.Gem.toShape()
    val arch: Shape @Composable get() = MaterialShapes.Arch.toShape()
    val pill: Shape @Composable get() = MaterialShapes.Pill.toShape()
    val flower: Shape @Composable get() = MaterialShapes.Flower.toShape()
    val pentagon: Shape @Composable get() = MaterialShapes.Pentagon.toShape()
}
