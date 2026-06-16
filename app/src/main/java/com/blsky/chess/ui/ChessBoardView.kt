package com.blsky.chess.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.blsky.chess.data.Move
import com.blsky.chess.data.Square
import com.blsky.chess.engine.*

class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var position: ChessPosition? = null
        set(value) {
            field = value
            selectedSquare = null
            legalMoveSquares = emptySet()
            invalidate()
        }

    var playerColor: String = "white" // "white" or "black"
    var onMoveSelected: ((Move) -> Unit)? = null
    var interactive: Boolean = true

    private var selectedSquare: Square? = null
    private var legalMoveSquares: Set<Square> = emptySet()
    private var lastMove: Pair<Square, Square>? = null

    private val lightSquarePaint = Paint().apply { color = Color.parseColor("#F0D9B5") }
    private val darkSquarePaint = Paint().apply { color = Color.parseColor("#B58863") }
    private val selectedPaint = Paint().apply { color = Color.parseColor("#7FFF0000"); style = Paint.Style.FILL }
    private val legalMovePaint = Paint().apply { color = Color.parseColor("#7F00FF00"); style = Paint.Style.FILL }
    private val lastMovePaint = Paint().apply { color = Color.parseColor("#7FFFFF00"); style = Paint.Style.FILL }
    private val coordinatePaint = Paint().apply {
        color = Color.parseColor("#666666")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val piecePaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

    private val pieceSymbols = mapOf(
        W_PAWN to "♙", W_KNIGHT to "♘", W_BISHOP to "♗",
        W_ROOK to "♖", W_QUEEN to "♕", W_KING to "♔",
        B_PAWN to "♟", B_KNIGHT to "♞", B_BISHOP to "♝",
        B_ROOK to "♜", B_QUEEN to "♛", B_KING to "♚"
    )

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val whitePiecePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val blackPiecePaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val whitePieceStrokePaint = Paint().apply {
        color = Color.parseColor("#333333")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val blackPieceStrokePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val squareSize: Float get() = width.toFloat() / 8f
    private val boardFlipped: Boolean get() = playerColor == "black"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sq = squareSize

        // Draw squares
        for (rank in 0..7) {
            for (file in 0..7) {
                val displayRank = if (boardFlipped) rank else 7 - rank
                val displayFile = if (boardFlipped) 7 - file else file

                val square = Square(displayFile, displayRank)
                val x = file * sq
                val y = rank * sq

                val paint = if ((displayFile + displayRank) % 2 == 0) darkSquarePaint else lightSquarePaint
                canvas.drawRect(x, y, x + sq, y + sq, paint)

                // Highlight last move
                lastMove?.let { (from, to) ->
                    if (square == from || square == to) {
                        canvas.drawRect(x, y, x + sq, y + sq, lastMovePaint)
                    }
                }

                // Highlight selected square
                if (square == selectedSquare) {
                    canvas.drawRect(x, y, x + sq, y + sq, selectedPaint)
                }

                // Highlight legal moves
                if (square in legalMoveSquares) {
                    if (position?.pieceAt(square) == EMPTY) {
                        val cx = x + sq / 2; val cy = y + sq / 2
                        canvas.drawCircle(cx, cy, sq * 0.15f, legalMovePaint)
                    } else {
                        canvas.drawRect(x, y, x + sq, y + sq, legalMovePaint)
                    }
                }
            }
        }

        // Draw pieces
        val pieceSize = sq * 0.78f
        textPaint.textSize = pieceSize
        whitePiecePaint.textSize = pieceSize
        blackPiecePaint.textSize = pieceSize
        whitePieceStrokePaint.textSize = pieceSize
        blackPieceStrokePaint.textSize = pieceSize

        position?.let { pos ->
            for (rank in 0..7) {
                for (file in 0..7) {
                    val displayRank = if (boardFlipped) rank else 7 - rank
                    val displayFile = if (boardFlipped) 7 - file else file

                    val piece = pos.board[displayRank][displayFile]
                    if (piece == EMPTY) continue

                    val symbol = pieceSymbols[piece] ?: continue
                    val x = file * sq + sq / 2
                    val y = rank * sq + sq / 2 - (textPaint.descent() + textPaint.ascent()) / 2

                    val isWhitePiece = piece > 0
                    if (isWhitePiece) {
                        canvas.drawText(symbol, x, y, whitePieceStrokePaint)
                        canvas.drawText(symbol, x, y, whitePiecePaint)
                    } else {
                        canvas.drawText(symbol, x, y, blackPieceStrokePaint)
                        canvas.drawText(symbol, x, y, blackPiecePaint)
                    }
                }
            }
        }

        // Draw coordinates
        coordinatePaint.textSize = sq * 0.18f
        for (i in 0..7) {
            val rankLabel = if (boardFlipped) ('1' + i).toString() else ('8' - i).toString()
            val fileLabel = if (boardFlipped) ('h' - i).toString() else ('a' + i).toString()

            canvas.drawText(rankLabel, sq * 0.1f, i * sq + sq * 0.2f, coordinatePaint)
            canvas.drawText(fileLabel, i * sq + sq / 2, 8 * sq - sq * 0.05f, coordinatePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive || position == null) return false
        if (event.action != MotionEvent.ACTION_UP) return true

        val sq = squareSize
        val file = (event.x / sq).toInt().coerceIn(0, 7)
        val rank = (event.y / sq).toInt().coerceIn(0, 7)

        val displayFile = if (boardFlipped) 7 - file else file
        val displayRank = if (boardFlipped) rank else 7 - rank
        val tappedSquare = Square(displayFile, displayRank)

        val pos = position ?: return true

        if (selectedSquare == null) {
            // Select a piece
            val piece = pos.pieceAt(tappedSquare)
            val isPlayerPiece = if (playerColor == "white") piece > 0 else piece < 0
            if (isPlayerPiece && piece != EMPTY) {
                selectedSquare = tappedSquare
                legalMoveSquares = findLegalMoves(pos, tappedSquare)
                invalidate()
            }
        } else {
            val from = selectedSquare!!
            if (tappedSquare in legalMoveSquares) {
                // Make the move
                val piece = pos.pieceAt(from)
                val isPawn = Math.abs(piece) == Math.abs(W_PAWN)
                val isPromoRank = (playerColor == "white" && tappedSquare.rank == 7) ||
                        (playerColor == "black" && tappedSquare.rank == 0)

                val promo = if (isPawn && isPromoRank) 'q' else null
                val move = Move(from, tappedSquare, promo)
                lastMove = Pair(from, tappedSquare)
                selectedSquare = null
                legalMoveSquares = emptySet()
                onMoveSelected?.invoke(move)
                invalidate()
            } else {
                // Select different piece or deselect
                val piece = pos.pieceAt(tappedSquare)
                val isPlayerPiece = if (playerColor == "white") piece > 0 else piece < 0
                if (isPlayerPiece && piece != EMPTY) {
                    selectedSquare = tappedSquare
                    legalMoveSquares = findLegalMoves(pos, tappedSquare)
                } else {
                    selectedSquare = null
                    legalMoveSquares = emptySet()
                }
                invalidate()
            }
        }
        return true
    }

    fun setLastMove(from: Square?, to: Square?) {
        lastMove = if (from != null && to != null) Pair(from, to) else null
        invalidate()
    }

    private fun findLegalMoves(pos: ChessPosition, from: Square): Set<Square> {
        val legalSquares = mutableSetOf<Square>()
        val piece = pos.pieceAt(from)
        if (piece == EMPTY) return legalSquares

        for (rank in 0..7) {
            for (file in 0..7) {
                val to = Square(file, rank)
                val target = pos.pieceAt(to)
                // Don't capture own pieces
                val ownPiece = if (piece > 0) target > 0 else target < 0
                if (ownPiece) continue

                val move = Move(from, to)
                val newPos = MoveApplier.applyMove(pos, move) ?: continue

                // Ensure move doesn't leave own king in check
                val inCheck = PositionEvaluator.isInCheck(newPos, piece > 0)
                if (!inCheck) legalSquares.add(to)
            }
        }
        return legalSquares
    }
}
