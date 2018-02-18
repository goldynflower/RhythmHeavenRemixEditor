package io.github.chrislo27.rhre3.editor.stage

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.github.chrislo27.rhre3.PreferenceKeys
import io.github.chrislo27.rhre3.RHRE3
import io.github.chrislo27.rhre3.editor.ClickOccupation
import io.github.chrislo27.rhre3.editor.Editor
import io.github.chrislo27.rhre3.entity.Entity
import io.github.chrislo27.rhre3.entity.model.ModelEntity
import io.github.chrislo27.rhre3.screen.EditorScreen
import io.github.chrislo27.rhre3.track.PlayState
import io.github.chrislo27.rhre3.track.Remix
import io.github.chrislo27.toolboks.ui.Stage
import io.github.chrislo27.toolboks.ui.UIElement
import io.github.chrislo27.toolboks.ui.UIPalette
import io.github.chrislo27.toolboks.util.gdxutils.*
import kotlin.math.roundToInt


class Minimap(val editor: Editor, palette: UIPalette, parent: UIElement<EditorScreen>,
              stage: Stage<EditorScreen>)
    : UIElement<EditorScreen>(parent, stage) {

    private val remix: Remix
        get() = editor.remix
    private var timeHovered: Float = 0f
    private lateinit var buffer: FrameBuffer
    var bufferSupported: Boolean = true
        private set

    init {
        try {
            buffer = FrameBuffer(Pixmap.Format.RGBA8888, RHRE3.WIDTH, RHRE3.HEIGHT, false, true)

            editor.main.addDisposeCall(Runnable(buffer::dispose))
        } catch (e: Exception) {
            e.printStackTrace()
            bufferSupported = false
        }
    }

    override fun render(screen: EditorScreen, batch: SpriteBatch, shapeRenderer: ShapeRenderer) {
        val furthest: Entity? = remix.entities.maxBy { it.bounds.x + it.bounds.width }
        val maxX: Float = if (furthest == null) remix.camera.viewportWidth else Math.min(
                furthest.bounds.x + furthest.bounds.width, remix.duration)

        shapeRenderer.prepareStencilMask(batch) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            begin(ShapeRenderer.ShapeType.Filled)
            rect(location.realX - 1, location.realY, location.realWidth + 1, location.realHeight)
            end()
        }.useStencilMask {
            val x = location.realX
            val y = location.realY
            val pxHeight = location.realHeight
            val pxWidth = location.realWidth
            val unitHeight = pxHeight / Editor.TRACK_COUNT
            val unitWidth = pxWidth / maxX

            // entities
            remix.entities.forEach { entity ->
                if (entity !is ModelEntity<*>)
                    return@forEach

                batch.color = entity.getRenderColor()
                batch.fillRect(x + entity.bounds.x * unitWidth, y + entity.bounds.y * unitHeight,
                               entity.bounds.width * unitWidth, unitHeight)
            }

            // horizontal lines
            batch.color = editor.theme.trackLine
            for (i in 1..Editor.TRACK_COUNT) {
                batch.fillRect(x, y + i * unitHeight - 0.5f, pxWidth, 1f)
            }
            batch.fillRect(x - 0.5f, y, 1f, pxHeight)
            batch.fillRect(x + pxWidth - 0.5f, y, 1f, pxHeight)
            batch.setColor(1f, 1f, 1f, 1f)

            // trackers
            editor.remix.trackers.forEach { tc ->
                tc.map.values.forEach { tracker ->
                    batch.color = tracker.getColour(editor.theme)
                    batch.fillRect(x + tracker.beat * unitWidth - 0.5f, y, 1f, pxHeight)
                }
            }
            run {
                batch.color = editor.theme.trackers.musicStart
                batch.fillRect(x + remix.tempos.secondsToBeats(remix.musicStartSec) * unitWidth - 0.5f, y, 1f, pxHeight)
                val playback = if (remix.playState == PlayState.STOPPED) remix.playbackStart else remix.beat
                batch.color = editor.theme.trackers.playback
                batch.fillRect(x + playback * unitWidth - 0.5f, y, 1f, pxHeight)
            }

            // camera box
            batch.setColor(1f, 1f, 1f, 1f)
            val camera = editor.camera
            batch.drawRect(x + (camera.position.x - camera.viewportWidth / 2) * unitWidth, y,
                           camera.viewportWidth * unitWidth, pxHeight, 2f)

            if (isMouseOver() && editor.clickOccupation == ClickOccupation.None
                    && remix.playState == PlayState.STOPPED
                    && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                val percent = (stage.camera.getInputX() - location.realX) / location.realWidth
                editor.camera.position.x = percent * maxX
            }
        }

        // Preview
        if (timeHovered >= 1f && bufferSupported && editor.main.preferences.getBoolean(PreferenceKeys.SETTINGS_MINIMAP_PREVIEW, true)) {
            val percent = (stage.camera.getInputX() - location.realX) / location.realWidth
            val centreX = percent * maxX
            val oldCamX = editor.camera.position.x
            val oldCamZoom = editor.camera.zoom

            batch.end()
            buffer.begin()

            editor.camera.position.x = centreX
            editor.camera.zoom = 1f
            editor.camera.update()
            editor.render((centreX - buffer.width / 2).toInt()..(centreX + buffer.width / 2).toInt(),
                          adjustCamera = false, otherUI = false)
            editor.camera.position.x = oldCamX
            editor.camera.zoom = oldCamZoom
            editor.camera.update()

            buffer.end()
            batch.begin()

            val bufSecH = (buffer.height * 0.75f).roundToInt()
            val h = location.realWidth * (bufSecH.toFloat() / buffer.width)
            batch.color = editor.theme.background
            batch.fillRect(location.realX, location.realY + location.realHeight,
                           location.realWidth, h)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(buffer.colorBufferTexture,
                       location.realX, location.realY + location.realHeight,
                       location.realWidth, h,
                       0, buffer.height - bufSecH, buffer.width, bufSecH, false, true)
        }
    }

    override fun frameUpdate(screen: EditorScreen) {
        super.frameUpdate(screen)
        this.visible = !screen.main.preferences.getBoolean(PreferenceKeys.SETTINGS_MINIMAP, false)
        if (isMouseOver()) {
            timeHovered += Gdx.graphics.deltaTime
        } else {
            timeHovered = 0f
        }
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (isMouseOver() && button == Input.Buttons.LEFT) {
            return true
        }

        return super.touchDown(screenX, screenY, pointer, button)
    }
}