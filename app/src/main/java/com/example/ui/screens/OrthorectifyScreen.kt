package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ocr.DocFilter
import com.example.ocr.DocumentQuad
import com.example.ocr.ImageProcessing
import com.example.ocr.PerspectiveCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun OrthorectifyScreen(
    sourceBitmap: Bitmap,
    pageNumber: Int,
    onBack: () -> Unit,
    onConfirmStraightened: (Bitmap, DocFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val imgWidth = sourceBitmap.width.toFloat()
    val imgHeight = sourceBitmap.height.toFloat()

    // Default to the full image with a tiny 1% inset so all corners are immediately at the edges
    var quad by remember {
        mutableStateOf(PerspectiveCorrection.fullImageQuad(imgWidth, imgHeight))
    }

    var selectedFilter by remember { mutableStateOf(DocFilter.BW_DOCUMENT) }
    var currentTab by remember { mutableIntStateOf(0) } // 0: Corner Adjust, 1: Filter Preview
    var activeDraggingCorner by remember { mutableIntStateOf(-1) } // 0: TL, 1: TR, 2: BR, 3: BL

    // Live filter preview state
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(currentTab, quad, selectedFilter) {
        if (currentTab == 1) {
            isGeneratingPreview = true
            withContext(Dispatchers.Default) {
                val rectified = PerspectiveCorrection.orthorectify(sourceBitmap, quad)
                val filtered = ImageProcessing.applyFilter(rectified, selectedFilter)
                previewBitmap = filtered
            }
            isGeneratingPreview = false
        }
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding + 6.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .testTag("btn_ortho_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Annulla",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ortorettifica Foglio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Pagina $pageNumber • ${if (currentTab == 0) "Regola 4 Angoli" else "Anteprima Filtri"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Full screen quad button in top bar
                IconButton(
                    onClick = {
                        quad = PerspectiveCorrection.fullImageQuad(imgWidth, imgHeight)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .testTag("btn_reset_full_screen")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Tutta l'immagine",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            // Mode Selector Tabs (Regola Angoli vs Anteprima Filtro)
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = Color(0xFF131A29),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("1. Angoli & Area", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("2. Anteprima Filtro", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (currentTab == 0) {
                // Tab 0: Corner Adjustment with EXACT pixel mapping across the whole screen
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val viewWidthPx = constraints.maxWidth.toFloat()
                    val viewHeightPx = constraints.maxHeight.toFloat()

                    // Pixel-accurate fitCenter scale
                    val scale = minOf(viewWidthPx / imgWidth, viewHeightPx / imgHeight)
                    val drawWPx = imgWidth * scale
                    val drawHPx = imgHeight * scale
                    val drawLeftPx = (viewWidthPx - drawWPx) / 2f
                    val drawTopPx = (viewHeightPx - drawHPx) / 2f

                    fun toScreen(pt: PointF): Offset {
                        return Offset(drawLeftPx + pt.x * scale, drawTopPx + pt.y * scale)
                    }

                    fun toImage(screenOffset: Offset): PointF {
                        val ix = ((screenOffset.x - drawLeftPx) / scale).coerceIn(0f, imgWidth)
                        val iy = ((screenOffset.y - drawTopPx) / scale).coerceIn(0f, imgHeight)
                        return PointF(ix, iy)
                    }

                    val tlScreen = toScreen(quad.topLeft)
                    val trScreen = toScreen(quad.topRight)
                    val brScreen = toScreen(quad.bottomRight)
                    val blScreen = toScreen(quad.bottomLeft)

                    val activeScreenPt = when (activeDraggingCorner) {
                        0 -> tlScreen
                        1 -> trScreen
                        2 -> brScreen
                        3 -> blScreen
                        else -> null
                    }
                    val activeImgPt = when (activeDraggingCorner) {
                        0 -> quad.topLeft
                        1 -> quad.topRight
                        2 -> quad.bottomRight
                        3 -> quad.bottomLeft
                        else -> null
                    }
                    val cornerLabel = when (activeDraggingCorner) {
                        0 -> "Alto SX"
                        1 -> "Alto DX"
                        2 -> "Basso DX"
                        3 -> "Basso SX"
                        else -> ""
                    }

                    val loupeDiameterDp = 126.dp
                    val loupeDiameterPx = with(density) { loupeDiameterDp.toPx() }
                    val loupeRadiusPx = loupeDiameterPx / 2f
                    val fingerClearancePx = with(density) { 56.dp.toPx() }

                    var clampedCenterX = 0f
                    var clampedCenterY = 0f
                    var loupeLeftPx = 0f
                    var loupeTopPx = 0f

                    if (activeScreenPt != null) {
                        // Check if placing above finger stays inside view boundaries
                        val spaceAbove = activeScreenPt.y - fingerClearancePx - loupeDiameterPx
                        val canPlaceAbove = spaceAbove >= 8f

                        val preferredCenterY = if (canPlaceAbove) {
                            // Comfortably above finger so the touch point is fully visible
                            activeScreenPt.y - fingerClearancePx - loupeRadiusPx
                        } else {
                            // Near top of screen: place below finger
                            activeScreenPt.y + fingerClearancePx + loupeRadiusPx
                        }

                        val preferredCenterX = when {
                            // Near left edge: push to right of the finger
                            activeScreenPt.x < loupeDiameterPx -> {
                                activeScreenPt.x + loupeRadiusPx + with(density) { 24.dp.toPx() }
                            }
                            // Near right edge: push to left of the finger
                            activeScreenPt.x > viewWidthPx - loupeDiameterPx -> {
                                activeScreenPt.x - loupeRadiusPx - with(density) { 24.dp.toPx() }
                            }
                            else -> {
                                if (!canPlaceAbove) {
                                    // When below finger, offset away from user hand
                                    if (activeDraggingCorner == 0 || activeDraggingCorner == 3) {
                                        activeScreenPt.x + loupeRadiusPx + with(density) { 20.dp.toPx() }
                                    } else {
                                        activeScreenPt.x - loupeRadiusPx - with(density) { 20.dp.toPx() }
                                    }
                                } else {
                                    activeScreenPt.x
                                }
                            }
                        }

                        clampedCenterX = preferredCenterX.coerceIn(loupeRadiusPx + 8f, viewWidthPx - loupeRadiusPx - 8f)
                        clampedCenterY = preferredCenterY.coerceIn(loupeRadiusPx + 8f, viewHeightPx - loupeRadiusPx - 8f)
                        loupeLeftPx = clampedCenterX - loupeRadiusPx
                        loupeTopPx = clampedCenterY - loupeRadiusPx
                    }

                    val drawWDp = with(density) { drawWPx.toDp() }
                    val drawHDp = with(density) { drawHPx.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(sourceBitmap) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val distTL = hypot(startOffset.x - tlScreen.x, startOffset.y - tlScreen.y)
                                        val distTR = hypot(startOffset.x - trScreen.x, startOffset.y - trScreen.y)
                                        val distBR = hypot(startOffset.x - brScreen.x, startOffset.y - brScreen.y)
                                        val distBL = hypot(startOffset.x - blScreen.x, startOffset.y - blScreen.y)

                                        val minDist = minOf(distTL, distTR, distBR, distBL)
                                        activeDraggingCorner = when (minDist) {
                                            distTL -> 0
                                            distTR -> 1
                                            distBR -> 2
                                            else -> 3
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        if (activeDraggingCorner != -1) {
                                            change.consume()
                                            val newImgPt = toImage(change.position)
                                            quad = when (activeDraggingCorner) {
                                                0 -> quad.copy(topLeft = newImgPt)
                                                1 -> quad.copy(topRight = newImgPt)
                                                2 -> quad.copy(bottomRight = newImgPt)
                                                3 -> quad.copy(bottomLeft = newImgPt)
                                                else -> quad
                                            }
                                        }
                                    },
                                    onDragEnd = { activeDraggingCorner = -1 },
                                    onDragCancel = { activeDraggingCorner = -1 }
                                )
                            }
                    ) {
                        // Displayed capture bitmap centered in box
                        Image(
                            bitmap = sourceBitmap.asImageBitmap(),
                            contentDescription = "Cattura fotocamera",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(drawWDp, drawHDp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        // Precision Polygon & Interactive Corner Pins Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val polygonPath = Path().apply {
                                moveTo(tlScreen.x, tlScreen.y)
                                lineTo(trScreen.x, trScreen.y)
                                lineTo(brScreen.x, brScreen.y)
                                lineTo(blScreen.x, blScreen.y)
                                close()
                            }

                            // Quad outline
                            drawPath(
                                path = polygonPath,
                                color = Color(0xFF00E5FF),
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // Inner alignment crosshairs
                            val midTop = Offset((tlScreen.x + trScreen.x) / 2, (tlScreen.y + trScreen.y) / 2)
                            val midBottom = Offset((blScreen.x + brScreen.x) / 2, (blScreen.y + brScreen.y) / 2)
                            val midLeft = Offset((tlScreen.x + blScreen.x) / 2, (tlScreen.y + blScreen.y) / 2)
                            val midRight = Offset((trScreen.x + brScreen.x) / 2, (trScreen.y + brScreen.y) / 2)

                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                                start = midTop,
                                end = midBottom,
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                                start = midLeft,
                                end = midRight,
                                strokeWidth = 1.dp.toPx()
                            )

                            // Connecting guide line between active pin and magnifying loupe
                            if (activeScreenPt != null) {
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                                    start = activeScreenPt,
                                    end = Offset(clampedCenterX, clampedCenterY),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(
                                    color = Color(0xFF00E5FF),
                                    radius = 4.dp.toPx(),
                                    center = activeScreenPt
                                )
                            }

                            // 4 Corner Control Handles
                            val corners = listOf(tlScreen, trScreen, brScreen, blScreen)
                            corners.forEachIndexed { idx, pt ->
                                val isBeingDragged = idx == activeDraggingCorner
                                val pinRadius = if (isBeingDragged) 18.dp.toPx() else 14.dp.toPx()

                                drawCircle(
                                    color = if (isBeingDragged) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.4f),
                                    radius = pinRadius + 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = pinRadius,
                                    center = pt
                                )
                                drawCircle(
                                    color = if (isBeingDragged) Color(0xFF00E5FF) else Color(0xFF0284C7),
                                    radius = pinRadius - 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        // Floating Magnifying Loupe above/beside dragging finger
                        if (activeDraggingCorner != -1 && activeScreenPt != null && activeImgPt != null) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = loupeLeftPx.roundToInt(),
                                            y = loupeTopPx.roundToInt()
                                        )
                                    }
                                    .size(loupeDiameterDp)
                                    .shadow(elevation = 16.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(0xFF090D16))
                                    .border(3.dp, Color(0xFF00E5FF), CircleShape)
                            ) {
                                // High magnification canvas
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val circlePath = Path().apply {
                                        addOval(Rect(0f, 0f, size.width, size.height))
                                    }

                                    clipPath(circlePath) {
                                        // Background behind bitmap
                                        drawRect(color = Color(0xFF1E293B))

                                        // High magnification factor relative to screen
                                        val zoomScale = maxOf(scale * 3.0f, 1.6f)

                                        drawIntoCanvas { canvas ->
                                            canvas.save()
                                            canvas.translate(cx, cy)
                                            canvas.scale(zoomScale, zoomScale)
                                            canvas.translate(-activeImgPt.x, -activeImgPt.y)

                                            val paint = Paint().apply {
                                                isFilterBitmap = true
                                                isAntiAlias = true
                                            }
                                            canvas.nativeCanvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
                                            canvas.restore()
                                        }

                                        // Precision Alignment Grid
                                        val gridStep = 18.dp.toPx()
                                        for (i in -2..2) {
                                            if (i != 0) {
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    start = Offset(cx + i * gridStep, 0f),
                                                    end = Offset(cx + i * gridStep, size.height),
                                                    strokeWidth = 1f
                                                )
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    start = Offset(0f, cy + i * gridStep),
                                                    end = Offset(size.width, cy + i * gridStep),
                                                    strokeWidth = 1f
                                                )
                                            }
                                        }

                                        // Crosshair Reticle (High Contrast Dark Shadow + Neon Cyan Foreground)
                                        val armLength = 22.dp.toPx()
                                        val reticleRadius = 6.dp.toPx()

                                        // Dark contrast shadows
                                        val shadowColor = Color.Black.copy(alpha = 0.75f)
                                        val shadowStroke = 3.dp.toPx()
                                        drawLine(shadowColor, Offset(cx - armLength, cy), Offset(cx - reticleRadius, cy), shadowStroke)
                                        drawLine(shadowColor, Offset(cx + reticleRadius, cy), Offset(cx + armLength, cy), shadowStroke)
                                        drawLine(shadowColor, Offset(cx, cy - armLength), Offset(cx, cy - reticleRadius), shadowStroke)
                                        drawLine(shadowColor, Offset(cx, cy + reticleRadius), Offset(cx, cy + armLength), shadowStroke)
                                        drawCircle(shadowColor, radius = reticleRadius, center = Offset(cx, cy), style = Stroke(width = shadowStroke))

                                        // Cyan neon precision lines
                                        val cyanNeon = Color(0xFF00E5FF)
                                        val neonStroke = 1.8.dp.toPx()
                                        drawLine(cyanNeon, Offset(cx - armLength, cy), Offset(cx - reticleRadius, cy), neonStroke)
                                        drawLine(cyanNeon, Offset(cx + reticleRadius, cy), Offset(cx + armLength, cy), neonStroke)
                                        drawLine(cyanNeon, Offset(cx, cy - armLength), Offset(cx, cy - reticleRadius), neonStroke)
                                        drawLine(cyanNeon, Offset(cx, cy + reticleRadius), Offset(cx, cy + armLength), neonStroke)
                                        drawCircle(cyanNeon, radius = reticleRadius, center = Offset(cx, cy), style = Stroke(width = neonStroke))

                                        // Precision target center dot
                                        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))

                                        // Inner lens glass reflection highlight
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.4f),
                                            radius = size.width / 2f - 4.dp.toPx(),
                                            center = Offset(cx, cy),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }

                                // Top Header Tag (Corner name + 3x Zoom badge)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$cornerLabel • Zoom 3x",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Bottom Coordinate Tag (Exact pixel position)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 7.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${activeImgPt.x.roundToInt()} × ${activeImgPt.y.roundToInt()} px",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Adjustment Buttons (Spread equally across the full screen width)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { quad = PerspectiveCorrection.fullImageQuad(imgWidth, imgHeight) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_select_full_image"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tutta l'Area", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { quad = PerspectiveCorrection.autoDetectQuad(sourceBitmap) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_auto_border"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto Bordo", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { quad = PerspectiveCorrection.defaultQuad(imgWidth, imgHeight, 0.02f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_reset_quad"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset 2%", fontSize = 11.sp, maxLines = 1)
                    }
                }
            } else {
                // Tab 1: Live Filter Preview of Rectified Result
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isGeneratingPreview || previewBitmap == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Generazione anteprima raddrizzata...", color = Color.LightGray, fontSize = 13.sp)
                        }
                    } else {
                        // Display the straightened and filtered document preview
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(14.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Anteprima documento ortorettificato",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Filter Enhancement Selector (Always visible to switch filters in real-time)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Scegli Filtro Documento:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocFilter.entries.forEach { filter ->
                        val isSelected = filter == selectedFilter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f))
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.LightGray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPadding + 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentTab == 0) {
                    // Preview Button
                    OutlinedButton(
                        onClick = { currentTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("btn_show_filter_preview"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vedi Anteprima", fontSize = 13.sp)
                    }

                    // Direct Confirm Button
                    Button(
                        onClick = {
                            val rectified = PerspectiveCorrection.orthorectify(sourceBitmap, quad)
                            onConfirmStraightened(rectified, selectedFilter)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(54.dp)
                            .testTag("btn_confirm_orthorectify"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Raddrizza & Salva", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Back to Corners
                    OutlinedButton(
                        onClick = { currentTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Modifica Angoli", fontSize = 13.sp)
                    }

                    // Confirm from Preview
                    Button(
                        onClick = {
                            val rectified = PerspectiveCorrection.orthorectify(sourceBitmap, quad)
                            onConfirmStraightened(rectified, selectedFilter)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(54.dp)
                            .testTag("btn_confirm_from_preview"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conferma Documento", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
