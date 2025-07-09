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
import org.opencv.core.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeApp(activity: ComponentActivity) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val stream = activity.contentResolver.openInputStream(it)
                bitmap = BitmapFactory.decodeStream(stream)
            }
        }

        Scaffold(
            topBar = @androidx.compose.runtime.Composable {
                TopAppBar(
                    title = {
                        Row (modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.End
                        ){
                            Text("Edge Detection")
                        }
                    },

                    colors = TopAppBarDefaults.topAppBarColors(colorResource(R.color.dark))
                )

            },
            containerColor = colorResource(R.color.grey_background),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val svg = generateSVGFromContours(bmp, bmp.width, bmp.height)
                                saveSvgToDownloads(activity, svg, "edge_output")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Save svg")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                val svg = generateSVGFromContours(bmp, bmp.width, bmp.height)
                                convertSvgToPngWithDpi(activity, svg, "edge_output")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Save PNG")
                        }
                    }
                }
            }
        }
    }
}

// --- Edge detection and SVG generation using contours ---
fun generateSVGFromContours(bitmap: Bitmap, width: Int, height: Int): String {
    // Convert Bitmap to Mat
    val src = Mat()
    Utils.bitmapToMat(bitmap, src)

    // Convert to grayscale
    val gray = Mat()
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

    // Blur to reduce noise
    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

    // Canny edge detection
    val edges = Mat()
    Imgproc.Canny(blurred, edges, 80.0, 150.0) // You can tweak these thresholds

    // Find contours
    val contours = mutableListOf<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

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

    for (contour in contours) {
        builder.append("<polyline points=\"")
        for (point in contour.toArray()) {
            builder.append("${point.x},${point.y} ")
        }
        builder.append("\" style=\"fill:none;stroke:white;stroke-width:1\"/>\n")
    }

    builder.append("</svg>")
    return builder.toString()
}

// --- SVG and PNG saving functions (unchanged) ---
fun saveSvgToDownloads(context: ComponentActivity, svgContent: String, fileName: String) {
    try {
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
    } catch (e: Exception) {
        Toast.makeText(context, "SVG save error: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

fun convertSvgToPngWithDpi(context: ComponentActivity, svgContent: String, fileName: String, targetDpi: Int = 72) {
    try {
        val svg = SVG.getFromString(svgContent)

        val svgWidth = if (svg.documentWidth > 0) svg.documentWidth else 512f
        val svgHeight = if (svg.documentHeight > 0) svg.documentHeight else 512f

        val svgWidthInInches = svgWidth / 96f // AndroidSVG assumes 96 DPI
        val svgHeightInInches = svgHeight / 96f

        val targetWidthPx = (svgWidthInInches * targetDpi).toInt()
        val targetHeightPx = (svgHeightInInches * targetDpi).toInt()

        svg.setDocumentWidth(svgWidth)
        svg.setDocumentHeight(svgHeight)

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scaleX = targetWidthPx / svgWidth
        val scaleY = targetHeightPx / svgHeight
        canvas.scale(scaleX, scaleY)

        svg.renderToCanvas(canvas)

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
        Toast.makeText(context, "Error during SVG → PNG: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
