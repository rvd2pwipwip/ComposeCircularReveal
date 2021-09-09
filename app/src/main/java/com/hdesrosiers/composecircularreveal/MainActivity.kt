package com.hdesrosiers.composecircularreveal

import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.FloatRange
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hdesrosiers.composecircularreveal.ui.theme.ComposeCircularRevealTheme
import kotlin.math.hypot

// https://dev.to/bmonjoie/jetpack-compose-reveal-effect-1fao

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
//      ComposeCircularRevealTheme {
//        // A surface container using the 'background' color from the theme
//        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
//
//        }
      var darkMode by remember { mutableStateOf(false) }
      CircularReveal(targetState = darkMode, animationSpec = tween(1000)) { localTheme ->
        ComposeCircularRevealTheme(localTheme) {
          Column(
            modifier = Modifier
              .background(MaterialTheme.colors.background)
              .padding(8.dp)
              .fillMaxSize()
          ) {
            Icon(
              if (localTheme) {
                Icons.Default.Star
              } else Icons.Default.Person,
              "Toggle",
              Modifier.clickable { darkMode = !darkMode },
              tint = MaterialTheme.colors.onBackground
            )
            Greeting("Android")
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String) {
  Surface(color = MaterialTheme.colors.background) {
    Text(text = "Hello $name!")
  }
}

// based on compose Crossfade
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> CircularReveal(
  targetState: T,
  modifier: Modifier = Modifier,
  animationSpec: FiniteAnimationSpec<Float> = tween(),
  content: @Composable CircularRevealScope.(T) -> Unit
) {
  val items = remember { mutableStateListOf<CircularRevealAnimationItem<T>>() }
  val transitionState = remember { MutableTransitionState(targetState) }
  val targetChanged = (targetState != transitionState.targetState)
  // State to remember the last input from the user
  var offset: Offset? by remember { mutableStateOf(null) }
  transitionState.targetState = targetState
  val transition = updateTransition(transitionState, label = "transition")

  if (targetChanged || items.isEmpty()) {
    // Only manipulate the list when the state is changed, or in the first run.

    // It first creates a list of keys from the items list (all the states it is currently animating/displaying)
    // and it adds the targetState if it's not in the list already
    val keys = items.map { it.key }.run {
      if (!contains(targetState)) {
        toMutableList().also { it.add(targetState) }
      } else {
        this
      }
    }

    // It removes all the saved items
    items.clear()

    // It maps the keys and store the result in the `items` variable
    keys.mapIndexedTo(items) { index, key ->
      // For each key, it creates a new CrossfadeAnimationItem which associates the key to a @Composable function.
      // The @Composable associated is a new one which holds the animation and the content for the corresponding key
      CircularRevealAnimationItem(key) {
        // It creates an animation for each state
        // As we can see, the animation is created from the transition in order to tie them together
        val progress by transition.animateFloat(
          transitionSpec = { animationSpec }, label = ""
        ) {
          if (index == keys.size - 1) {
            if (it == key) 1f else 0f
          } else 1f
        }
        // It puts our content into a Box with the animated circular reveal Modifier applied
        // using the offset from the last user input
        Box(Modifier.circularReveal(progress = progress, offset = offset)) {
          // "content" is the lambda passed to CrossFade to which it passes the key so the lambda knows how to properly display itself
          with(CircularRevealScope) {
            content(key)
          }
        }
      }
    }
  } else if (transitionState.currentState == transitionState.targetState) {
    // Remove all the intermediate items from the list once the animation is finished.
    items.removeAll { it.key != transitionState.targetState }
  }

  // Detect where was the last click from the user
  Box(modifier.pointerInteropFilter {
    offset = when (it.action) {
      MotionEvent.ACTION_DOWN -> Offset(it.x, it.y)
      else -> null
    }
    false
  }) {
    // It iterates over the items to display them
    items.forEach {
      key(it.key) {
        it.content()
      }
    }
  }
}

// helper object to hold the key with the @Composable function
private data class CircularRevealAnimationItem<T>(
  val key: T,
  val content: @Composable () -> Unit
)


fun Modifier.circularReveal(
  @FloatRange(from = 0.0, to = 1.0) progress: Float,
  offset: Offset? = null
) = clip(CircularRevealShape(progress = progress, offset = offset))

private class CircularRevealShape(
  @FloatRange(from = 0.0, to = 1.0) private val progress: Float,
  private val offset: Offset? = null
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
  ): Outline {
    return Outline.Generic(Path().apply {
      // Create a circle and put its center at the given offset
      // or in the middle of view if no offset is given
      addCircle(
        offset?.x ?: (size.width / 2f),
        offset?.y ?: (size.height / 2f),
        // the biggest value between the width and the height of the view multiplied by 2
        // made transition seem quicker or slower based on offset position
//        size.width.coerceAtLeast(size.height) * 2 * progress,
        longestDistanceToACorner(size, offset) * progress,
        Path.Direction.CW
      )
    }.asComposePath())
  }
}

private fun longestDistanceToACorner(size: Size, offset: Offset?): Float {
  if (offset == null) {
    return hypot(size.width / 2f, size.height / 2f)
  }

  val topLeft = hypot(offset.x, offset.y)
  val topRight = hypot(size.width - offset.x, offset.y)
  val bottomLeft = hypot(offset.x, size.height - offset.y)
  val bottomRight = hypot(size.width - offset.x, size.height - offset.y)
  // return the biggest value
  return topLeft.coerceAtLeast(topRight).coerceAtLeast(bottomLeft).coerceAtLeast(bottomRight)
}

@LayoutScopeMarker
@Immutable
object CircularRevealScope