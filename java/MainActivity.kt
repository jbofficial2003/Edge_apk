package com.jahid.edge

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.jahid.edge.ui.theme.EdgeTheme
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.OutputStream
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.Canvas
import android.view.Window
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.caverock.androidsvg.SVG

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hidestatusbar(window)
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_SHORT).show()
        }

        setContent {
            EdgeTheme {
                EdgeApp(this)
            }
        }
    }
}
fun hidestatusbar(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.statusBars())
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}


@Composable
fun EdgeApp(activity: ComponentActivity) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var edgePoints by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val stream = activity.contentResolver.openInputStream(it)
                bitmap = BitmapFactory.decodeStream(stream)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colorResource(R.color.grey_background),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { launcher.launch("image/*") },
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.light_grey))
                    ) {
                        Text("Pick Image")
                    }
                }
            }

        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Edge Detection",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(24.dp))

                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(4.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            edgePoints = detectEdges(bmp)
                            val svg = generateSVG(edgePoints, bmp.width, bmp.height)
                            saveSvgToDownloads(activity, svg, "edge_output")
                            convertSvgToPngWithDpi(activity, svg, "edge_output")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Detect Edges and Save SVG + PNG")
                    }
                }
            }
        }
    }
}




fun detectEdges(bitmap: Bitmap): List<Pair<Int, Int>> {
    val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
    val tmp = Mat()
    Utils.bitmapToMat(bitmap, tmp)
    Imgproc.cvtColor(tmp, mat, Imgproc.COLOR_BGR2GRAY)
    Imgproc.Canny(mat, mat, 100.0, 200.0)

    val points = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until mat.rows()) {
        for (j in 0 until mat.cols()) {
            if (mat.get(i, j)[0] > 0) {
                points.add(Pair(j, i))
            }
        }
    }
    return points
}

fun generateSVG(edgePoints: List<Pair<Int, Int>>, width: Int, height: Int): String {
    val builder = StringBuilder()
    builder.append(
        """
        <svg xmlns="http://www.w3.org/2000/svg" 
             width="${width}" height="${height}" 
             viewBox="0 0 $width $height" 
             version="1.1">
          <rect width="100%" height="100%" fill="black"/>
    """.trimIndent()
    )

    for ((x, y) in edgePoints) {
        builder.append("<rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"white\"/>")
    }

    builder.append("</svg>")
    return builder.toString()
}


fun saveSvgToDownloads(context: ComponentActivity, svgContent: String, fileName: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.svg")
        put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val itemUri: Uri? = resolver.insert(collection, contentValues)

    itemUri?.let { uri ->
        resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
            outputStream.write(svgContent.toByteArray())
            outputStream.flush()
        }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        Toast.makeText(context, "SVG saved to Downloads", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save SVG", Toast.LENGTH_SHORT).show()
    }
}




fun convertSvgToPngWithDpi(context: ComponentActivity, svgContent: String, fileName: String, targetDpi: Int = 72) {
    try {
        val svg = SVG.getFromString(svgContent)

        val svgWidthInInches = svg.documentWidth / 96f // AndroidSVG assumes 96 DPI
        val svgHeightInInches = svg.documentHeight / 96f

        if (svgWidthInInches <= 0 || svgHeightInInches <= 0) {
            Toast.makeText(context, "SVG has invalid dimensions", Toast.LENGTH_SHORT).show()
            return
        }

        // Target bitmap dimensions based on desired DPI
        val targetWidthPx = (svgWidthInInches * targetDpi).toInt()
        val targetHeightPx = (svgHeightInInches * targetDpi).toInt()

        // Set SVG internal size
        svg.setDocumentWidth(svg.documentWidth)
        svg.setDocumentHeight(svg.documentHeight)

        // Create high-res bitmap and scale canvas
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scaleX = targetWidthPx / svg.documentWidth
        val scaleY = targetHeightPx / svg.documentHeight
        canvas.scale(scaleX, scaleY)

        svg.renderToCanvas(canvas)

        // Save to Downloads
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Toast.makeText(context, "High-DPI PNG saved", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "Failed to save PNG", Toast.LENGTH_SHORT).show()
        }

    } catch (e: Exception) {
        Toast.makeText(context, "Error during SVG → PNG", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
